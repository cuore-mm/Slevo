package com.websarva.wings.android.slevo.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * pending restore の staging と marker 管理を担当する。
 *
 * 検証済みの DB / DataStore JSON を `filesDir/pending-restore/` へ保存し、
 * 最後に `restore.json` marker を作成して次回起動時の [PendingRestoreApplier] へ引き継ぐ。
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
    private val resultDir: File get() = File(context.filesDir, RESULT_DIR_NAME)

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
            dbFile.writeBytes(preview.dbBytes)
        } catch (e: Exception) {
            cleanupPendingDir()
            return "failed to stage DB: ${e.message}"
        }

        // --- DB integrity check ---
        val integrityError = checkIntegrity(dbFile)
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
        val marker = PendingRestoreMarker(
            status = RestoreStatus.PREPARED,
            createdAt = java.time.Instant.now().toString(),
            includeCookies = preview.containsCookies,
            databaseVersion = preview.databaseVersion,
        )
        logD("prepareRestore: writing marker includeCookies=${marker.includeCookies}")
        try {
            markerFile.writeText(
                moshi.adapter<PendingRestoreMarker>().toJson(marker),
            )
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
        if (!markerFile.exists()) return null
        return try {
            moshi.adapter<PendingRestoreMarker>().fromJson(markerFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * marker の状態を更新する。
     */
    fun updateMarker(marker: PendingRestoreMarker) {
        markerFile.writeText(moshi.adapter<PendingRestoreMarker>().toJson(marker))
    }

    /**
     * result file を書き込む。
     *
     * UI が 1 回表示後に削除する。
     */
    fun writeResultFile(success: Boolean, message: String) {
        resultDir.mkdirs()
        val result = PendingRestoreResultFile(
            success = success,
            message = message,
            timestamp = java.time.Instant.now().toString(),
        )
        File(resultDir, RESULT_FILENAME).writeText(
            moshi.adapter<PendingRestoreResultFile>().toJson(result),
        )
    }

    /**
     * result file を読み取る。
     */
    fun readResultFile(): PendingRestoreResultFile? {
        val file = File(resultDir, RESULT_FILENAME)
        if (!file.exists()) return null
        return try {
            moshi.adapter<PendingRestoreResultFile>().fromJson(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    /**
     * result file を削除する。
     */
    fun deleteResultFile() {
        File(resultDir, RESULT_FILENAME).delete()
    }

    /**
     * pending directory と rollback directory を cleanup する。
     */
    fun cleanupPendingDir() {
        pendingDir.deleteRecursively()
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
                cleanupPendingDir()
            }
            // result file のみ存在する場合は削除
            deleteResultFile()
            return null
        }

        return when (marker.status) {
            RestoreStatus.PREPARED -> {
                "pending restore already prepared; restart to apply"
            }
            RestoreStatus.APPLYING, RestoreStatus.DB_SWAPPED -> {
                "pending restore in progress; restart to recover"
            }
            RestoreStatus.FAILED -> {
                // failed の場合は cleanup 成功時のみ新規準備可
                cleanupPendingDir()
                deleteResultFile()
                null
            }
        }
    }

    /**
     * DB の integrity check を実行する。
     *
     * @return 成功時 `null`、失敗時エラーメッ�ージ。
     */
    internal fun checkIntegrity(dbFile: File): String? {
        return dbValidator.validate(dbFile)
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
    }
}
