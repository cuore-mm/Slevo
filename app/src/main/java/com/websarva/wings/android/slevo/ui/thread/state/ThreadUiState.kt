package com.websarva.wings.android.slevo.ui.thread.state

import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.bbsroute.BaseUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkStatusState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogState
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType

/**
 * スレッド表示のソート種別。
 *
 * 画面上での並び順を決めるために利用する。
 */
enum class ThreadSortType {
    NUMBER,
    TREE
}

/**
 * スレッド画面のUI状態。
 *
 * 画面描画に必要なデータと表示状態を保持する。
 */
data class ThreadUiState(
    val threadInfo: ThreadInfo = ThreadInfo(),
    val posts: List<ThreadPostUiModel>? = null,
    override val loadProgress: Float = 0f,
    override val boardInfo: BoardInfo = BoardInfo(0, "", ""),
    override val bookmarkStatusState: BookmarkStatusState = BookmarkStatusState(),
    override val bookmarkSheetState: BookmarkSheetUiState = BookmarkSheetUiState(),
    override val isLoading: Boolean = false,
    val loadingSource: ThreadLoadingSource = ThreadLoadingSource.NONE,
    val postDialogState: PostDialogState = PostDialogState(),
    val showThreadInfoSheet: Boolean = false,
    val showMoreSheet: Boolean = false,
    val showDisplaySettingsSheet: Boolean = false,
    val showImageMenuSheet: Boolean = false,
    val imageMenuTargetUrl: String? = null,
    val imageMenuTargetUrls: List<String> = emptyList(),
    val showImageNgDialog: Boolean = false,
    val imageNgTargetUrl: String? = null,
    val popupStack: List<PopupInfo> = emptyList(),
    val myPostNumbers: Set<Int> = emptySet(),
    // UI描画用の派生情報（ViewModelで算出）
    val idCountMap: Map<String, Int> = emptyMap(),
    val idIndexList: List<Int> = emptyList(),
    val replySourceMap: Map<Int, List<Int>> = emptyMap(),
    val ngPostNumbers: Set<Int> = emptySet(),
    val imageLoadFailureByUrl: Map<String, ImageLoadFailureType> = emptyMap(),
    val imageLoadingUrls: Set<String> = emptySet(),
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val sortType: ThreadSortType = ThreadSortType.NUMBER,
    val treeOrder: List<Int> = emptyList(),
    val treeDepthMap: Map<Int, Int> = emptyMap(),
    val postGroups: List<ThreadPostGroup> = emptyList(),
    val lastLoadedResCount: Int = 0,
    val latestArrivalGroupIndex: Int? = null,
    val firstNewResNo: Int? = null,
    val prevResCount: Int = 0,
    val isAutoScroll: Boolean = false,
    val showMinimapScrollbar: Boolean = true,
    val textScale: Float = 1f,
    val isIndividualTextScale: Boolean = false,
    val headerTextScale: Float = 0.85f,
    val bodyTextScale: Float = 1f,
    val lineHeight: Float = DEFAULT_THREAD_LINE_HEIGHT,
    val visiblePosts: List<DisplayPost> = emptyList(),
    val replyCounts: List<Int> = emptyList(),
    val firstAfterIndex: Int = -1,
    override val gestureSettings: GestureSettings = GestureSettings.DEFAULT,
    override val isTabSwipeEnabled: Boolean = true,
) : BaseUiState<ThreadUiState> {
    override fun copyState(
        boardInfo: BoardInfo,
        bookmarkStatusState: BookmarkStatusState,
        bookmarkSheetState: BookmarkSheetUiState,
        loadProgress: Float,
        gestureSettings: GestureSettings,
        isLoading: Boolean,
        isTabSwipeEnabled: Boolean,
    ): ThreadUiState {
        return this.copy(
            boardInfo = boardInfo,
            bookmarkStatusState = bookmarkStatusState,
            bookmarkSheetState = bookmarkSheetState,
            loadProgress = loadProgress,
            gestureSettings = gestureSettings,
            isLoading = isLoading,
            loadingSource = if (isLoading) loadingSource else ThreadLoadingSource.NONE,
            isTabSwipeEnabled = isTabSwipeEnabled,
        )
    }
}

/**
 * スレッド更新の起点種別。
 *
 * ローディング表示や挙動の切り替えに利用する。
 */
enum class ThreadLoadingSource {
    NONE,
    INITIAL,
    MANUAL,
    BOTTOM_PULL,
    AUTO_SCROLL,
}
