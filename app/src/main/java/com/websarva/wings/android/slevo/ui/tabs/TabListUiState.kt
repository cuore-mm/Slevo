package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.ui.unit.IntRect
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo

/**
 * タブ一覧画面専用の UI 状態を表すデータクラス。
 *
 * 検索、長押し選択、削除待ち、詳細 BottomSheet、URL入力ダイアログなど、
 * タブ一覧画面のライフサイクルに紐づく一時 UI 状態を保持する。
 */
data class TabListUiState(
    val isSearchMode: Boolean = false,
    val searchQuery: String = "",
    val pendingScrollToTopRequest: TabListScrollToTopRequest? = null,
    val selectedBoardTab: BoardTabInfo? = null,
    val selectedThreadTab: ThreadTabInfo? = null,
    val selectedTabBounds: IntRect? = null,
    val pendingCloseBoardTab: BoardTabInfo? = null,
    val pendingCloseThreadTab: ThreadTabInfo? = null,
    val detailBoardTab: BoardTabInfo? = null,
    val detailThreadTab: ThreadTabInfo? = null,
    val showBoardInfoBottomSheet: Boolean = false,
    val showThreadInfoBottomSheet: Boolean = false,
    val isUrlValidating: Boolean = false,
    val showUrlDialog: Boolean = false,
    val urlErrorMessage: String? = null,
) {
    val isInLongPressSelectionMode: Boolean
        get() = selectedBoardTab != null || selectedThreadTab != null
}
