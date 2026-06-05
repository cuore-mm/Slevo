package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabRefreshProgress

/**
 * タブセッション状態を表すデータクラス。
 *
 * 開いている板/スレッドタブ、読み込み状態、更新進捗、URL検証状態をまとめて保持する。
 * 画面固有の一時 UI 状態（検索、長押し選択、BottomSheet など）は
 * [TabListUiState] へ移行済み。
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
) {
    // isLoading を他の状態から計算する算出プロパティとして定義
    val isLoading: Boolean
        get() = !(boardLoaded && threadLoaded)
}
