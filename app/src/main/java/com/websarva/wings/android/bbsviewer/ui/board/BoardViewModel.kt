package com.websarva.wings.android.bbsviewer.ui.board

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.repository.BoardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
@HiltViewModel
class BoardViewModel @Inject constructor(
    private val repository: BoardRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val boardUrl = savedStateHandle.get<String>("boardUrl")
        ?: error("boardUrl is required")
    private val boardName = savedStateHandle.get<String>("boardName")
        ?: error("boardName is required")

    private val _uiState = MutableStateFlow(
        BoardUiState(
            boardInfo = BoardInfo(name = boardName, url = boardUrl)
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
                _uiState.update { it.copy(threads   = threads) }
            } catch (e: Exception) {
                // 必要に応じて errorMessage を入れる
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }
}

