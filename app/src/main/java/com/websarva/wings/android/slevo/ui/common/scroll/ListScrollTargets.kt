package com.websarva.wings.android.slevo.ui.common.scroll

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos

/**
 * リスト下端移動時のターゲットインデックスを算出する。
 *
 * `LazyColumn` の `totalItemsCount` を優先し、レイアウト未確定時は呼び出し側の
 * fallback 件数を利用して下端ターゲットを決定する。
 */
fun resolveBottomTargetIndex(totalItemsCount: Int, fallbackCount: Int): Int {
    return when {
        totalItemsCount > 0 -> totalItemsCount - 1
        fallbackCount > 0 -> fallbackCount
        else -> 0
    }
}

/**
 * viewport の更新完了を待機し、直後の下端移動で古いレイアウト情報を参照しないようにする。
 */
suspend fun waitForViewportUpdate(listState: LazyListState, frameLimit: Int = 10) {
    val beforeViewportEnd = listState.layoutInfo.viewportEndOffset
    repeat(frameLimit) {
        withFrameNanos { /* 1フレーム待機 */ }
        if (listState.layoutInfo.viewportEndOffset != beforeViewportEnd) {
            return
        }
    }
}
