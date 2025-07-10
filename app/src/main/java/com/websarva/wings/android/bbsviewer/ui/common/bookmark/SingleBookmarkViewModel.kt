package com.websarva.wings.android.bbsviewer.ui.common.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.Groupable
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.bbsviewer.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.bbsviewer.ui.theme.BookmarkColor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SingleBookmarkViewModel @AssistedInject constructor(
    private val boardBookmarkRepo: BookmarkBoardRepository,
    private val threadBookmarkRepo: ThreadBookmarkRepository,
    @Assisted private val boardInfo: BoardInfo,
    @Assisted private val threadInfo: ThreadInfo? // スレッド画面の場合のみ渡される
) : ViewModel() {

    private val _uiState = MutableStateFlow(SingleBookmarkState())
    val uiState: StateFlow<SingleBookmarkState> = _uiState.asStateFlow()

    init {
        // boardInfoは必須、threadInfoの有無で板画面かスレ画面かを判断
        if (threadInfo == null) {
            observeBoardBookmark()
        } else {
            observeThreadBookmark()
        }
        observeGroups()
    }

    private fun observeGroups() {
        viewModelScope.launch {
            val groupsFlow = if (threadInfo == null) {
                boardBookmarkRepo.observeGroups()
            } else {
                threadBookmarkRepo.observeAllGroups()
            }
            groupsFlow.collect { groups ->
                _uiState.update { it.copy(groups = groups) }
            }
        }
    }

    private fun observeBoardBookmark() {
        viewModelScope.launch {
            boardBookmarkRepo.getBoardWithBookmarkAndGroupByUrlFlow(boardInfo.url)
                .collect { boardWithBookmark ->
                    _uiState.update {
                        it.copy(
                            isBookmarked = boardWithBookmark?.bookmarkWithGroup != null,
                            selectedGroup = boardWithBookmark?.bookmarkWithGroup?.group
                        )
                    }
                }
        }
    }

    private fun observeThreadBookmark() {
        viewModelScope.launch {
            threadInfo?.let {
                threadBookmarkRepo.getBookmarkWithGroup(it.key, it.url)
                    .collect { threadWithBookmark ->
                        _uiState.update {
                            it.copy(
                                isBookmarked = threadWithBookmark != null,
                                selectedGroup = threadWithBookmark?.group
                            )
                        }
                    }
            }
        }
    }

    fun unbookmark() {
        viewModelScope.launch {
            if (threadInfo == null) { // Board
                boardBookmarkRepo.deleteBookmark(boardInfo.boardId)
            } else { // Thread
                threadBookmarkRepo.deleteBookmark(threadInfo.key, threadInfo.url)
            }
            closeBookmarkSheet()
        }
    }

    fun saveBookmark(groupId: Long) {
        viewModelScope.launch {
            if (threadInfo == null) { // Board
                boardBookmarkRepo.upsertBookmark(BookmarkBoardEntity(boardInfo.boardId, groupId))
            } else { // Thread
                threadBookmarkRepo.insertBookmark(
                    BookmarkThreadEntity(
                        threadKey = threadInfo.key,
                        boardUrl = boardInfo.url,
                        boardId = boardInfo.boardId,
                        groupId = groupId,
                        title = threadInfo.title,
                        boardName = boardInfo.name,
                        resCount = threadInfo.resCount
                    )
                )
            }
            closeBookmarkSheet()
        }
    }

    fun openBookmarkSheet() = _uiState.update { it.copy(showBookmarkSheet = true) }
    fun closeBookmarkSheet() = _uiState.update { it.copy(showBookmarkSheet = false) }

    fun openAddGroupDialog() = _uiState.update {
        it.copy(
            showAddGroupDialog = true,
            enteredGroupName = "",
            selectedColor = BookmarkColor.RED.value,
            editingGroupId = null
        )
    }

    fun openEditGroupDialog(group: Groupable) = _uiState.update {
        it.copy(
            showAddGroupDialog = true,
            enteredGroupName = group.name,
            selectedColor = group.colorName,
            editingGroupId = group.id
        )
    }

    fun closeAddGroupDialog() = _uiState.update {
        it.copy(
            showAddGroupDialog = false,
            enteredGroupName = "",
            selectedColor = BookmarkColor.RED.value,
            editingGroupId = null
        )
    }

    fun setEnteredGroupName(name: String) = _uiState.update { it.copy(enteredGroupName = name) }
    fun setSelectedColor(color: String) = _uiState.update { it.copy(selectedColor = color) }

    private suspend fun addGroup(name: String, color: String) {
        if (threadInfo == null) {
            boardBookmarkRepo.addGroupAtEnd(name, color)
        } else {
            threadBookmarkRepo.addGroupAtEnd(name, color)
        }
    }

    private suspend fun updateGroup(id: Long, name: String, color: String) {
        if (threadInfo == null) {
            boardBookmarkRepo.updateGroup(id, name, color)
        } else {
            threadBookmarkRepo.updateGroup(id, name, color)
        }
    }

    private suspend fun deleteGroup(id: Long) {
        if (threadInfo == null) {
            boardBookmarkRepo.deleteGroup(id)
        } else {
            threadBookmarkRepo.deleteGroup(id)
        }
    }

    fun confirmGroup() {
        viewModelScope.launch {
            val name = _uiState.value.enteredGroupName.takeIf { it.isNotBlank() } ?: return@launch
            val color = _uiState.value.selectedColor
            val editId = _uiState.value.editingGroupId
            if (editId == null) {
                addGroup(name, color)
            } else {
                updateGroup(editId, name, color)
            }
            closeAddGroupDialog()
        }
    }

    fun deleteEditingGroup() {
        val groupId = _uiState.value.editingGroupId ?: return
        viewModelScope.launch {
            deleteGroup(groupId)
            closeAddGroupDialog()
        }
    }

    fun requestDeleteGroup() {
        val groupId = _uiState.value.editingGroupId ?: return
        viewModelScope.launch {
            val groupName = _uiState.value.groups.find { it.id == groupId }?.name ?: return@launch
            val items = if (threadInfo == null) {
                boardBookmarkRepo.observeGroupsWithBoards().first()
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
                    deleteGroupIsBoard = threadInfo == null
                )
            }
        }
    }

    fun confirmDeleteGroup() {
        val groupId = _uiState.value.editingGroupId ?: return
        viewModelScope.launch {
            deleteGroup(groupId)
            _uiState.update { it.copy(showDeleteGroupDialog = false) }
            closeAddGroupDialog()
        }
    }

    fun closeDeleteGroupDialog() {
        _uiState.update { it.copy(showDeleteGroupDialog = false) }
    }
}


// --- HiltがこのFactoryを生成できるように設定 ---
@AssistedFactory
interface SingleBookmarkViewModelFactory {
    fun create(
        boardInfo: BoardInfo,
        threadInfo: ThreadInfo?
    ): SingleBookmarkViewModel
}
