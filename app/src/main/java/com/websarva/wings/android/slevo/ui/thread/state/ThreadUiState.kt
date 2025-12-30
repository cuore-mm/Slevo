package com.websarva.wings.android.slevo.ui.thread.state

import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.bbsroute.BaseUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkState

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
    override val singleBookmarkState: SingleBookmarkState = SingleBookmarkState(),
    override val isLoading: Boolean = false,
    val showThreadInfoSheet: Boolean = false,
    val showMoreSheet: Boolean = false,
    val showDisplaySettingsSheet: Boolean = false,
    val showImageMenuSheet: Boolean = false,
    val imageMenuTargetUrl: String? = null,
    val showImageNgDialog: Boolean = false,
    val imageNgTargetUrl: String? = null,
    val myPostNumbers: Set<Int> = emptySet(),
    // UI描画用の派生情報（ViewModelで算出）
    val idCountMap: Map<String, Int> = emptyMap(),
    val idIndexList: List<Int> = emptyList(),
    val replySourceMap: Map<Int, List<Int>> = emptyMap(),
    val ngPostNumbers: Set<Int> = emptySet(),
    val searchQuery: String = "",
    val isSearchMode: Boolean = false,
    val sortType: ThreadSortType = ThreadSortType.NUMBER,
    val treeOrder: List<Int> = emptyList(),
    val treeDepthMap: Map<Int, Int> = emptyMap(),
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
) : BaseUiState<ThreadUiState> {
    override fun copyState(
        boardInfo: BoardInfo,
        singleBookmarkState: SingleBookmarkState,
        loadProgress: Float,
        gestureSettings: GestureSettings,
        isLoading: Boolean,
    ): ThreadUiState {
        return this.copy(
            boardInfo = boardInfo,
            singleBookmarkState = singleBookmarkState,
            loadProgress = loadProgress,
            gestureSettings = gestureSettings,
            isLoading = isLoading,
        )
    }
}

