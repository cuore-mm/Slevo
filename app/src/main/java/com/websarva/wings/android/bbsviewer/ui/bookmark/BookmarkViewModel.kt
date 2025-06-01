package com.websarva.wings.android.bbsviewer.ui.bookmark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithBoards
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.GroupWithThreadBookmarks
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
}

data class BookmarkUiState(
    val isLoading: Boolean = false,
    val boardList: List<GroupWithBoards> = emptyList(),
    val groupedThreadBookmarks: List<GroupWithThreadBookmarks> = emptyList()
)
