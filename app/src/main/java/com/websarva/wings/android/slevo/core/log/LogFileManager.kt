package com.websarva.wings.android.slevo.core.log

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * アプリ内部ログファイルの保存先、ローテーション、一時コピーを管理する。
 *
 * ログは `noBackupFilesDir/logs/app.log` に保存し、サイズ上限を超えた場合は
 * `app.log.old` へ 1 世代退避する。共有時には cache 領域へ一時コピーを作成する。
 *
 * `noBackupFilesDir` を使うことで、ログが Android Auto Backup の対象外になる。
 */
@Singleton
class LogFileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val LOG_DIR = "logs"
        private const val LOG_FILE_NAME = "app.log"
        private const val OLD_LOG_FILE_NAME = "app.log.old"
        private const val SHARED_LOG_DIR = "shared_logs"
        private const val MAX_LOG_SIZE_BYTES = 1L * 1024 * 1024 // 1MB
    }

    /** ログファイル格納ディレクトリ */
    val logsDir: File
        get() = File(context.noBackupFilesDir, LOG_DIR).apply { mkdirs() }

    /** 現行ログファイル */
    val logFile: File
        get() = File(logsDir, LOG_FILE_NAME)

    /** 退避用旧ログファイル */
    val oldLogFile: File
        get() = File(logsDir, OLD_LOG_FILE_NAME)

    /** 共有用一時コピー格納ディレクトリ */
    private val sharedLogsDir: File
        get() = File(context.cacheDir, SHARED_LOG_DIR).apply { mkdirs() }

    /**
     * ログファイルがサイズ上限を超えていればローテーションを実行する。
     *
     * 既存の old ファイルを削除し、現行ファイルを old へ退避して、
     * 新しい現行ファイルを作成する。
     */
    fun rotateIfNeeded() {
        val current = logFile
        if (!current.exists() || current.length() <= MAX_LOG_SIZE_BYTES) {
            return
        }
        try {
            oldLogFile.delete()
            current.renameTo(oldLogFile)
        } catch (_: IOException) {
            // ローテーション失敗は無視し、書き込みを継続する
        }
    }

    /**
     * 現行ログファイルの内容を共有用 cache ディレクトリへ一時コピーする。
     *
     * @return 作成された一時コピーファイル。コピー失敗時は null
     */
    fun createTempCopyForSharing(): File? {
        val source = logFile
        if (!source.exists() || source.length() == 0L) {
            return null
        }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val dest = File(sharedLogsDir, "slevo-log-$timestamp.log")
        return try {
            source.copyTo(dest, overwrite = true)
            dest
        } catch (_: IOException) {
            null
        }
    }

    /**
     * 古い共有用一時コピーを削除する。
     */
    fun clearOldSharedLogs() {
        try {
            sharedLogsDir.listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
            // 削除失敗は無視する
        }
    }
}
