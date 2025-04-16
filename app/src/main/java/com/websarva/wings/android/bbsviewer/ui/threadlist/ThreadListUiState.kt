package com.websarva.wings.android.bbsviewer.ui.threadlist

import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo

data class ThreadListUiState(
    val threads: List<ThreadInfo>? = null,
    val isLoading: Boolean = false,
    val boardInfo: BoardInfo = BoardInfo(),
)
