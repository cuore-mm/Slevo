package com.websarva.wings.android.slevo.ui.board

import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.ui.common.BaseUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.SingleBookmarkState

data class BoardUiState(
    val threads: List<ThreadInfo>? = null,
    val boardInfo: BoardInfo = BoardInfo(0, "", ""),
    val singleBookmarkState: SingleBookmarkState = SingleBookmarkState(),
    val showSortSheet: Boolean = false,
    val serviceName: String = "",
    val showInfoDialog: Boolean = false,
    val currentSortKey: ThreadSortKey = ThreadSortKey.DEFAULT,
    val isSortAscending: Boolean = false,
    val sortKeys: List<ThreadSortKey> = ThreadSortKey.entries,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val createDialog: Boolean = false,
    val createFormState: CreateThreadFormState = CreateThreadFormState(),
    val isPosting: Boolean = false,
    val postConfirmation: ConfirmationData? = null,
    val isConfirmationScreen: Boolean = false,
    val showErrorWebView: Boolean = false,
    val errorHtmlContent: String = "",
    val postResultMessage: String? = null,
    val resetScroll: Boolean = false,
    val loadProgress: Float = 0f,
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

data class CreateThreadFormState(
    val name: String = "",
    val mail: String = "",
    val title: String = "",
    val message: String = "",
)
