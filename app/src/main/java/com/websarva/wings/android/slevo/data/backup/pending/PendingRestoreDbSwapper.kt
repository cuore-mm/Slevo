package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.websarva.wings.android.slevo.data.backup.BackupMoshiFactory
import java.io.File

/**
 * pending restore 用 DB file 操作の抽象。
 *
 * live DB path 判定、rollback backup、DB replace、rollback restore、
 * fresh install failure cleanup を [PendingRestoreApplier] から分離する。
 */
internal interface PendingRestoreDbSwapper {
    /** live DB file を返す。 */
    fun getLiveDbFile(): File

    /**
     * rollback backup を作成する。
     *
     * main DB と、存在する非空 `-wal` を一貫した file set として temp snapshot へコピーし、
     * すべて成功した時点で `rollback-ready.json` completion marker を書いて公開する。
     * `-shm` はコピーしない。成功時 null、失敗時 detail。
     *
     * @param liveDbFile live DB file。
     * @param rollbackDir rollback snapshot を公開する directory。
     * @return 成功時 null、失敗時 detail message。
     */
    fun createRollbackBackup(liveDbFile: File, rollbackDir: File): String?

    /** staged DB を live DB へ置換する。成功時 null、失敗時 detail。 */
    fun replaceDbFile(stagedDbFile: File, liveDbFile: File): String?

    /** rollback backup が完成しているか返す。 */
    fun hasRollbackBackup(rollbackDir: File, liveDbFile: File): Boolean

    /** rollback backup から live DB を復元する。 */
    fun restoreRollbackBackup(liveDbFile: File, rollbackDir: File): Boolean

    /** fresh install 失敗時に壊れた live DB を削除する。 */
    fun cleanupCorruptFreshInstallDb(liveDbFile: File)
}

/**
 * [PendingRestoreDbSwapper] の本番実装。
 *
 * `AppDatabase` を生成・close せず、SQLite file / WAL / SHM の物理操作だけを扱う。
 */
internal class RealPendingRestoreDbSwapper(
    context: Context,
) : PendingRestoreDbSwapper {
    private val appContext = context.applicationContext ?: context
    private val moshi = BackupMoshiFactory.create()
    private val manifestAdapter = moshi.adapter(RollbackSnapshotManifest::class.java)

    /**
     * Main DB copy seam。
     *
     * production では [File.copyTo] を使い、test では failure または遅延を注入できる。
     */
    internal var mainDbCopy: (source: File, destination: File) -> Unit = { source, destination ->
        source.copyTo(destination, overwrite = true)
    }

    /**
     * WAL copy seam。
     *
     * production では [File.copyTo] を使い、test では failure を注入できる。
     */
    internal var walCopy: (source: File, destination: File) -> Unit = { source, destination ->
        source.copyTo(destination, overwrite = true)
    }

    /**
     * Manifest publish seam。
     *
     * production では [File.writeText] で [RollbackSnapshotManifest.ROLLBACK_READY_FILENAME] を書く。
     * test では failure を注入できる。
     */
    internal var manifestPublisher: (File, RollbackSnapshotManifest) -> Unit = { dir, manifest ->
        File(dir, RollbackSnapshotManifest.ROLLBACK_READY_FILENAME)
            .writeText(manifestAdapter.toJson(manifest))
    }

    /**
     * Main DB restore seam。
     *
     * production では [File.copyTo] を使い、test では failure を注入できる。
     */
    internal var mainDbRestore: (source: File, destination: File) -> Unit = { source, destination ->
        source.copyTo(destination, overwrite = true)
    }

    /**
     * WAL restore seam。
     *
     * production では [File.copyTo] を使い、test では failure を注入できる。
     */
    internal var walRestore: (source: File, destination: File) -> Unit = { source, destination ->
        source.copyTo(destination, overwrite = true)
    }

    /**
     * Live DB sidecar delete seam。
     *
     * production では [File.delete] の Boolean を返し、test では WAL/SHM ごとの失敗を注入する。
     */
    internal var sidecarDelete: (File) -> Boolean = { file -> file.delete() }

    /** debug/release configuration に対応する live DB file を返す。 */
    override fun getLiveDbFile(): File {
        val dbName = if (appContext.packageName.contains(".debug") ||
            appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        ) {
            "slevo_dev_database"
        } else {
            "slevo_database"
        }
        return appContext.getDatabasePath(dbName)
    }

    /** live DB の main/WAL snapshot を rollback directory に公開する。 */
    override fun createRollbackBackup(liveDbFile: File, rollbackDir: File): String? {
        if (rollbackDir.exists() && !rollbackDir.isDirectory) {
            return "rollback path is not a directory"
        }
        val parentDir = rollbackDir.parentFile
            ?: return "rollback directory has no parent"
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            return "failed to create rollback parent directory"
        }

        // --- 同一 filesystem 上の temp snapshot directory ---
        // rollbackDir への rename に失敗しても、手動復旧のため固定名で残る。
        val tempDir = File(parentDir, ROLLBACK_TMP_DIR_NAME)
        if (tempDir.exists() && !tempDir.deleteRecursively()) {
            return "failed to clean previous rollback temp directory"
        }
        if (!tempDir.mkdirs()) {
            return "failed to create rollback temp directory"
        }

        // --- Main DB backup (必須) ---
        try {
            mainDbCopy(liveDbFile, File(tempDir, liveDbFile.name))
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            return "failed to backup main DB: ${e.message}"
        }

        // --- WAL backup (非空の場合のみ必須) ---
        val walFile = File(liveDbFile.parent, liveDbFile.name + "-wal")
        val walIncluded = walFile.exists() && walFile.length() > 0
        if (walIncluded) {
            try {
                walCopy(walFile, File(tempDir, walFile.name))
            } catch (e: Exception) {
                tempDir.deleteRecursively()
                return "failed to backup WAL: ${e.message}"
            }
        }

        // --- SHM は再生成可能なためコピーしない ---

        // --- Completion marker を publish して temp snapshot を公開 ---
        val manifest = RollbackSnapshotManifest(
            formatVersion = RollbackSnapshotManifest.CURRENT_FORMAT_VERSION,
            mainDbFileName = liveDbFile.name,
            walIncluded = walIncluded,
        )
        return try {
            manifestPublisher(tempDir, manifest)
            // 古い rollback snapshot は新しい完成 snapshot で置換する。
            // tempDir が完成してから削除するため、copy 失敗時は旧 snapshot が残る。
            if (rollbackDir.exists() && !rollbackDir.deleteRecursively()) {
                tempDir.deleteRecursively()
                "failed to remove previous rollback snapshot"
            } else if (!tempDir.renameTo(rollbackDir)) {
                tempDir.deleteRecursively()
                "failed to publish rollback snapshot"
            } else {
                null
            }
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            "failed to publish rollback snapshot: ${e.message}"
        }
    }

    /** sidecar cleanup 成功後に staged DB を live DB へ置換する。 */
    override fun replaceDbFile(stagedDbFile: File, liveDbFile: File): String? {
        if (!stagedDbFile.exists()) {
            return "staged DB file not found"
        }

        // --- Cleanup replace-era WAL/SHM ---
        val cleanupError = cleanWalShm(liveDbFile)
        if (cleanupError != null) {
            return cleanupError
        }

        // --- Temp copy / rename ---
        val tempFile = File(liveDbFile.parent, ".restore_tmp_${System.currentTimeMillis()}")
        return try {
            stagedDbFile.copyTo(tempFile, overwrite = true)
            if (liveDbFile.exists() && !liveDbFile.delete()) {
                tempFile.delete()
                return "failed to delete live DB before replace"
            }
            val renamed = tempFile.renameTo(liveDbFile)
            if (!renamed || !liveDbFile.exists()) {
                tempFile.delete()
                "rename failed: live DB does not exist after rename"
            } else {
                null
            }
        } catch (e: Exception) {
            tempFile.delete()
            "failed to replace DB: ${e.message}"
        }
    }

    override fun hasRollbackBackup(rollbackDir: File, liveDbFile: File): Boolean {
        return readValidManifest(rollbackDir) != null
    }

    /** sidecar cleanup 成功後に完成済み rollback snapshot を live DB へ復元する。 */
    override fun restoreRollbackBackup(liveDbFile: File, rollbackDir: File): Boolean {
        val manifest = readValidManifest(rollbackDir)
        if (manifest == null) {
            logWarn("rollback snapshot manifest missing or invalid")
            return false
        }

        // --- Remove replace-era WAL/SHM ---
        val cleanupError = cleanWalShm(liveDbFile)
        if (cleanupError != null) {
            logWarn("rollback sidecar cleanup failed: $cleanupError")
            return false
        }

        val rollbackMain = File(rollbackDir, manifest.mainDbFileName)
        if (!rollbackMain.exists()) {
            logWarn("rollback main DB does not exist")
            return false
        }

        // --- Restore main DB ---
        try {
            if (liveDbFile.exists() && !liveDbFile.delete()) {
                logWarn("failed to delete live DB before rollback")
            }
            mainDbRestore(rollbackMain, liveDbFile)
        } catch (e: Exception) {
            logError("rollback main DB restore failed: ${e.message}")
            return false
        }

        // --- Restore WAL if required ---
        if (manifest.walIncluded) {
            val rollbackWal = File(rollbackDir, liveDbFile.name + "-wal")
            val liveWal = File(liveDbFile.parent, liveDbFile.name + "-wal")
            if (!rollbackWal.exists()) {
                logError("rollback WAL required by manifest but missing")
                return false
            }
            try {
                walRestore(rollbackWal, liveWal)
            } catch (e: Exception) {
                logError("rollback WAL restore failed: ${e.message}")
                return false
            }
        }

        // --- SHM は SQLite に再生成させる ---

        return true
    }

    /** fresh-install failure の main DB/sidecar を従来どおり best-effort で削除する。 */
    override fun cleanupCorruptFreshInstallDb(liveDbFile: File) {
        try {
            if (liveDbFile.exists() && !liveDbFile.delete()) {
                logWarn("failed to delete corrupt live DB")
            }
        } catch (e: Exception) {
            logWarn("failed to delete corrupt live DB: ${e.message}")
        }
        // Fresh-install cleanup is intentionally best-effort and attempts both sidecars.
        cleanWalShm(liveDbFile, continueAfterFailure = true)
    }

    /**
     * rollback directory 内の manifest を読み、必須 file set が揃っているか検証する。
     *
     * @return 有効な [RollbackSnapshotManifest]。無効な場合は null。
     */
    private fun readValidManifest(rollbackDir: File): RollbackSnapshotManifest? {
        val manifestFile = File(rollbackDir, RollbackSnapshotManifest.ROLLBACK_READY_FILENAME)
        if (!manifestFile.exists()) return null

        val manifest = try {
            manifestAdapter.fromJson(manifestFile.readText())
        } catch (_: Exception) {
            null
        } ?: return null

        if (manifest.formatVersion != RollbackSnapshotManifest.CURRENT_FORMAT_VERSION) {
            return null
        }

        val mainDb = File(rollbackDir, manifest.mainDbFileName)
        if (!mainDb.exists()) return null

        if (manifest.walIncluded && !File(rollbackDir, mainDb.name + "-wal").exists()) {
            return null
        }

        return manifest
    }

    /**
     * live DB の存在する WAL/SHM を順に削除し、最初の削除失敗を呼び出し元へ返す。
     *
     * destructive restore paths stop at the first failure; fresh-install cleanup can continue
     * attempting the remaining sidecar while still ignoring the returned error.
     *
     * @param continueAfterFailure fresh-install の best-effort cleanup で残りを試行するか。
     * @return 成功時 null、削除 false または例外時は対象 sidecar を含む error message。
     */
    private fun cleanWalShm(dbFile: File, continueAfterFailure: Boolean = false): String? {
        var firstError: String? = null
        for (suffix in listOf("-wal", "-shm")) {
            val sibling = File(dbFile.parent, dbFile.name + suffix)
            if (!sibling.exists()) {
                continue
            }

            val deleted = try {
                sidecarDelete(sibling)
            } catch (e: Exception) {
                val error = "failed to delete ${sibling.name}: ${e.message}"
                logWarn(error)
                if (firstError == null) firstError = error
                if (!continueAfterFailure) return error
                continue
            }
            if (!deleted) {
                val error = "failed to delete ${sibling.name}: delete returned false"
                logWarn(error)
                if (firstError == null) firstError = error
                if (!continueAfterFailure) return error
            }
        }
        return firstError
    }

    private fun logWarn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: RuntimeException) {
            // JVM unit test の android.util.Log stub では例外になるため握りつぶす。
        }
    }

    private fun logError(message: String) {
        try {
            Log.e(TAG, message)
        } catch (_: RuntimeException) {
            // JVM unit test の android.util.Log stub では例外になるため握りつぶす。
        }
    }

    /** 定数。 */
    private companion object {
        private const val TAG = "PendingRestoreDbSwapper"

        /** rollback snapshot 公開前の temp directory 名。 */
        private const val ROLLBACK_TMP_DIR_NAME = ".rollback-tmp"
    }
}
