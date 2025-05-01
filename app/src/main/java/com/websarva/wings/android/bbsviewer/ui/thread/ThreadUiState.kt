package com.websarva.wings.android.bbsviewer.ui.thread

import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.repository.ConfirmationData

data class ThreadUiState(
    val threadInfo: ThreadInfo = ThreadInfo(),
    val posts: List<ReplyInfo>? = null,
    val isLoading: Boolean = false,
    val boardInfo: BoardInfo = BoardInfo(0, "", ""),
    val postDialog: Boolean = false,
    val postFormState: PostFormState = PostFormState(),
    val isPosting: Boolean = false,
    val postConfirmation: ConfirmationData? = null,
    val isConfirmationScreen: Boolean = false
)

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

