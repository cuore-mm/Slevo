package com.websarva.wings.android.bbsviewer.data.model

data class BbsMenuContent(
    val categoryName: String,
    val boards: List<Board>
)

data class Board(
    val name: String,
    val url: String,
)
