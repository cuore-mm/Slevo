package com.websarva.wings.android.bbsviewer.ui.thread

import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo

data class ThreadUiState(
    val threadInfo: ThreadInfo = ThreadInfo(),
    val threadUrl: String = "",
    val datUrl: String = "",
    val posts: List<ReplyInfo>? = null,
    val isLoading: Boolean = false,
    val boardInfo: BoardInfo = BoardInfo(),
)

data class ReplyInfo(
    val name: String,
    val email: String,
    val date: String,
    val id: String,
    val content: String
)
