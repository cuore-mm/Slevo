package com.websarva.wings.android.bbsviewer.ui.bottombar

data class BottomBarUiState(
    val sortOptions: List<String> = listOf("昇順", "降順", "その他"),
    val selectedSortOption: String = sortOptions[0]
)
