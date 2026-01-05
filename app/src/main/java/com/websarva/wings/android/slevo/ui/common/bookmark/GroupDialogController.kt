package com.websarva.wings.android.slevo.ui.common.bookmark

import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.ui.theme.BookmarkColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ブックマークグループ編集とダイアログ制御を共通化するコントローラ。
 *
 * 画面ごとの UI 状態に GroupDialogState を内包させ、共通操作をまとめる。
 */
class GroupDialogController<T>(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<T>,
    private val getDialogState: (T) -> GroupDialogState,
    private val setDialogState: (T, GroupDialogState) -> T,
    private val config: Config,
) {

    /**
     * 削除確認ダイアログで表示するデータをまとめたモデル。
     */
    data class DeleteDialogData(
        val groupName: String,
        val items: List<String>,
    )

    /**
     * グループ編集に必要な依存をまとめた設定。
     */
    data class Config(
        val addGroup: suspend (isBoard: Boolean, name: String, color: String) -> Unit,
        val updateGroup: suspend (isBoard: Boolean, id: Long, name: String, color: String) -> Unit,
        val deleteGroup: suspend (isBoard: Boolean, id: Long) -> Unit,
        val loadDeleteDialogData: suspend (isBoard: Boolean, id: Long) -> DeleteDialogData?,
        val defaultColorName: String = BookmarkColor.RED.value,
    )

    /** グループ追加ダイアログを開く。 */
    fun openAddGroupDialog(isBoard: Boolean) {
        updateDialogState {
            it.copy(
                showAddGroupDialog = true,
                enteredGroupName = "",
                selectedColor = config.defaultColorName,
                editingGroupId = null,
                dialogTargetIsBoard = isBoard,
            )
        }
    }

    /** グループ編集ダイアログを開く。 */
    fun openEditGroupDialog(group: Groupable, isBoard: Boolean) {
        updateDialogState {
            it.copy(
                showAddGroupDialog = true,
                enteredGroupName = group.name,
                selectedColor = group.colorName,
                editingGroupId = group.id,
                dialogTargetIsBoard = isBoard,
            )
        }
    }

    /** グループ追加/編集ダイアログを閉じる。 */
    fun closeAddGroupDialog() {
        updateDialogState {
            it.copy(
                showAddGroupDialog = false,
                enteredGroupName = "",
                selectedColor = config.defaultColorName,
                editingGroupId = null,
            )
        }
    }

    /** 入力中のグループ名を更新する。 */
    fun setEnteredGroupName(name: String) {
        updateDialogState { it.copy(enteredGroupName = name) }
    }

    /** 選択中のグループカラーを更新する。 */
    fun setSelectedColor(color: String) {
        updateDialogState { it.copy(selectedColor = color) }
    }

    /**
     * グループ追加または更新を確定する。
     */
    fun confirmGroup() {
        scope.launch {
            val dialogState = getDialogState(state.value)
            val name = dialogState.enteredGroupName.takeIf { it.isNotBlank() }
                ?: return@launch // 空入力は保存しない
            val color = dialogState.selectedColor
            val isBoard = dialogState.dialogTargetIsBoard
            val editId = dialogState.editingGroupId
            if (editId == null) {
                config.addGroup(isBoard, name, color)
            } else {
                config.updateGroup(isBoard, editId, name, color)
            }
            closeAddGroupDialog()
        }
    }

    /**
     * 削除確認ダイアログを開き、対象情報を読み込む。
     */
    fun requestDeleteGroup() {
        val dialogState = getDialogState(state.value)
        val groupId = dialogState.editingGroupId ?: return // 対象がない場合は処理しない
        val isBoard = dialogState.dialogTargetIsBoard
        scope.launch {
            val deleteData = config.loadDeleteDialogData(isBoard, groupId)
                ?: return@launch // 取得失敗時は開かない
            updateDialogState {
                it.copy(
                    showDeleteGroupDialog = true,
                    deleteGroupName = deleteData.groupName,
                    deleteGroupItems = deleteData.items,
                    dialogTargetIsBoard = isBoard,
                )
            }
        }
    }

    /**
     * グループ削除を確定し、関連ダイアログを閉じる。
     */
    fun confirmDeleteGroup() {
        val dialogState = getDialogState(state.value)
        val groupId = dialogState.editingGroupId ?: return // 対象がない場合は処理しない
        val isBoard = dialogState.dialogTargetIsBoard
        scope.launch {
            config.deleteGroup(isBoard, groupId)
            updateDialogState { it.copy(showDeleteGroupDialog = false) }
            closeAddGroupDialog()
        }
    }

    /** 削除確認ダイアログを閉じる。 */
    fun closeDeleteGroupDialog() {
        updateDialogState { it.copy(showDeleteGroupDialog = false) }
    }

    /**
     * 内包する GroupDialogState を安全に更新する。
     */
    private fun updateDialogState(transform: (GroupDialogState) -> GroupDialogState) {
        state.update { current ->
            val dialogState = getDialogState(current)
            setDialogState(current, transform(dialogState))
        }
    }
}
