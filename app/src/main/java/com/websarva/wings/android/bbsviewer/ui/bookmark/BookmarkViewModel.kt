package com.websarva.wings.android.bbsviewer.ui.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithBoards
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithThreadBookmarks
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.bbsviewer.data.repository.ThreadBookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)
