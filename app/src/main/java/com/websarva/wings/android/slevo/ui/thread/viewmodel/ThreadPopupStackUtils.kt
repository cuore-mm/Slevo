package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.PopupContentType

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
        left.rootNumbers == right.rootNumbers &&
        left.contentType == right.contentType &&
        left.hasMoreCandidates == right.hasMoreCandidates
}

/** 通常ポップアップは描画可能な投稿がある場合に残し、返信先一覧は状態行も含めて残す。 */
internal fun prunePopupStackWithoutRenderablePosts(
    popupStack: List<PopupInfo>,
    postCount: Int,
    ngPostNumbers: Set<Int>,
): List<PopupInfo> {
    return popupStack.filter { info ->
        if (info.contentType == PopupContentType.REPLY_TARGETS) {
            info.postNumbers.any { number -> number > 0 }
        } else {
            info.postNumbers.any { number -> number in 1..postCount && number !in ngPostNumbers }
        }
    }
}
