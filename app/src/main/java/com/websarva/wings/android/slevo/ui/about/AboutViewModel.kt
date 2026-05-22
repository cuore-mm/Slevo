package com.websarva.wings.android.slevo.ui.about

import android.content.Context
import androidx.lifecycle.ViewModel
import com.websarva.wings.android.slevo.core.log.LogFileManager
import com.websarva.wings.android.slevo.ui.util.LogShareUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 「このアプリについて」画面の ViewModel。
 *
 * 保存済みログの共有処理を担当し、Hilt 経由で [LogFileManager] を注入する。
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    private val logFileManager: LogFileManager
) : ViewModel() {

    /**
     * ログファイルを共有する。失敗時は Toast でユーザーに通知する。
     */
    fun shareLog(context: Context) {
        LogShareUtil.shareLog(context, logFileManager)
    }
}
