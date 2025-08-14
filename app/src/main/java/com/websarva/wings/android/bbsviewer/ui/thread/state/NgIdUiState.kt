package com.websarva.wings.android.bbsviewer.ui.thread.state

data class NgIdUiState(
    val text: String = "",
    val boardName: String = "",
    val boardId: Long? = null,
    val boardQuery: String = "",
    val isAllBoards: Boolean = false,
    val isRegex: Boolean = false,
    val showBoardDialog: Boolean = false,
)

