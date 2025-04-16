package com.websarva.wings.android.bbsviewer.ui.bbslist

import com.websarva.wings.android.bbsviewer.data.model.BoardInfo

data class BBSListUiState(
    val categories: List<Category>? = null,
    val boards: List<BoardInfo>? = null
)

data class Category(
    val name: String,
    val boards: List<BoardInfo>
)
