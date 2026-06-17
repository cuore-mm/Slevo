package com.websarva.wings.android.slevo.ui.tabs.session

import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.board.state.ThreadSortKey
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogState

/**
 * 板タブごとの揮発 UI セッション状態。
 *
 * 検索・並び替え・シート表示・投稿ダイアログなど、板タブを開いている間だけ必要な
 * UI 状態をまとめて扱う。
 */
data class BoardSessionState(
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val currentSortKey: ThreadSortKey = ThreadSortKey.DEFAULT,
    val isSortAscending: Boolean = false,
    val showSortSheet: Boolean = false,
    val showThreadInfoSheet: Boolean = false,
    val threadInfoSheetTarget: ThreadInfo = ThreadInfo(),
    val showBoardInfoSheet: Boolean = false,
    val postDialogState: PostDialogState = PostDialogState(),
    val resetScroll: Boolean = false,
    val pendingToastResId: Int? = null,
    val isLoading: Boolean = false,
    val loadProgress: Float = 0f,
    val isTabSwipeEnabled: Boolean = true,
)
