package com.websarva.wings.android.bbsviewer.ui.favorite

import com.websarva.wings.android.bbsviewer.data.model.Groupable

data class FavoriteUiState(
    val isBookmarked: Boolean = false,
    val groups: List<Groupable> = emptyList(),
    val selectedGroup: Groupable? = null,
    val showBookmarkSheet: Boolean = false,
    val showAddGroupDialog: Boolean = false,
    val enteredGroupName: String = "",
    val selectedColor: String = "#FF0000"
)
