package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.data.backup.restore.BackupDatabaseValidator
import com.websarva.wings.android.slevo.data.backup.restore.BackupPreview
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * pending restore のstagingとatomic marker管理を担当する。
 *
 * 検証済みの DB / DataStore JSON を `filesDir/pending-restore/` へ保存し、
 * 最後にatomicに`restore.json` markerを作成して次回起動時の
 * [PendingRestoreApplier]へ引き継ぐ。result fileのpublicationは別のlifecycleで管理する。
 *
 * **依存制約:** 起動時ではなく restore 準備時にのみ使われるため、
 * Hilt 経由 DataSource/Repository/DAO/AppDatabase に依存してよい。
 *
 * @param context アプリケーション Context。`filesDir` の取得にのみ使う。
 * @param moshi marker / JSON のシリアライズに使う Moshi インスタンス。
 * @param dbValidator DB の integrity check に使う validator。
 */
@Singleton
@OptIn(ExperimentalStdlibApi::class)
class PendingRestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val dbValidator: BackupDatabaseValidator,
) {
    private val pendingDir: File get() = File(context.filesDir, PENDING_DIR_NAME)
    private val markerFile: File get() = File(pendingDir, MARKER_FILENAME)
    private val markerStore by lazy {
        AtomicPendingRestoreMarkerFile(markerFile, moshi.adapter<PendingRestoreMarker>())
    }
    private val resultDir: File get() = File(context.filesDir, RESULT_DIR_NAME)
    private val resultStore by lazy {
        AtomicPendingRestoreResultFile(
            File(resultDir, RESULT_FILENAME),
            moshi.adapter<PendingRestoreResultFile>(),
        )
    }

    // test-only hook: true にすると marker write が故意に失敗する
    internal var shouldFailMarkerWrite: Boolean = false

    /**
     * 検証済みのバックアップデータを pending restore として staging する。
     *
     * 既存の pending marker 状態を確認し、新規準備を block または cleanup してから
     * DB / DataStore JSON を staging し、最後に marker を作成する。
     *
     * @param preview 検証済みのバックアップ preview。
     * @return 成功時 `null`、失敗時エラーメッセージ。
     */
    suspend fun prepareRestore(preview: BackupPreview): String? {
        logD("prepareRestore: preview.containsCookies=${preview.containsCookies}" +
            " cookiesJson=${preview.cookiesJson != null}" +
            " cookieCount=${preview.cookiesJson?.cookies?.size ?: 0}")

        // --- 既存 pending の確認 ---
        val existingError = handleExistingPending()
        if (existingError != null) return existingError

        // --- staging directory の準備 ---
        pendingDir.mkdirs()
        val dbStagingDir = File(pendingDir, "database")
        val datastoreStagingDir = File(pendingDir, "datastore")
        dbStagingDir.mkdirs()
        datastoreStagingDir.mkdirs()

        // --- DB staging ---
        val dbFile = File(dbStagingDir, "slevo.db")
        try {
            // 既存 staging file の削除（marker 未作成なので安全）
            if (dbFile.exists()) {
                dbFile.delete()
            }
            // move 優先 → fallback to copy
            if (!preview.dbFile.renameTo(dbFile)) {
                preview.dbFile.copyTo(dbFile, overwrite = true)
                // copy 成功後に source temp file を best-effort 削除
                preview.dbFile.delete()
            }
        } catch (e: Exception) {
            cleanupPendingDir()
            return "failed to stage DB: ${e.message}"
        }

        // --- DB integrity check ---
        val integrityError = checkIntegrity(dbFile, preview.databaseVersion)
        if (integrityError != null) {
            cleanupPendingDir()
            return integrityError
        }

        // --- DataStore JSON staging ---
        try {
            val adapter = moshi.adapter<BackupSettingsJson>()
            File(datastoreStagingDir, "settings.json")
                .writeText(adapter.toJson(preview.settingsJson))
            File(datastoreStagingDir, "tabs.json")
                .writeText(moshi.adapter<BackupTabsJson>().toJson(preview.tabsJson))
            if (preview.cookiesJson != null) {
                File(datastoreStagingDir, "cookies.json")
                    .writeText(moshi.adapter<BackupCookiesJson>().toJson(preview.cookiesJson))
                logD("prepareRestore: staged cookies.json count=${preview.cookiesJson.cookies.size}")
            } else {
                logD("prepareRestore: preview.cookiesJson is null, skipping cookies.json staging")
            }
        } catch (e: Exception) {
            cleanupPendingDir()
            return "failed to stage DataStore JSON: ${e.message}"
        }

        // --- marker 作成 (最後に書く) ---
        if (shouldFailMarkerWrite) {
            cleanupPendingDir()
            return "failed to write marker: test-induced failure"
        }
        val marker = PendingRestoreMarker(
            status = RestoreStatus.PREPARED,
            createdAt = Instant.now().toString(),
            includeCookies = preview.containsCookies,
            databaseVersion = preview.databaseVersion,
        )
        logD("prepareRestore: writing marker includeCookies=${marker.includeCookies}")
        try {
            markerStore.write(marker)
        } catch (e: Exception) {
            cleanupPendingDir()
            return "failed to write marker: ${e.message}"
        }

        return null
    }

    /**
     * 現在の pending marker を読み取る。
     *
     * marker が存在しない場合は `null` を返す。
     */
    fun readMarker(): PendingRestoreMarker? {
        return markerStore.read()
    }

    /**
     * marker の状態を更新する。
     */
    fun updateMarker(marker: PendingRestoreMarker) {
        markerStore.write(marker)
    }

    /**
     * result file を書き込む。
     *
     * UI が 1 回表示後に削除する。
     */
    fun writeResultFile(success: Boolean, message: String) {
        synchronized(PendingRestoreResultFileLock.monitor) {
            resultStore.write(
                PendingRestoreResultFile(
                    success = success,
                    message = message,
                    timestamp = Instant.now().toString(),
                ),
            )
        }
    }

    /**
     * result file を読み取る。
     */
    fun readResultFile(): PendingRestoreResultFile? {
        synchronized(PendingRestoreResultFileLock.monitor) {
            val raw = resultStore.readRaw() ?: return null
            return try {
                moshi.adapter<PendingRestoreResultFile>().fromJson(raw)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * result file を削除する。
     */
    fun deleteResultFile() {
        synchronized(PendingRestoreResultFileLock.monitor) {
            resultStore.delete()
        }
    }

    /**
     * pending directory と rollback directory を cleanup する。
     *
     * @return directory が存在しない、または削除完了した場合は true。
     */
    fun cleanupPendingDir(): Boolean {
        return !pendingDir.exists() || pendingDir.deleteRecursively() && !pendingDir.exists()
    }

    /**
     * rollback directory を cleanup する。
     */
    fun cleanupRollbackDir() {
        File(pendingDir, ROLLBACK_DIR_NAME).deleteRecursively()
    }

    /**
     * 既存の pending marker 状態を確認し、新規準備の可否を判断する。
     *
     * - `prepared`: 新規準備を拒否。
     * - `applying` / `db-swapped`: recovery 優先で新規準備を拒否。
     * - `failed`: cleanup 成功時のみ新規準備可。
     * - marker なし不完全 staging: cleanup。
     * - result file のみ: 削除後に新規準備可。
     *
     * @return 新規準備可能な場合 `null`、拒否時エラーメッセージ。
     */
    internal fun handleExistingPending(): String? {
        val marker = readMarker()
        if (marker == null) {
            // marker なしで staging directory が存在する場合は不完全 staging として cleanup
            if (pendingDir.exists()) {
                if (!cleanupPendingDir()) {
                    return "incomplete pending restore cleanup failed; retry before preparing"
                }
            }
            // result file のみ存在する場合は削除
            deleteResultFile()
            return null
        }

        return when (marker.status) {
            RestoreStatus.PREPARED -> {
                "pending restore already prepared; restart to apply"
            }
            RestoreStatus.APPLYING, RestoreStatus.ROLLBACK_READY, RestoreStatus.DB_SWAPPED -> {
                "pending restore in progress; restart to recover"
            }
            RestoreStatus.MIGRATION_PENDING -> {
                "pending restore waiting for migration confirmation; restart to complete"
            }
            RestoreStatus.ROLLBACK_REQUIRED -> {
                "pending restore requires rollback; restart to recover"
            }
            RestoreStatus.COMPLETED -> {
                "pending restore completing cleanup; restart to finish"
            }
            RestoreStatus.FAILED -> {
                // failed の場合は pending だけ cleanup し、result file は診断用に保持。
                // cleanup 成功時のみ新規復元準備可。
                if (cleanupPendingDir()) {
                    null
                } else {
                    "previous pending restore cleanup failed; retry recovery before preparing"
                }
            }
        }
    }

    /**
     * DB の pre-migration integrity check を実行する。
     *
     * @param dbFile 検証対象の DB ファイル。
     * @param manifestDatabaseVersion manifest.json の databaseVersion。
     * @return 成功時 `null`、失敗時エラーメッセージ。
     */
    internal fun checkIntegrity(dbFile: File, manifestDatabaseVersion: Int): String? {
        return dbValidator.preValidate(dbFile, manifestDatabaseVersion)
    }

    /**
     * staging directory 内の DB ファイルを返す。
     */
    fun getStagedDbFile(): File = File(pendingDir, "database/slevo.db")

    /**
     * staging directory 内の settings JSON ファイルを返す。
     */
    fun getStagedSettingsFile(): File = File(pendingDir, "datastore/settings.json")

    /**
     * staging directory 内の tabs JSON ファイルを返す。
     */
    fun getStagedTabsFile(): File = File(pendingDir, "datastore/tabs.json")

    /**
     * staging directory 内の cookies JSON ファイルを返す。
     */
    fun getStagedCookiesFile(): File = File(pendingDir, "datastore/cookies.json")

    /**
     * rollback directory を返す。
     */
    fun getRollbackDir(): File = File(pendingDir, ROLLBACK_DIR_NAME)

    private fun logD(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            // JVM unit test の Log stub では例外になるため握りつぶす。
        }
    }

    /** 定数。 */
    companion object {
        private const val TAG = "PendingRestoreManager"
        internal const val PENDING_DIR_NAME = "pending-restore"
        internal const val MARKER_FILENAME = "restore.json"
        internal const val ROLLBACK_DIR_NAME = "rollback"
        internal const val RESULT_DIR_NAME = "pending-restore-result"
        internal const val RESULT_FILENAME = "restore-result.json"
        internal const val QUARANTINE_DIR_NAME = "pending-restore-quarantine"
        internal const val DATASTORE_ROLLBACK_SNAPSHOT_FILENAME = "datastore-rollback.json"
    }
}
