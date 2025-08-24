package com.websarva.wings.android.slevo.ui.thread.state

import com.websarva.wings.android.slevo.data.model.NgType

data class NgUiState(
    val id: Long? = null,
    val text: String = "",
    val type: NgType = NgType.USER_ID,
    val boardName: String = "",
    val boardId: Long? = null,
    val boardQuery: String = "",
    val isAllBoards: Boolean = false,
    val isRegex: Boolean = false,
    val showBoardDialog: Boolean = false,
)
