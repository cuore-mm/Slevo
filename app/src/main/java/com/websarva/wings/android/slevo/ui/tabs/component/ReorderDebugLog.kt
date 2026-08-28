package com.websarva.wings.android.slevo.ui.tabs.component

import android.util.Log
import com.websarva.wings.android.slevo.BuildConfig

private const val REORDER_LOG_TAG = "TabReorder"

/** Debug buildでだけタブ並び替えのgesture診断メッセージをLogcatへ出力する。 */
internal fun logTabReorder(message: () -> String) {
    if (BuildConfig.DEBUG) {
        try {
            Log.d(REORDER_LOG_TAG, message())
        } catch (_: RuntimeException) {
            // JVM unit testのAndroid SDK stubではLog.dが未実装のため、診断ログだけ無視する。
        }
    }
}
