package com.websarva.wings.android.slevo.ui.board.state

import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.bbsroute.BaseUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkStatusState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogState

/**
 * 板画面のUI状態。
 *
 * 画面描画に必要なデータと表示状態を保持する。
 */
data class BoardUiState(
    val threads: List<ThreadInfo>? = null,
    override val boardInfo: BoardInfo = BoardInfo(0, "", ""),
    override val bookmarkStatusState: BookmarkStatusState = BookmarkStatusState(),
    override val bookmarkSheetState: BookmarkSheetUiState = BookmarkSheetUiState(),
    val showSortSheet: Boolean = false,
    val serviceName: String = "",
    val showInfoDialog: Boolean = false,
    val currentSortKey: ThreadSortKey = ThreadSortKey.DEFAULT,
    val isSortAscending: Boolean = false,
    val sortKeys: List<ThreadSortKey> = ThreadSortKey.entries,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val postDialogState: PostDialogState = PostDialogState(),
    val resetScroll: Boolean = false,
    override val loadProgress: Float = 0f,
    override val gestureSettings: GestureSettings = GestureSettings.DEFAULT,
    override val isLoading: Boolean = false,
) : BaseUiState<BoardUiState> {
    override fun copyState(
        boardInfo: BoardInfo,
        bookmarkStatusState: BookmarkStatusState,
        bookmarkSheetState: BookmarkSheetUiState,
        loadProgress: Float,
        gestureSettings: GestureSettings,
        isLoading: Boolean,
    ): BoardUiState {
        return this.copy(
            boardInfo = boardInfo,
            bookmarkStatusState = bookmarkStatusState,
            bookmarkSheetState = bookmarkSheetState,
            loadProgress = loadProgress,
            gestureSettings = gestureSettings,
            isLoading = isLoading,
        )
    }
}

/**
 * 板画面のスレッド並び替え基準。
 */
enum class ThreadSortKey(val displayName: String) {
    DEFAULT("デフォルト"), // サーバーから返ってきた順
    MOMENTUM("勢い"),
    RES_COUNT("レス数"),
    DATE_CREATED("作成日時") // スレッドキー順
}
