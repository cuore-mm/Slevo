package com.websarva.wings.android.slevo.ui.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.core.log.LogFileManager
import java.io.File

/**
 * 保存済みログファイルの共有処理を担当する helper。
 *
 * 現行ログを一時コピーし、FileProvider URI 経由で `ACTION_SEND` Intent を作成する。
 * ログなし・共有先なし・失敗時はクラッシュせず Toast でユーザーに通知する。
 */
object LogShareUtil {

    /**
     * ログファイルを共有する。失敗時は Toast で通知する。
     */
    fun shareLog(context: Context, logFileManager: LogFileManager) {
        // --- Clean up old shared copies ---
        logFileManager.clearOldSharedLogs()

        val logFile = logFileManager.logFile
        if (!logFile.exists() || logFile.length() == 0L) {
            showToast(context, R.string.share_log_no_logs)
            return
        }

        val tempFile = logFileManager.createTempCopyForSharing()
        if (tempFile == null) {
            showToast(context, R.string.share_log_failed)
            return
        }

        val authority = "${context.packageName}.fileprovider"
        val uri = try {
            FileProvider.getUriForFile(context, authority, tempFile)
        } catch (_: Exception) {
            showToast(context, R.string.share_log_failed)
            return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, context.getString(R.string.share_log_title))
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
            showToast(context, R.string.share_log_no_target)
        }
    }

    private fun showToast(context: Context, messageResId: Int) {
        android.widget.Toast.makeText(context, messageResId, android.widget.Toast.LENGTH_SHORT).show()
    }
}
