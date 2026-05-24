package com.websarva.wings.android.slevo.core.log

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Kermit の [LogWriter] として動作し、ログをファイルへ追記する。
 *
 * ログレベル、tag、メッセージ、Throwable を時刻付きでファイルに保存する。
 * ファイルサイズ上限を超えた場合は [LogFileManager] によるローテーションを行う。
 * 書き込み失敗時は例外を握りつぶし、アプリ本体の動作を妨げない。
 */
class FileLogWriter(
    private val logFileManager: LogFileManager,
    private val minSeverity: Severity = Severity.Debug
) : LogWriter() {

    private val dateFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    override fun isLoggable(tag: String, severity: Severity): Boolean {
        return severity.ordinal >= minSeverity.ordinal
    }

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        if (!isLoggable(tag, severity)) {
            return
        }

        // --- Rotation check ---
        logFileManager.rotateIfNeeded()

        // --- Format log line ---
        val timestamp = dateFormatter.format(Instant.now())
        val logLine = buildString {
            append(timestamp)
            append(" [")
            append(severity.name.uppercase(Locale.getDefault()))
            append("] [")
            append(tag)
            append("] ")
            append(message)
            if (throwable != null) {
                append("\n")
                append(throwable.stackTraceToString())
            }
        }

        // --- Append to file ---
        try {
            logFileManager.logFile.appendText(logLine + "\n")
        } catch (_: Exception) {
            // ファイル書き込み失敗は握りつぶし、本体処理を妨げない
        }
    }
}
