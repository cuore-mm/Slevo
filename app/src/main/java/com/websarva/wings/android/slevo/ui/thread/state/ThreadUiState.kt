package com.websarva.wings.android.slevo.ui.thread.state

import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.common.BaseUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkState

enum class ThreadSortType {
    NUMBER,
    TREE
}

data class ThreadUiState(
    val threadInfo: ThreadInfo = ThreadInfo(),
    val posts: List<ReplyInfo>? = null,
    val loadProgress: Float = 0f,
    val boardInfo: BoardInfo = BoardInfo(0, "", ""),
    val singleBookmarkState: SingleBookmarkState = SingleBookmarkState(),
    override val isLoading: Boolean = false,
    override val showTabListSheet: Boolean = false,
    val showThreadInfoSheet: Boolean = false,
    val showMoreSheet: Boolean = false,
    val showDisplaySettingsSheet: Boolean = false,
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
    val textScale: Float = 1f,
    val visiblePosts: List<DisplayPost> = emptyList(),
    val replyCounts: List<Int> = emptyList(),
    val firstAfterIndex: Int = -1,
) : BaseUiState<ThreadUiState> {
    override fun copyState(
        isLoading: Boolean,
        showTabListSheet: Boolean
    ): ThreadUiState {
        return this.copy(
            isLoading = isLoading,
            showTabListSheet = showTabListSheet
        )
    }
}

data class ReplyInfo(
    val name: String,
    val email: String,
    val date: String,
    val id: String,
    val beLoginId: String = "",
    val beRank: String = "",
    val beIconUrl: String = "",
    val content: String,
    val momentum: Float = 0.0f,
    val urlFlags: Int = 0
) {
    companion object {
        const val HAS_IMAGE_URL = 1 shl 0
        const val HAS_THREAD_URL = 1 shl 1
        const val HAS_OTHER_URL = 1 shl 2
    }
}
