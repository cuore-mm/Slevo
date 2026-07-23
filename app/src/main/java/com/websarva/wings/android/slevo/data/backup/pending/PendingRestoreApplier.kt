package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.data.backup.restore.BackupDatabaseValidator
import com.websarva.wings.android.slevo.data.backup.BackupMoshiFactory
import com.websarva.wings.android.slevo.data.backup.restore.RealBackupDatabaseValidator
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import com.websarva.wings.android.slevo.data.datasource.local.DATABASE_VERSION
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
                val cookieError = writer.writeCookies(cookies)
                if (cookieError != null) {
                    return cookieError
                }
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
 * [runIfNeeded] を呼ぶ。Hilt / Room (AppDatabase) / DAO / Repository /
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
    private val currentDbVersion: Int,
) {
    constructor(context: Context) : this(
        context = context,
        dbValidator = RealBackupDatabaseValidator(),
        dataStoreReflectorOverride = null,
        fileStoreOverride = null,
        dbSwapperOverride = null,
        nowProvider = { Instant.now().toString() },
        // Hilt / Room 非依存を保つため const val を直接参照（compile 時に inline される）
        currentDbVersion = DATABASE_VERSION,
    )

    private val appContext = context.applicationContext ?: context
    private val moshi = BackupMoshiFactory.create()
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
            RestoreStatus.MIGRATION_PENDING -> recoverFromMigrationPending(marker)
            RestoreStatus.ROLLBACK_REQUIRED -> recoverFromRollbackRequired(marker)
            RestoreStatus.COMPLETED -> recoverFromCompleted(marker)
            RestoreStatus.FAILED -> {
                logInfo("failed marker found, result file preserved for diagnostics")
            }
        }
    }

    /** MIGRATION_PENDING: Room open 成功確認待ち。strict validation を再実行して判定する。 */
    private fun recoverFromMigrationPending(marker: PendingRestoreMarker) {
        val liveDbFile = dbSwapper.getLiveDbFile()
        val hasRollback = dbSwapper.hasRollbackBackup(fileStore.rollbackDir, liveDbFile)

        // rollback backup の有無を確認する前に strict validation を実行する
        val strictError = dbValidator.validate(liveDbFile)
        if (strictError == null) {
            logInfo("MIGRATION_PENDING: strict validation passed, transitioning to COMPLETED")
            fileStore.writeMarker(marker.copy(status = RestoreStatus.COMPLETED))
            // marker を COMPLETED に更新後、success result と cleanup は
            // recoverFromCompleted または completion checker に任せる
            return
        }

        logWarn("MIGRATION_PENDING: strict validation failed: $strictError")
        if (hasRollback) {
            rollbackAndFail(marker, "stale MIGRATION_PENDING: $strictError", liveDbFile, true)
        } else {
            // rollback backup がない → quarantine して fresh DB 起動を優先
            quarantineAndFail(marker, "stale MIGRATION_PENDING (no rollback backup): $strictError", liveDbFile)
        }
    }

    /** ROLLBACK_REQUIRED: completion checker が post-validation 失敗を検出した状態。rollback する。 */
    private fun recoverFromRollbackRequired(marker: PendingRestoreMarker) {
        val liveDbFile = dbSwapper.getLiveDbFile()
        val hasRollback = dbSwapper.hasRollbackBackup(fileStore.rollbackDir, liveDbFile)

        if (!hasRollback) {
            logWarn("ROLLBACK_REQUIRED: no rollback backup available")
            quarantineAndFail(marker, "rollback required but no rollback backup", liveDbFile)
            return
        }

        logInfo("ROLLBACK_REQUIRED: rolling back")
        rollbackAndFail(marker, "rollback required", liveDbFile, hadExistingLiveDb = true)
    }

    /** COMPLETED: post-validation 成功済み。success result と cleanup を再試行する。 */
    private fun recoverFromCompleted(marker: PendingRestoreMarker) {
        logInfo("COMPLETED: retrying success result write and cleanup")
        // success result を再試行
        fileStore.writeResult(
            success = true,
            message = "restore completed successfully",
            timestamp = nowProvider(),
            backupDatabaseVersion = marker.databaseVersion,
            currentDatabaseVersion = currentDbVersion,
            migrationRequired = marker.databaseVersion < currentDbVersion,
            migrationCompleted = true,
        )
        // rollback backup と staging の cleanup、marker 削除は cleanupPending に含まれる
        fileStore.cleanupPending()
    }

    /** rollback backup がなく invalid DB を quarantine して fresh DB 起動を優先する。 */
    private fun quarantineAndFail(
        marker: PendingRestoreMarker,
        reason: String,
        liveDbFile: File,
    ) {
        logError("quarantine: $reason")
        val quarantineDir = File(fileStore.pendingDir, "quarantine")
        quarantineDir.mkdirs()
        var quarantineSuccess = true

        // live DB main, -wal, -shm を quarantine へ移動
        for (suffix in listOf("", "-wal", "-shm")) {
            val file = File(liveDbFile.absolutePath + suffix)
            if (file.exists()) {
                val dest = File(quarantineDir, file.name)
                try {
                    if (!file.renameTo(dest)) {
                        file.copyTo(dest, overwrite = true)
                        file.delete()
                    }
                } catch (e: Exception) {
                    logError("quarantine failed for ${file.name}: ${e.message}")
                    quarantineSuccess = false
                }
            }
        }

        val finalReason = if (quarantineSuccess) {
            "$reason (invalid DB quarantined to $quarantineDir)"
        } else {
            "$reason (quarantine partially failed: manual intervention required)"
        }

        fileStore.writeMarker(marker.copy(status = RestoreStatus.FAILED, failureReason = finalReason))
        fileStore.writeResult(
            success = false,
            message = finalReason,
            timestamp = nowProvider(),
            backupDatabaseVersion = marker.databaseVersion,
            currentDatabaseVersion = currentDbVersion,
            migrationRequired = marker.databaseVersion < currentDbVersion,
            migrationCompleted = false,
        )
        fileStore.cleanupPending()
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

        // --- Post-replace pre-migration validation ---
        val validationError = dbValidator.preValidate(liveDbFile, applyingMarker.databaseVersion)
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

        val migrationRequired = marker.databaseVersion < currentDbVersion
        fileStore.writeResult(
            success = true,
            message = "restore completed successfully",
            timestamp = nowProvider(),
            backupDatabaseVersion = marker.databaseVersion,
            currentDatabaseVersion = currentDbVersion,
            migrationRequired = migrationRequired,
            migrationCompleted = !migrationRequired,
        )
        // --- MIGRATION_PENDING: cleanup せず marker/rollback を保持 ---
        fileStore.writeMarker(marker.copy(status = RestoreStatus.MIGRATION_PENDING))
        logInfo("applyRestore: transitioned to MIGRATION_PENDING")
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
        fileStore.writeResult(
            success = false,
            message = reason,
            timestamp = nowProvider(),
            backupDatabaseVersion = marker.databaseVersion,
            currentDatabaseVersion = currentDbVersion,
            migrationRequired = marker.databaseVersion < currentDbVersion,
            migrationCompleted = false,
        )

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

    /** 定数とテスト用 factory。 */
    companion object {
        private const val TAG = "PendingRestoreApplier"

        internal fun createForTest(
            context: Context,
            dbValidator: BackupDatabaseValidator,
            dataStoreReflector: PendingRestoreDataStoreReflector?,
            fileStore: PendingRestoreFileStore,
            dbSwapper: PendingRestoreDbSwapper,
            nowProvider: () -> String,
            currentDbVersion: Int = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION,
        ): PendingRestoreApplier {
            return PendingRestoreApplier(
                context = context,
                dbValidator = dbValidator,
                dataStoreReflectorOverride = dataStoreReflector,
                fileStoreOverride = fileStore,
                dbSwapperOverride = dbSwapper,
                nowProvider = nowProvider,
                currentDbVersion = currentDbVersion,
            )
        }
    }
}
