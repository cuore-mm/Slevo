package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.ui.unit.IntRect
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabRefreshProgress

/**
 * タブ画面全体の UI 状態を表すデータクラス。
 *
 * 板/スレッド一覧のロード状態、更新進捗、URL入力ダイアログの状態、
 * 長押し選択中のタブとアンカー位置、詳細 BottomSheet 表示状態をまとめて保持する。
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
    val selectedBoardTab: BoardTabInfo? = null,
    val selectedThreadTab: ThreadTabInfo? = null,
    val selectedTabBounds: IntRect? = null,
    val pendingCloseBoardTab: BoardTabInfo? = null,
    val pendingCloseThreadTab: ThreadTabInfo? = null,
    val detailBoardTab: BoardTabInfo? = null,
    val detailThreadTab: ThreadTabInfo? = null,
    val showBoardInfoBottomSheet: Boolean = false,
    val showThreadInfoBottomSheet: Boolean = false,
) {
    // --- Loading state ---
    // isLoading を他の状態から計算する算出プロパティとして定義
    val isLoading: Boolean
        get() = !(boardLoaded && threadLoaded)

    // 長押し選択中かどうかを判定する算出プロパティ
    val isInLongPressSelectionMode: Boolean
        get() = selectedBoardTab != null || selectedThreadTab != null
}
