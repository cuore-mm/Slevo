package com.websarva.wings.android.slevo.ui.common.bookmark

import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.ui.theme.BookmarkColor

data class SingleBookmarkState(
    val isBookmarked: Boolean = false,
    val groups: List<Groupable> = emptyList(),
    val selectedGroup: Groupable? = null,
    val showBookmarkSheet: Boolean = false,
    val showAddGroupDialog: Boolean = false,
    val enteredGroupName: String = "",
    val selectedColor: String = BookmarkColor.RED.value,
    val editingGroupId: Long? = null,
    val showDeleteGroupDialog: Boolean = false,
    val deleteGroupName: String = "",
    val deleteGroupItems: List<String> = emptyList(),
    val deleteGroupIsBoard: Boolean = true
)
