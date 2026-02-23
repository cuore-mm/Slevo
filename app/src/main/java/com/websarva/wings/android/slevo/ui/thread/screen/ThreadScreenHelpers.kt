package com.websarva.wings.android.slevo.ui.thread.screen

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.IntOffset
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo

/**
 * ポップアップ表示要求時の基準座標を算出する。
 *
 * ポップアップが未表示の場合は投稿アイテム自身の座標を返し、表示中の場合は最上位ポップアップの
 * 直上に重ねるための座標を返す。
 */
internal fun resolvePopupBaseOffset(
    itemOffset: IntOffset,
    popupStack: List<PopupInfo>,
): IntOffset {
    if (popupStack.isEmpty()) {
        return itemOffset
    }
    val last = popupStack.last()
    return IntOffset(
        x = last.offset.x,
        y = (last.offset.y - last.size.height).coerceAtLeast(0),
    )
}

/**
 * 下端移動ジェスチャー時のスクロール先インデックスを決定する。
 *
 * `LazyColumn` の totalItemsCount を優先し、レイアウト情報が未確定の場合は表示件数をフォールバックとして使う。
 */
internal fun resolveBottomTargetIndex(totalItemsCount: Int, fallbackCount: Int): Int {
    return when {
        totalItemsCount > 0 -> totalItemsCount - 1
        fallbackCount > 0 -> fallbackCount
        else -> 0
    }
}

/**
 * 下端移動前に viewport の更新を待ち、レイアウト確定待ちによる誤スクロールを抑制する。
 */
internal suspend fun waitForViewportUpdate(listState: LazyListState, frameLimit: Int = 10) {
    val beforeViewportEnd = listState.layoutInfo.viewportEndOffset
    repeat(frameLimit) {
        withFrameNanos { /* 1フレーム待ち */ }
        if (listState.layoutInfo.viewportEndOffset != beforeViewportEnd) {
            return
        }
    }
}
