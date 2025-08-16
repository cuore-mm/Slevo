package com.websarva.wings.android.bbsviewer.ui.thread.state

import com.websarva.wings.android.bbsviewer.data.model.NgType

data class NgUiState(
    val text: String = "",
    val type: NgType = NgType.USER_ID,
    val boardName: String = "",
    val boardId: Long? = null,
    val boardQuery: String = "",
    val isAllBoards: Boolean = false,
    val isRegex: Boolean = false,
    val showBoardDialog: Boolean = false,
)
