package com.websarva.wings.android.bbsviewer.ui.common.bookmark

import com.websarva.wings.android.bbsviewer.data.model.Groupable

data class SingleBookmarkState(
    val isBookmarked: Boolean = false,
    val groups: List<Groupable> = emptyList(),
    val selectedGroup: Groupable? = null,
    val showBookmarkSheet: Boolean = false,
    val showAddGroupDialog: Boolean = false,
    val enteredGroupName: String = "",
    val selectedColor: String = "#FF0000",
    val editingGroupId: Long? = null
)
