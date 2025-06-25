package com.websarva.wings.android.bbsviewer.ui.board

import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardBookmarkGroupEntity
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.ui.common.BaseUiState
import com.websarva.wings.android.bbsviewer.ui.bookmark.BookmarkState

data class BoardUiState(
    val threads: List<ThreadInfo>? = null,
    val boardInfo: BoardInfo = BoardInfo(0, "", ""),
    val bookmarkState: BookmarkState = BookmarkState(),
    val showSortSheet: Boolean = false,
    val serviceName: String = "",
    val showInfoDialog: Boolean = false,
    val currentSortKey: ThreadSortKey = ThreadSortKey.DEFAULT,
    val isSortAscending: Boolean = false,
    val sortKeys: List<ThreadSortKey> = ThreadSortKey.entries,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    override val isLoading: Boolean = false,
    override val showTabListSheet: Boolean = false,
) : BaseUiState<BoardUiState> {
    override fun copyState(
        isLoading: Boolean,
        showTabListSheet: Boolean
    ): BoardUiState {
        return this.copy(
            isLoading = isLoading,
            showTabListSheet = showTabListSheet
        )
    }
}

// 並び替え基準の定義
enum class ThreadSortKey(val displayName: String) {
    DEFAULT("デフォルト"), // サーバーから返ってきた順
    MOMENTUM("勢い"),
    RES_COUNT("レス数"),
    DATE_CREATED("作成日時") // スレッドキー順
}
