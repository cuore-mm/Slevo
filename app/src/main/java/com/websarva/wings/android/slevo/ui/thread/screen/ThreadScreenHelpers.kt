package com.websarva.wings.android.slevo.ui.thread.screen

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
