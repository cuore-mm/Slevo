package com.websarva.wings.android.slevo.data.backup

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * pending restore 用 DataStore 反映処理の抽象。
 *
 * [PendingRestoreApplier] から分離し、JVM unit test では fake 実装へ差し替える。
 */
internal interface PendingRestoreDataStoreReflector {
    /**
     * staging 済み JSON を DataStore へ反映する。
     */
    suspend fun reflect(pendingDir: File, includeCookies: Boolean): String?
}

/**
 * [PendingRestoreDataStoreReflector] の本番実装。
 *
 * pending directory の JSON を読み取り、[PendingRestoreDataStoreWriter] で
 * settings / tabs / cookies を DataStore へ反映する。
 */
@OptIn(ExperimentalStdlibApi::class)
internal class RealPendingRestoreDataStoreReflector(
    private val context: Context,
    private val moshi: Moshi,
) : PendingRestoreDataStoreReflector {
    private val settingsAdapter = moshi.adapter<BackupSettingsJson>()
    private val tabsAdapter = moshi.adapter<BackupTabsJson>()
    private val cookiesAdapter = moshi.adapter<BackupCookiesJson>()

    /** pending directory の JSON を DataStore へ反映する。 */
    override suspend fun reflect(pendingDir: File, includeCookies: Boolean): String? {
        return try {
            val writer = PendingRestoreDataStoreWriter(context, moshi)

            // --- JSON read ---
            val settingsFile = File(pendingDir, "datastore/settings.json")
            val tabsFile = File(pendingDir, "datastore/tabs.json")
            val cookiesFile = File(pendingDir, "datastore/cookies.json")

            val settings = settingsAdapter.fromJson(settingsFile.readText())
                ?: return "failed to parse settings JSON"
            val tabs = tabsAdapter.fromJson(tabsFile.readText())
                ?: return "failed to parse tabs JSON"

            // --- DataStore write ---
            writer.writeSettings(settings)
            writer.writeTabs(tabs)

            if (includeCookies && cookiesFile.exists()) {
                val cookies = cookiesAdapter.fromJson(cookiesFile.readText())
                    ?: return "failed to parse cookies JSON"
                writer.writeCookies(cookies)
            }

            null
        } catch (e: Exception) {
            "DataStore reflection failed: ${e.message}"
        }
    }
}

/**
 * 起動時に pending restore を適用する applier。
 *
 * `SlevoApplication.onCreate()` の `super.onCreate()` 直後に手動生成して
 * [runIfNeeded] を呼ぶ。Hilt 経由で取得せず、`AppDatabase`、DAO、Repository、
 * DB 依存 DataSource には一切依存しない。
 *
 * この class は state machine orchestration に集中し、marker/result file I/O、
 * DB file 操作、DataStore 反映の詳細は collaborator へ委譲する。
 */
@OptIn(ExperimentalStdlibApi::class)
class PendingRestoreApplier private constructor(
    private val context: Context,
    private val dbValidator: BackupDatabaseValidator,
    private val dataStoreReflectorOverride: PendingRestoreDataStoreReflector?,
    private val fileStoreOverride: PendingRestoreFileStore?,
    private val dbSwapperOverride: PendingRestoreDbSwapper?,
    private val nowProvider: () -> String,
) {
    constructor(context: Context) : this(
        context = context,
        dbValidator = RealBackupDatabaseValidator(),
        dataStoreReflectorOverride = null,
        fileStoreOverride = null,
        dbSwapperOverride = null,
        nowProvider = { Instant.now().toString() },
    )

    private val appContext = context.applicationContext ?: context
    private val moshi = Moshi.Builder().build()
    private val fileStore = fileStoreOverride ?: RealPendingRestoreFileStore(appContext, moshi)
    private val dbSwapper = dbSwapperOverride ?: RealPendingRestoreDbSwapper(appContext)
    private val dataStoreReflector =
        dataStoreReflectorOverride ?: RealPendingRestoreDataStoreReflector(appContext, moshi)

    /**
     * pending restore が存在する場合、DB 置換と DataStore 反映を同期的に完了する。
     *
     * 例外は外へ投げない。想定外例外は marker を `failed` に更新し、
     * result file に失敗を記録して return する。
     */
    suspend fun runIfNeeded() {
        withContext(Dispatchers.IO) {
            try {
                runIfNeededOnIo()
            } catch (e: Exception) {
                recordStartupRestoreFailureOnIo(e)
            }
        }
    }

    /** I/O dispatcher 上で実行する pending restore の本体処理。 */
    private suspend fun runIfNeededOnIo() {
        val marker = fileStore.readMarker() ?: return

        when (marker.status) {
            RestoreStatus.PREPARED -> applyRestore(marker)
            RestoreStatus.APPLYING, RestoreStatus.DB_SWAPPED -> {
                logWarn("stale marker found: ${marker.status}, rolling back")
                val liveDbFile = dbSwapper.getLiveDbFile()
                rollbackAndFail(
                    marker = marker,
                    reason = "stale marker: ${marker.status}",
                    liveDbFile = liveDbFile,
                    hadExistingLiveDb = dbSwapper.hasRollbackBackup(fileStore.rollbackDir, liveDbFile),
                )
            }
            RestoreStatus.FAILED -> {
                logInfo("failed marker found, cleaning up")
                fileStore.cleanupPending()
            }
        }
    }

    /** 想定外例外を `failed` marker/result として記録する。 */
    private fun recordStartupRestoreFailureOnIo(e: Exception) {
        logError("startup restore failed unexpectedly", e)

        try {
            fileStore.readMarker()?.let { marker ->
                fileStore.writeMarker(
                    marker.copy(
                        status = RestoreStatus.FAILED,
                        failureReason = "unexpected error: ${e.message}",
                    ),
                )
            }
        } catch (_: Exception) {
            // marker 更新失敗は握りつぶす
        }

        try {
            fileStore.writeResult(
                success = false,
                message = "unexpected error: ${e.message}",
                timestamp = nowProvider(),
            )
        } catch (_: Exception) {
            // result file 書き込み失敗も握りつぶす
        }
    }

    /** pending restore の prepared state を適用する。 */
    private suspend fun applyRestore(marker: PendingRestoreMarker) {
        val applyingMarker = marker.copy(status = RestoreStatus.APPLYING)
        fileStore.writeMarker(applyingMarker)

        val stagedDbFile = File(fileStore.pendingDir, "database/slevo.db")
        val liveDbFile = dbSwapper.getLiveDbFile()
        val hadExistingLiveDb = liveDbFile.exists()

        // --- Rollback backup ---
        if (hadExistingLiveDb) {
            val rollbackError = dbSwapper.createRollbackBackup(liveDbFile, fileStore.rollbackDir)
            if (rollbackError != null) {
                rollbackAndFail(applyingMarker, rollbackError, liveDbFile, hadExistingLiveDb)
                return
            }
        }

        // --- DB replace ---
        val replaceError = dbSwapper.replaceDbFile(stagedDbFile, liveDbFile)
        if (replaceError != null) {
            rollbackAndFail(applyingMarker, replaceError, liveDbFile, hadExistingLiveDb)
            return
        }

        // --- Post-replace validation ---
        val validationError = dbValidator.validate(liveDbFile)
        if (validationError != null) {
            rollbackAndFail(
                applyingMarker,
                "post-replace validation failed: $validationError",
                liveDbFile,
                hadExistingLiveDb,
            )
            return
        }

        // --- DataStore reflection ---
        val dbSwappedMarker = marker.copy(status = RestoreStatus.DB_SWAPPED)
        fileStore.writeMarker(dbSwappedMarker)

        val dataStoreError = dataStoreReflector.reflect(fileStore.pendingDir, marker.includeCookies)
        if (dataStoreError != null) {
            rollbackAndFail(dbSwappedMarker, dataStoreError, liveDbFile, hadExistingLiveDb)
            return
        }

        fileStore.writeResult(
            success = true,
            message = "restore completed successfully",
            timestamp = nowProvider(),
        )
        fileStore.cleanupPending()
    }

    /** rollback を実行して `failed` marker / result を記録する。 */
    private fun rollbackAndFail(
        marker: PendingRestoreMarker,
        reason: String,
        liveDbFile: File,
        hadExistingLiveDb: Boolean,
    ) {
        logError("rollback: $reason")
        var shouldCleanupPending = true

        if (hadExistingLiveDb) {
            val restored = dbSwapper.restoreRollbackBackup(liveDbFile, fileStore.rollbackDir)
            if (!restored) {
                shouldCleanupPending = false
            }
        } else {
            dbSwapper.cleanupCorruptFreshInstallDb(liveDbFile)
        }

        fileStore.writeMarker(marker.copy(status = RestoreStatus.FAILED, failureReason = reason))
        fileStore.writeResult(success = false, message = reason, timestamp = nowProvider())

        if (shouldCleanupPending) {
            fileStore.cleanupPending()
        } else {
            logWarn("rollback backup preserved for manual recovery")
        }
    }

    private fun logInfo(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: RuntimeException) {
            // JVM unit test の android.util.Log stub では例外になるため握りつぶす。
        }
    }

    private fun logWarn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: RuntimeException) {
            // JVM unit test の android.util.Log stub では例外になるため握りつぶす。
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        try {
            if (throwable == null) {
                Log.e(TAG, message)
            } else {
                Log.e(TAG, message, throwable)
            }
        } catch (_: RuntimeException) {
            // JVM unit test の android.util.Log stub では例外になるため握りつぶす。
        }
    }

    companion object {
        private const val TAG = "PendingRestoreApplier"

        internal fun createForTest(
            context: Context,
            dbValidator: BackupDatabaseValidator,
            dataStoreReflector: PendingRestoreDataStoreReflector?,
            fileStore: PendingRestoreFileStore,
            dbSwapper: PendingRestoreDbSwapper,
            nowProvider: () -> String,
        ): PendingRestoreApplier {
            return PendingRestoreApplier(
                context = context,
                dbValidator = dbValidator,
                dataStoreReflectorOverride = dataStoreReflector,
                fileStoreOverride = fileStore,
                dbSwapperOverride = dbSwapper,
                nowProvider = nowProvider,
            )
        }
    }
}
