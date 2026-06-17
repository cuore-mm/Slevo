package com.websarva.wings.android.slevo.ui.tabs.session

import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogState
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadLoadingSource
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType

/**
 * スレッドタブごとの揮発 UI セッション状態。
 *
 * プロセス内でだけ保持する検索条件、表示モード、ポップアップ、投稿ダイアログ、
 * 自動スクロール・更新表示などの状態をまとめて扱う。
 */
data class ThreadSessionState(
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val sortType: ThreadSortType = ThreadSortType.NUMBER,
    val popupStack: List<PopupInfo> = emptyList(),
    val postDialogState: PostDialogState = PostDialogState(),
    val isAutoScroll: Boolean = false,
    val isLoading: Boolean = false,
    val loadingSource: ThreadLoadingSource = ThreadLoadingSource.NONE,
    val loadProgress: Float = 0f,
    val isTabSwipeEnabled: Boolean = true,
    val pendingToastResId: Int? = null,
    val showThreadInfoSheet: Boolean = false,
    val showMoreSheet: Boolean = false,
    val showDisplaySettingsSheet: Boolean = false,
    val showImageMenuSheet: Boolean = false,
    val imageMenuTargetUrl: String? = null,
    val imageMenuTargetUrls: List<String> = emptyList(),
    val showImageNgDialog: Boolean = false,
    val imageNgTargetUrl: String? = null,
)
