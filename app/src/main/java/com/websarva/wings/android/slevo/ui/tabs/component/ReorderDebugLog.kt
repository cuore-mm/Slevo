package com.websarva.wings.android.slevo.ui.tabs.component

import android.util.Log
import com.websarva.wings.android.slevo.BuildConfig

private const val REORDER_LOG_TAG = "TabReorder"

/** Debug buildでだけタブ並び替えのgesture診断メッセージをLogcatへ出力する。 */
internal fun logTabReorder(message: () -> String) {
    if (BuildConfig.DEBUG) {
        Log.d(REORDER_LOG_TAG, message())
    }
}
