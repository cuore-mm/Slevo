package com.websarva.wings.android.bbsviewer.ui.thread

import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.repository.ConfirmationData
import com.websarva.wings.android.bbsviewer.ui.common.BaseUiState
import com.websarva.wings.android.bbsviewer.ui.common.bookmark.SingleBookmarkState

data class ThreadUiState(
    val threadInfo: ThreadInfo = ThreadInfo(),
    val posts: List<ReplyInfo>? = null,
    val loadProgress: Float = 0f,
    val boardInfo: BoardInfo = BoardInfo(0, "", ""),
    val postDialog: Boolean = false,
    val postFormState: PostFormState = PostFormState(),
    val isPosting: Boolean = false,
    val postConfirmation: ConfirmationData? = null,
    val isConfirmationScreen: Boolean = false,
    val singleBookmarkState: SingleBookmarkState = SingleBookmarkState(),
    override val isLoading: Boolean = false,
    override val showTabListSheet: Boolean = false,
    val showErrorWebView: Boolean = false,
    val errorHtmlContent: String = "",
    val postResultMessage: String? = null
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
    val content: String
)

data class PostFormState(
    val name: String = "",
    val mail: String = "",
    val message: String = ""
)

