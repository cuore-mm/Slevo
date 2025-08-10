package com.websarva.wings.android.bbsviewer.ui.tabs

/**
 * タブ画面全体のUI状態を表すデータクラス
 */
data class TabsUiState(
    val openThreadTabs: List<ThreadTabInfo> = emptyList(),
    val openBoardTabs: List<BoardTabInfo> = emptyList(),
    val boardLoaded: Boolean = false,
    val threadLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val newResCounts: Map<String, Int> = emptyMap(),
    val lastTabPage: Int = 0,
) {
    // isLoadingを他の状態から計算する算出プロパティとして定義
    val isLoading: Boolean
        get() = !(boardLoaded && threadLoaded)
}
