package com.websarva.wings.android.bbsviewer.ui.thread.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.NgType
import com.websarva.wings.android.bbsviewer.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.bbsviewer.data.repository.NgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.websarva.wings.android.bbsviewer.ui.thread.state.NgUiState

@HiltViewModel
class NgViewModel @Inject constructor(
    repository: BookmarkBoardRepository,
    private val ngRepository: NgRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NgUiState())
    val uiState: StateFlow<NgUiState> = _uiState.asStateFlow()

    private val boards: StateFlow<List<BoardInfo>> = repository.observeAllBoards()
        .map { list -> list.map { BoardInfo(it.boardId, it.name, it.url) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredBoards: StateFlow<List<BoardInfo>> = boards
        .combine(_uiState.map { it.boardQuery }) { list, query ->
            if (query.isBlank()) {
                list
            } else {
                list.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.url.contains(query, ignoreCase = true)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun initialize(
        id: Long?,
        text: String,
        boardName: String,
        boardId: Long?,
        type: NgType,
        isRegex: Boolean,
    ) {
        _uiState.update {
            it.copy(
                id = id,
                text = text,
                boardName = boardName,
                boardId = boardId,
                isAllBoards = boardId == null,
                type = type,
                isRegex = isRegex,
            )
        }
    }

    fun setText(text: String) {
        _uiState.update { it.copy(text = text) }
    }

    fun setBoard(info: BoardInfo) {
        _uiState.update {
            it.copy(
                boardName = info.name,
                boardId = info.boardId.takeIf { id -> id != 0L },
                isAllBoards = info.boardId == 0L,
            )
        }
    }

    fun setBoardQuery(query: String) {
        _uiState.update { it.copy(boardQuery = query) }
    }

    fun setRegex(isRegex: Boolean) {
        _uiState.update { it.copy(isRegex = isRegex) }
    }

    fun setShowBoardDialog(show: Boolean) {
        _uiState.update { it.copy(showBoardDialog = show) }
    }

    fun saveNg() {
        val state = _uiState.value
        viewModelScope.launch {
            ngRepository.addNg(
                state.text,
                state.isRegex,
                state.type,
                if (state.isAllBoards) null else state.boardId,
                state.id,
            )
        }
    }
}
