package com.websarva.wings.android.bbsviewer.ui.threadlist

data class ThreadListUiState(
    val threads: List<ThreadInfo>? = null,
    val isLoading: Boolean = false
)

data class ThreadInfo(
    val title: String,
    val key: String,
    val resCount: Int,
    val date: ThreadDate
)

data class ThreadDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int
)
