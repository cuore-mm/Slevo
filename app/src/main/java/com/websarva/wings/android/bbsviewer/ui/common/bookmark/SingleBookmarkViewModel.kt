package com.websarva.wings.android.bbsviewer.ui.common.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.bbsviewer.data.repository.ThreadBookmarkRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BookmarkStateViewModel @AssistedInject constructor(
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
    }

    private fun observeBoardBookmark() {
        viewModelScope.launch {
            boardBookmarkRepo.getBoardWithBookmarkAndGroupByUrlFlow(boardInfo.url).collect { boardWithBookmark ->
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
                threadBookmarkRepo.getBookmarkWithGroup(it.key, it.url).collect { threadWithBookmark ->
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

    fun openBookmarkSheet() {
        viewModelScope.launch {
            val groupsFlow = if (threadInfo == null) {
                boardBookmarkRepo.observeGroups()
            } else {
                threadBookmarkRepo.observeAllGroups()
            }
            groupsFlow.first().let { groups ->
                _uiState.update { it.copy(showBookmarkSheet = true, groups = groups) }
            }
        }
    }

    fun closeBookmarkSheet() = _uiState.update { it.copy(showBookmarkSheet = false) }
    fun openAddGroupDialog() = _uiState.update { it.copy(showAddGroupDialog = true) }
    fun closeAddGroupDialog() = _uiState.update { it.copy(showAddGroupDialog = false, enteredGroupName = "", selectedColor = "#FF0000") }
    fun setEnteredGroupName(name: String) = _uiState.update { it.copy(enteredGroupName = name) }
    fun setSelectedColor(color: String) = _uiState.update { it.copy(selectedColor = color) }

    fun addGroup() {
        viewModelScope.launch {
            val name = _uiState.value.enteredGroupName.takeIf { it.isNotBlank() } ?: return@launch
            val color = _uiState.value.selectedColor
            if (threadInfo == null) {
                boardBookmarkRepo.addGroupAtEnd(name, color)
            } else {
                threadBookmarkRepo.addGroupAtEnd(name, color)
            }
            closeAddGroupDialog()
        }
    }
}


// --- HiltがこのFactoryを生成できるように設定 ---
@AssistedFactory
interface BookmarkStateViewModelFactory {
    fun create(
        boardInfo: BoardInfo,
        threadInfo: ThreadInfo?
    ): BookmarkStateViewModel
}
