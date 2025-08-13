package com.websarva.wings.android.bbsviewer.ui.thread.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import com.websarva.wings.android.bbsviewer.ui.thread.state.NgIdUiState

@HiltViewModel
class NgIdViewModel @Inject constructor(
    repository: BookmarkBoardRepository
) : ViewModel() {
    val boards: StateFlow<List<BoardInfo>> = repository.observeAllBoards()
        .map { list -> list.map { BoardInfo(it.boardId, it.name, it.url) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(NgIdUiState())
    val uiState: StateFlow<NgIdUiState> = _uiState.asStateFlow()

    fun initialize(text: String, board: String) {
        _uiState.update { it.copy(text = text, board = board) }
    }

    fun setText(text: String) {
        _uiState.update { it.copy(text = text) }
    }

    fun setBoard(board: String) {
        _uiState.update { it.copy(board = board) }
    }

    fun setRegex(isRegex: Boolean) {
        _uiState.update { it.copy(isRegex = isRegex) }
    }

    fun setShowBoardDialog(show: Boolean) {
        _uiState.update { it.copy(showBoardDialog = show) }
    }
}
