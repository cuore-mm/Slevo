package com.websarva.wings.android.slevo.ui.common.bookmark

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable

/**
 * ブックマークシートと関連ダイアログをまとめて表示するホストComposable。
 *
 * ホルダーの状態に応じてシート表示と各種ダイアログの開閉を制御する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkSheetHost(
    sheetState: SheetState,
    holder: BookmarkBottomSheetStateHolder?,
    uiState: BookmarkSheetUiState,
    onAfterApply: () -> Unit = {},
    onAfterUnbookmark: () -> Unit = {},
) {
    if (holder == null || !uiState.isVisible) {
        // 表示条件を満たさない場合は何も表示しない。
        return
    }

    // --- Bottom sheet ---
    BookmarkBottomSheet(
        sheetState = sheetState,
        onDismissRequest = { holder.close() },
        groups = uiState.groups,
        selectedGroupId = uiState.selectedGroupId,
        onGroupSelected = { groupId ->
            holder.applyGroup(groupId)
            holder.close()
            onAfterApply()
        },
        onUnbookmarkRequested = {
            holder.unbookmarkTargets()
            holder.close()
            onAfterUnbookmark()
        },
        onAddGroup = { holder.openAddGroupDialog() },
        onGroupLongClick = { group -> holder.openEditGroupDialog(group) }
    )

    // --- Add group dialog ---
    if (uiState.showAddGroupDialog) {
        AddGroupDialog(
            onDismissRequest = { holder.closeAddGroupDialog() },
            isEdit = uiState.editingGroupId != null,
            onConfirm = { holder.confirmGroup() },
            onDelete = { holder.requestDeleteGroup() },
            onValueChange = { holder.setEnteredGroupName(it) },
            enteredValue = uiState.enteredGroupName,
            onColorSelected = { holder.setSelectedColor(it) },
            selectedColor = uiState.selectedColor
        )
    }

    // --- Delete group dialog ---
    if (uiState.showDeleteGroupDialog) {
        DeleteGroupDialog(
            groupName = uiState.deleteGroupName,
            itemNames = uiState.deleteGroupItems,
            isBoard = uiState.deleteGroupIsBoard,
            onDismissRequest = { holder.closeDeleteGroupDialog() },
            onConfirm = { holder.confirmDeleteGroup() }
        )
    }
}
