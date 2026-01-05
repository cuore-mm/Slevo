package com.websarva.wings.android.slevo.ui.common.bookmark

import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.ui.theme.BookmarkColor

/**
 * ブックマークシートのUI状態を表すデータ。
 *
 * グループ一覧や編集ダイアログの表示状態を保持する。
 */
data class BookmarkSheetUiState(
    val groups: List<Groupable> = emptyList(),
    val selectedGroupId: Long? = null,
    val showAddGroupDialog: Boolean = false,
    val enteredGroupName: String = "",
    val selectedColor: String = BookmarkColor.RED.value,
    val editingGroupId: Long? = null,
    val showDeleteGroupDialog: Boolean = false,
    val deleteGroupName: String = "",
    val deleteGroupItems: List<String> = emptyList(),
    val deleteGroupIsBoard: Boolean = true,
)
