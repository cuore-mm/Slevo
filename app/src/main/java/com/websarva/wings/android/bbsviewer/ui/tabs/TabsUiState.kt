package com.websarva.wings.android.bbsviewer.ui.tabs

/**
 * タブ画面全体のUI状態を表すデータクラス
 */
data class TabsUiState(
    val openThreadTabs: List<ThreadTabInfo> = emptyList(),
    val openBoardTabs: List<BoardTabInfo> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val newResCounts: Map<String, Int> = emptyMap(),
)
