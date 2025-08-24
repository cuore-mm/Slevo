package com.websarva.wings.android.bbsviewer.ui.bookmarklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.GroupWithBoards
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.GroupWithThreadBookmarks
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bookmark.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.model.Groupable
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.bbsviewer.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.bbsviewer.ui.theme.BookmarkColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val boardRepo: BookmarkBoardRepository,
    private val threadBookmarkRepo: ThreadBookmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarkUiState())
    val uiState: StateFlow<BookmarkUiState> = _uiState.asStateFlow()

    // 初期化時にお気に入りリストを監視
    init {

        // グループ→板のネストリストを監視して UIState.boardList に流し込む
        viewModelScope.launch {
            boardRepo.observeGroupsWithBoards()
                .collect { groupsWithBoards ->
                    _uiState.update { it.copy(boardList = groupsWithBoards) }
                }
        }

        // スレッドのお気に入り一覧を監視
        viewModelScope.launch {
            threadBookmarkRepo.observeSortedGroupsWithThreadBookmarks()
                .collect { groupedThreads ->
                    _uiState.update { it.copy(groupedThreadBookmarks = groupedThreads) }
                }
        }
    }

    fun toggleSelectMode(enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                selectMode = enabled,
                selectedBoards = if (enabled) state.selectedBoards else emptySet(),
                selectedThreads = if (enabled) state.selectedThreads else emptySet()
            )
        }
    }

    fun toggleBoardSelect(boardId: Long) {
        _uiState.update { state ->
            val next = state.selectedBoards.toMutableSet().apply {
                if (!add(boardId)) remove(boardId)
            }
            state.copy(selectedBoards = next)
        }
    }

    fun toggleThreadSelect(id: String) {
        _uiState.update { state ->
            val next = state.selectedThreads.toMutableSet().apply {
                if (!add(id)) remove(id)
            }
            state.copy(selectedThreads = next)
        }
    }

    fun openEditSheet() {
        val groupId = computeSelectedGroupId()
        _uiState.update { it.copy(showEditSheet = true, selectedGroupId = groupId) }
    }

    fun closeEditSheet() {
        _uiState.update { it.copy(showEditSheet = false, selectedGroupId = null) }
    }

    fun applyGroupToSelection(groupId: Long) {
        val current = _uiState.value
        viewModelScope.launch {
            current.selectedBoards.forEach { id ->
                boardRepo.upsertBookmark(BookmarkBoardEntity(boardId = id, groupId = groupId))
            }
            current.selectedThreads.forEach { key ->
                findThreadEntity(key)?.let { thread ->
                    threadBookmarkRepo.insertBookmark(thread.copy(groupId = groupId))
                }
            }
            _uiState.update {
                it.copy(
                    selectMode = false,
                    selectedBoards = emptySet(),
                    selectedThreads = emptySet(),
                    showEditSheet = false,
                    selectedGroupId = null
                )
            }
        }
    }

    fun unbookmarkSelection() {
        val current = _uiState.value
        viewModelScope.launch {
            current.selectedBoards.forEach { id ->
                boardRepo.deleteBookmark(id)
            }
            current.selectedThreads.forEach { key ->
                findThreadEntity(key)?.let { thread ->
                    threadBookmarkRepo.deleteBookmark(thread.threadKey, thread.boardUrl)
                }
            }
            _uiState.update {
                it.copy(
                    selectMode = false,
                    selectedBoards = emptySet(),
                    selectedThreads = emptySet(),
                    showEditSheet = false,
                    selectedGroupId = null
                )
            }
        }
    }

    private fun computeSelectedGroupId(): Long? {
        val state = _uiState.value
        if (state.selectedBoards.isNotEmpty()) {
            val groups = state.selectedBoards.mapNotNull { findBoardGroupId(it) }.distinct()
            return if (groups.size == 1) groups.first() else null
        }
        if (state.selectedThreads.isNotEmpty()) {
            val groups = state.selectedThreads.mapNotNull { findThreadGroupId(it) }.distinct()
            return if (groups.size == 1) groups.first() else null
        }
        return null
    }

    private fun findBoardGroupId(boardId: Long): Long? {
        _uiState.value.boardList.forEach { g ->
            if (g.boards.any { it.boardId == boardId }) return g.group.groupId
        }
        return null
    }

    private fun findThreadGroupId(key: String): Long? = findThreadEntity(key)?.groupId

    private fun findThreadEntity(key: String): BookmarkThreadEntity? {
        _uiState.value.groupedThreadBookmarks.forEach { g ->
            g.threads.forEach { t ->
                if (t.threadKey + t.boardUrl == key) return t
            }
        }
        return null
    }

    fun openAddGroupDialog(isBoard: Boolean) {
        _uiState.update {
            it.copy(
                showAddGroupDialog = true,
                enteredGroupName = "",
                selectedColor = BookmarkColor.RED.value,
                editingGroupId = null,
                groupDialogIsBoard = isBoard
            )
        }
    }

    fun openEditGroupDialog(group: Groupable, isBoard: Boolean) {
        _uiState.update {
            it.copy(
                showAddGroupDialog = true,
                enteredGroupName = group.name,
                selectedColor = group.colorName,
                editingGroupId = group.id,
                groupDialogIsBoard = isBoard
            )
        }
    }

    fun closeAddGroupDialog() {
        _uiState.update {
            it.copy(
                showAddGroupDialog = false,
                enteredGroupName = "",
                selectedColor = BookmarkColor.RED.value,
                editingGroupId = null
            )
        }
    }

    fun setEnteredGroupName(name: String) {
        _uiState.update { it.copy(enteredGroupName = name) }
    }

    fun setSelectedColor(color: String) {
        _uiState.update { it.copy(selectedColor = color) }
    }

    private suspend fun addGroup(isBoard: Boolean, name: String, color: String) {
        if (isBoard) {
            boardRepo.addGroupAtEnd(name, color)
        } else {
            threadBookmarkRepo.addGroupAtEnd(name, color)
        }
    }

    private suspend fun updateGroup(isBoard: Boolean, id: Long, name: String, color: String) {
        if (isBoard) {
            boardRepo.updateGroup(id, name, color)
        } else {
            threadBookmarkRepo.updateGroup(id, name, color)
        }
    }

    fun confirmGroup() {
        viewModelScope.launch {
            val name = _uiState.value.enteredGroupName.takeIf { it.isNotBlank() } ?: return@launch
            val color = _uiState.value.selectedColor
            val isBoard = _uiState.value.groupDialogIsBoard
            val editId = _uiState.value.editingGroupId
            if (editId == null) {
                addGroup(isBoard, name, color)
            } else {
                updateGroup(isBoard, editId, name, color)
            }
            closeAddGroupDialog()
        }
    }

    fun requestDeleteGroup() {
        val groupId = _uiState.value.editingGroupId ?: return
        viewModelScope.launch {
            val isBoard = _uiState.value.groupDialogIsBoard
            val groupName = if (isBoard) {
                boardRepo.observeGroupsWithBoards().first()
                    .firstOrNull { it.group.groupId == groupId }?.group?.name
            } else {
                threadBookmarkRepo.observeSortedGroupsWithThreadBookmarks().first()
                    .firstOrNull { it.group.groupId == groupId }?.group?.name
            } ?: return@launch

            val items = if (isBoard) {
                boardRepo.observeGroupsWithBoards().first()
                    .firstOrNull { it.group.groupId == groupId }?.boards?.map { it.name } ?: emptyList()
            } else {
                threadBookmarkRepo.observeSortedGroupsWithThreadBookmarks().first()
                    .firstOrNull { it.group.groupId == groupId }?.threads?.map { it.title } ?: emptyList()
            }

            _uiState.update {
                it.copy(
                    showDeleteGroupDialog = true,
                    deleteGroupName = groupName,
                    deleteGroupItems = items,
                    deleteGroupIsBoard = isBoard
                )
            }
        }
    }

    fun confirmDeleteGroup() {
        val groupId = _uiState.value.editingGroupId ?: return
        val isBoard = _uiState.value.groupDialogIsBoard
        viewModelScope.launch {
            if (isBoard) {
                boardRepo.deleteGroup(groupId)
            } else {
                threadBookmarkRepo.deleteGroup(groupId)
            }
            _uiState.update { it.copy(showDeleteGroupDialog = false) }
            closeAddGroupDialog()
        }
    }

    fun closeDeleteGroupDialog() {
        _uiState.update { it.copy(showDeleteGroupDialog = false) }
    }
}

data class BookmarkUiState(
    val isLoading: Boolean = false,
    val boardList: List<GroupWithBoards> = emptyList(),
    val groupedThreadBookmarks: List<GroupWithThreadBookmarks> = emptyList(),
    val selectMode: Boolean = false,
    val selectedBoards: Set<Long> = emptySet(),
    val selectedThreads: Set<String> = emptySet(),
    val showEditSheet: Boolean = false,
    val selectedGroupId: Long? = null,
    val showAddGroupDialog: Boolean = false,
    val enteredGroupName: String = "",
    val selectedColor: String = BookmarkColor.RED.value,
    val editingGroupId: Long? = null,
    val showDeleteGroupDialog: Boolean = false,
    val deleteGroupName: String = "",
    val deleteGroupItems: List<String> = emptyList(),
    val deleteGroupIsBoard: Boolean = true,
    val groupDialogIsBoard: Boolean = true,
)
