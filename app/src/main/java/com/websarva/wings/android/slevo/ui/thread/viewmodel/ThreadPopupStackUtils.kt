package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo

/**
 * スレッド画面のポップアップ重複抑止を扱うヘルパー群。
 *
 * RouteViewModel とテストから共通利用し、表示内容ベースで連続重複を防ぐ。
 */

/**
 * 現在のポップアップスタックへ新しいポップアップを追加する。
 *
 * 直前の最上位ポップアップと表示内容が同一の場合は連続表示を抑止し、
 * 既存スタックをそのまま返す。
 */
internal fun appendPopupIfDistinct(
    stack: List<PopupInfo>,
    candidate: PopupInfo,
): List<PopupInfo> {
    val top = stack.lastOrNull() ?: return stack + candidate
    if (isSamePopupContent(top, candidate)) {
        // 連続で同一内容を開こうとした場合は積み上げない。
        return stack
    }
    return stack + candidate
}

/**
 * 2つのポップアップが同一表示内容かを判定する。
 *
 * `popupId` やレイアウト情報ではなく、表示対象投稿とツリーインデントの一致で比較する。
 */
internal fun isSamePopupContent(
    left: PopupInfo,
    right: PopupInfo,
): Boolean {
    return left.postNumbers == right.postNumbers &&
        left.indentLevels == right.indentLevels &&
        left.rootNumbers == right.rootNumbers
}
