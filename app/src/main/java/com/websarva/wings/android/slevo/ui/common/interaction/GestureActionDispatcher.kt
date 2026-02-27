package com.websarva.wings.android.slevo.ui.common.interaction

import com.websarva.wings.android.slevo.data.model.GestureAction

/**
 * 画面共通のジェスチャーアクション処理を受け取るハンドラ群。
 */
data class CommonGestureActionHandlers(
    val onRefresh: () -> Unit,
    val onPostOrCreateThread: () -> Unit,
    val onSearch: () -> Unit,
    val onOpenTabList: () -> Unit,
    val onOpenBookmarkList: () -> Unit,
    val onOpenBoardList: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onOpenNewTab: () -> Unit,
    val onSwitchToNextTab: () -> Unit,
    val onSwitchToPreviousTab: () -> Unit,
    val onCloseTab: () -> Unit,
)

/**
 * Thread/Board で共通化できるジェスチャーアクションをディスパッチする。
 */
fun dispatchCommonGestureAction(
    action: GestureAction,
    handlers: CommonGestureActionHandlers,
) {
    // --- Shared action dispatch ---
    when (action) {
        GestureAction.Refresh -> handlers.onRefresh()
        GestureAction.PostOrCreateThread -> handlers.onPostOrCreateThread()
        GestureAction.Search -> handlers.onSearch()
        GestureAction.OpenTabList -> handlers.onOpenTabList()
        GestureAction.OpenBookmarkList -> handlers.onOpenBookmarkList()
        GestureAction.OpenBoardList -> handlers.onOpenBoardList()
        GestureAction.OpenHistory -> handlers.onOpenHistory()
        GestureAction.OpenNewTab -> handlers.onOpenNewTab()
        GestureAction.SwitchToNextTab -> handlers.onSwitchToNextTab()
        GestureAction.SwitchToPreviousTab -> handlers.onSwitchToPreviousTab()
        GestureAction.CloseTab -> handlers.onCloseTab()

        // Guard: ToTop / ToBottom は Screen 側でスクロール済みのため no-op とする。
        GestureAction.ToTop,
        GestureAction.ToBottom -> Unit
    }
}
