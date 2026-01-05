package com.websarva.wings.android.slevo.ui.common.bookmark

import com.websarva.wings.android.slevo.ui.theme.BookmarkColor

/**
 * ブックマークグループ編集ダイアログの状態をまとめたデータ。
 *
 * 板/スレッド/一覧の各画面で共通に利用する。
 */
data class GroupDialogState(
    val showAddGroupDialog: Boolean = false,
    val enteredGroupName: String = "",
    val selectedColor: String = BookmarkColor.RED.value,
    val editingGroupId: Long? = null,
    val showDeleteGroupDialog: Boolean = false,
    val deleteGroupName: String = "",
    val deleteGroupItems: List<String> = emptyList(),
    val dialogTargetIsBoard: Boolean = true,
)
