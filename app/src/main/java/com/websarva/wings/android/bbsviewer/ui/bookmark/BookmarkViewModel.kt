package com.websarva.wings.android.bbsviewer.ui.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkRepository
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkThreadEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithBoards
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookmarkViewModel @Inject constructor(
    private val threadRepo: BookmarkRepository,
    private val boardRepo: BookmarkBoardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarkUiState())
    val uiState: StateFlow<BookmarkUiState> = _uiState.asStateFlow()

    // 初期化時にお気に入りリストを監視
    init {
        // スレッドのお気に入り一覧を監視
        viewModelScope.launch {
            threadRepo.getAllBookmarks()
                .collect { threads ->
                    _uiState.update { it.copy(bookmarks = threads) }
                }
        }

        // グループ→板のネストリストを監視して UIState.boardList に流し込む
        viewModelScope.launch {
            boardRepo.observeGroupsWithBoards()
                .collect { groupsWithBoards ->
                    _uiState.update { it.copy(boardList = groupsWithBoards) }
                }
        }
    }

    fun addBookmark(bookmark: BookmarkThreadEntity) {
        viewModelScope.launch {
            threadRepo.insertBookmark(bookmark)
        }
    }

    fun removeBookmark(bookmark: BookmarkThreadEntity) {
        viewModelScope.launch {
            threadRepo.deleteBookmark(bookmark)
        }
    }

    val groupsWithBoards: StateFlow<List<GroupWithBoards>> =
        boardRepo.observeGroupsWithBoards()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

data class BookmarkUiState(
    val bookmarks: List<BookmarkThreadEntity> = emptyList(),
    val isLoading: Boolean = false,
    val boardList: List<GroupWithBoards> = emptyList()
)
