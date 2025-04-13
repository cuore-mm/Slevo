package com.websarva.wings.android.bbsviewer.ui.thread

data class ThreadUiState(
    val threadUrl: String = "",
    val datUrl: String = "",
    val title: String = "",
    val posts: List<ReplyInfo>? = null,
    val isLoading: Boolean = false
)

data class ReplyInfo(
    val name: String,
    val email: String,
    val date: String,
    val id: String,
    val content: String
)
