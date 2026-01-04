package com.websarva.wings.android.slevo.ui.common.bookmark

import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.ui.theme.BookmarkColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ブックマークグループの編集とダイアログ制御を共通化するヘルパー。
 *
 * 板/スレッド別の ViewModel から合成して利用する。
 */
class BookmarkGroupEditor(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SingleBookmarkState>,
    private val config: Config,
) {

    /**
     * グループ編集に必要な依存をまとめた設定。
     */
    data class Config(
        val isBoard: Boolean,
        val observeGroups: () -> Flow<List<Groupable>>,
        val addGroup: suspend (name: String, color: String) -> Unit,
        val updateGroup: suspend (id: Long, name: String, color: String) -> Unit,
        val deleteGroup: suspend (id: Long) -> Unit,
        val loadDeleteItems: suspend (groupId: Long) -> List<String>,
        val defaultColorName: String = BookmarkColor.RED.value,
    )

    init {
        observeGroups()
    }

    /**
     * グループ一覧を監視し、UI状態へ反映する。
     */
    private fun observeGroups() {
        scope.launch {
            config.observeGroups().collect { groups ->
                state.update { it.copy(groups = groups) }
            }
        }
    }

    /** グループ追加ダイアログを表示する。 */
    fun openAddGroupDialog() {
        state.update {
            it.copy(
                showAddGroupDialog = true,
                enteredGroupName = "",
                selectedColor = config.defaultColorName,
                editingGroupId = null,
            )
        }
    }

    /** 指定グループを編集対象としてダイアログを表示する。 */
    fun openEditGroupDialog(group: Groupable) {
        state.update {
            it.copy(
                showAddGroupDialog = true,
                enteredGroupName = group.name,
                selectedColor = group.colorName,
                editingGroupId = group.id,
            )
        }
    }

    /** グループ追加/編集ダイアログを閉じる。 */
    fun closeAddGroupDialog() {
        state.update {
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
        state.update { it.copy(enteredGroupName = name) }
    }

    /** 選択中のグループ色を更新する。 */
    fun setSelectedColor(color: String) {
        state.update { it.copy(selectedColor = color) }
    }

    /**
     * グループ追加または更新を確定する。
     */
    fun confirmGroup() {
        scope.launch {
            val name = state.value.enteredGroupName.takeIf { it.isNotBlank() }
                ?: return@launch // 入力が空の場合は処理しない
            val color = state.value.selectedColor
            val editId = state.value.editingGroupId
            if (editId == null) {
                config.addGroup(name, color)
            } else {
                config.updateGroup(editId, name, color)
            }
            closeAddGroupDialog()
        }
    }

    /**
     * 削除確認ダイアログを開き、対象アイテム一覧を準備する。
     */
    fun requestDeleteGroup() {
        val groupId = state.value.editingGroupId
            ?: return // 編集対象がない場合は何もしない
        scope.launch {
            val groupName = state.value.groups.find { it.id == groupId }?.name ?: return@launch
            val items = config.loadDeleteItems(groupId)
            state.update {
                it.copy(
                    showDeleteGroupDialog = true,
                    deleteGroupName = groupName,
                    deleteGroupItems = items,
                    deleteGroupIsBoard = config.isBoard,
                )
            }
        }
    }

    /**
     * グループ削除を確定し、表示状態を閉じる。
     */
    fun confirmDeleteGroup() {
        val groupId = state.value.editingGroupId
            ?: return // 編集対象がない場合は処理しない
        scope.launch {
            config.deleteGroup(groupId)
            state.update { it.copy(showDeleteGroupDialog = false) }
            closeAddGroupDialog()
        }
    }

    /** 削除確認ダイアログを閉じる。 */
    fun closeDeleteGroupDialog() {
        state.update { it.copy(showDeleteGroupDialog = false) }
    }
}
