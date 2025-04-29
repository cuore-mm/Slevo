package com.websarva.wings.android.bbsviewer.ui.board

import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo

data class BoardUiState(
    val threads: List<ThreadInfo>? = null,
    val isLoading: Boolean = false,
    val boardInfo: BoardInfo = BoardInfo(),)
