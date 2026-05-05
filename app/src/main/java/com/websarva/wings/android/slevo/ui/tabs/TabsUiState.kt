package com.websarva.wings.android.slevo.ui.tabs

/**
 * タブ画面全体の UI 状態を表すデータクラス。
 *
 * 板/スレッド一覧のロード状態、更新進捗、URL入力ダイアログの状態をまとめて保持する。
 */
data class TabsUiState(
    val openThreadTabs: List<ThreadTabInfo> = emptyList(),
    val openBoardTabs: List<BoardTabInfo> = emptyList(),
    val boardLoaded: Boolean = false,
    val threadLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshProgress: ThreadTabRefreshProgress? = null,
    val newResCounts: Map<String, Int> = emptyMap(),
    val isUrlValidating: Boolean = false,
    val showUrlDialog: Boolean = false,
    val urlErrorMessage: String? = null,
) {
    // --- Loading state ---
    // isLoading を他の状態から計算する算出プロパティとして定義
    val isLoading: Boolean
        get() = !(boardLoaded && threadLoaded)
}
