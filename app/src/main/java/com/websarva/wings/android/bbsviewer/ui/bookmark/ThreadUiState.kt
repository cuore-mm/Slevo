package com.websarva.wings.android.bbsviewer.ui.bookmark

data class ThreadUiState(
    val threadUrl: String = "",
    val datUrl: String = "",
    val posts: List<ThreadPost>? = null
)

data class ThreadPost(
    val name: String,
    val email: String,
    val date: String,
    val id: String,
    val content: String
)
