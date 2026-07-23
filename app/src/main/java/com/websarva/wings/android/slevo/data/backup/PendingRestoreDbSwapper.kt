package com.websarva.wings.android.slevo.data.backup

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
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

    /** rollback backup を作成する。成功時 null、失敗時 detail。 */
    fun createRollbackBackup(liveDbFile: File, rollbackDir: File): String?

    /** staged DB を live DB へ置換する。成功時 null、失敗時 detail。 */
    fun replaceDbFile(stagedDbFile: File, liveDbFile: File): String?

    /** rollback backup が存在するか返す。 */
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

    override fun createRollbackBackup(liveDbFile: File, rollbackDir: File): String? {
        if (!rollbackDir.exists() && !rollbackDir.mkdirs()) {
            return "failed to create rollback directory"
        }

        // --- Main DB backup ---
        try {
            liveDbFile.copyTo(File(rollbackDir, liveDbFile.name), overwrite = true)
        } catch (e: Exception) {
            return "failed to backup main DB: ${e.message}"
        }

        // --- WAL/SHM backup ---
        for (suffix in listOf("-wal", "-shm")) {
            val sibling = File(liveDbFile.parent, liveDbFile.name + suffix)
            if (sibling.exists()) {
                try {
                    sibling.copyTo(File(rollbackDir, sibling.name), overwrite = true)
                } catch (e: Exception) {
                    logWarn("failed to backup ${sibling.name}: ${e.message}")
                }
            }
        }

        return null
    }

    override fun replaceDbFile(stagedDbFile: File, liveDbFile: File): String? {
        if (!stagedDbFile.exists()) {
            return "staged DB file not found"
        }

        // --- Cleanup replace-era WAL/SHM ---
        cleanWalShm(liveDbFile)

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
        return File(rollbackDir, liveDbFile.name).exists()
    }

    override fun restoreRollbackBackup(liveDbFile: File, rollbackDir: File): Boolean {
        // --- Remove replace-era WAL/SHM ---
        cleanWalShm(liveDbFile)

        val rollbackMain = File(rollbackDir, liveDbFile.name)
        if (!rollbackMain.exists()) {
            logWarn("rollback main DB does not exist")
            return true
        }

        // --- Restore main DB ---
        try {
            if (liveDbFile.exists() && !liveDbFile.delete()) {
                logWarn("failed to delete live DB before rollback")
            }
            rollbackMain.copyTo(liveDbFile, overwrite = true)
        } catch (e: Exception) {
            logError("rollback main DB restore failed: ${e.message}")
            return false
        }

        // --- Restore WAL/SHM ---
        for (suffix in listOf("-wal", "-shm")) {
            val rollbackSibling = File(rollbackDir, liveDbFile.name + suffix)
            val liveSibling = File(liveDbFile.parent, liveDbFile.name + suffix)
            if (rollbackSibling.exists()) {
                try {
                    rollbackSibling.copyTo(liveSibling, overwrite = true)
                } catch (e: Exception) {
                    logError("rollback $suffix restore failed: ${e.message}")
                }
            }
        }

        return true
    }

    override fun cleanupCorruptFreshInstallDb(liveDbFile: File) {
        try {
            if (liveDbFile.exists() && !liveDbFile.delete()) {
                logWarn("failed to delete corrupt live DB")
            }
        } catch (e: Exception) {
            logWarn("failed to delete corrupt live DB: ${e.message}")
        }
        cleanWalShm(liveDbFile)
    }

    private fun cleanWalShm(dbFile: File) {
        for (suffix in listOf("-wal", "-shm")) {
            val sibling = File(dbFile.parent, dbFile.name + suffix)
            if (sibling.exists()) {
                try {
                    sibling.delete()
                } catch (e: Exception) {
                    logWarn("failed to delete ${sibling.name}: ${e.message}")
                }
            }
        }
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
    }
}
