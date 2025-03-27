package com.websarva.wings.android.bbsviewer.ui.bbslist

data class BBSListUiState(
    val categories: List<Category>? = null,
    val boards: List<Board>? = null
)

data class Board(
    val name: String,
    val url: String
)

data class Category(
    val name: String,
    val boards: List<Board>
)
