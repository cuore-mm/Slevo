package com.websarva.wings.android.bbsviewer.ui

data class ThreadUiState(
    val threadUrl: String = "",
    val posts: List<ThreadPost>? = null
)

data class ThreadPost(
    val name: String,
    val email: String,
    val date: String,
    val id: String,
    val content: String
)