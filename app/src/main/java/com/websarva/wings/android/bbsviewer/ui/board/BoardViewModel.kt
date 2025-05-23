package com.websarva.wings.android.bbsviewer.ui.board

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardGroupEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BookmarkBoardEntity
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.repository.BoardRepository
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class BoardViewModel @Inject constructor(
    private val repository: BoardRepository,
    private val bookmarkRepo: BookmarkBoardRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val boardUrl = savedStateHandle.get<String>("boardUrl")
        ?: error("boardUrl is required")
    private val boardName = savedStateHandle.get<String>("boardName")
        ?: error("boardName is required")

    private val _uiState = MutableStateFlow(
        BoardUiState(
            boardInfo = BoardInfo(
                boardId = savedStateHandle.get<Long>("boardId") ?: 0,
                name = boardName,
                url = boardUrl
            )
        )
    )
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    init {
        // 初期化時に一度だけ subject.txt をロード
        loadThreadList()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadThreadList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val threads = repository.getThreadList("$boardUrl/subject.txt")
                _uiState.update { it.copy(threads = threads) }
            } catch (e: Exception) {
                // 必要に応じて errorMessage を入れる
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadGroups() {
        viewModelScope.launch {
            bookmarkRepo.observeGroups()         // Flow<List<BoardGroupEntity>>
                .collect { groups ->
                    _uiState.update { it.copy(groups = groups) }
                }
        }
    }

    /** ブックマークシートを開く */
    fun openBookmarkSheet() = viewModelScope.launch {
        _uiState.update { it.copy(showBookmarkSheet = true) }
    }

    /** ブックマークシートを閉じる */
    fun closeBookmarkSheet() = viewModelScope.launch {
        _uiState.update { it.copy(showBookmarkSheet = false) }
    }

    /** グループ追加ダイアログを開く */
    fun openAddGroupDialog() = viewModelScope.launch {
        _uiState.update { it.copy(showAddGroupDialog = true) }
    }

    /** グループ追加ダイアログを閉じる */
    fun closeAddGroupDialog() = viewModelScope.launch {
        _uiState.update { it.copy(showAddGroupDialog = false) }
    }

    /** グループ名をセット */
    fun setGroupName(name: String) = viewModelScope.launch {
        _uiState.update { it.copy(enteredGroupName = name) }
    }

    /** カラーコードをセット */
    fun setColorCode(color: String) = viewModelScope.launch {
        _uiState.update { it.copy(selectedColor = color) }
    }

    fun addGroup() = viewModelScope.launch {
        val name = uiState.value.enteredGroupName.takeIf { it.isNotBlank() } ?: return@launch
        val color = uiState.value.selectedColor ?: return@launch
        bookmarkRepo.addGroupAtEnd(name, color)
        closeAddGroupDialog()
    }

    /**
     * お気に入りを登録または更新
     */
    fun saveBookmark(groupId: Long) {
        viewModelScope.launch {
            bookmarkRepo.upsertBookmark(
                BookmarkBoardEntity(
                    boardId = uiState.value.boardInfo.boardId,
                    groupId = groupId,
                )
            )
        }
        closeBookmarkSheet()
    }

    /**
     * お気に入りを解除
     */
    fun deleteBookmark(bookmark: BookmarkBoardEntity) {
        viewModelScope.launch {
            bookmarkRepo.deleteBookmark(bookmark)
        }
    }

}

data class BoardUiState(
    val threads: List<ThreadInfo>? = null,
    val isLoading: Boolean = false,
    val boardInfo: BoardInfo = BoardInfo(0, "", ""),
    val isBookmarked: Boolean = false,
    val groups: List<BoardGroupEntity> = emptyList(),
    val selectedGroup: BoardGroupEntity? = null,
    val showBookmarkSheet: Boolean = false,
    val showAddGroupDialog: Boolean = false,
    val selectedColor: String? = null,
    val enteredGroupName: String = "",
)
