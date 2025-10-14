package com.websarva.wings.android.slevo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ThreadHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeHistories()
                .collect { histories ->
                    _uiState.update { it.copy(histories = histories) }
                }
        }
    }

    fun startSelection(target: ThreadHistoryDao.HistoryWithLastAccess) {
        val threadId = target.history.threadId
        _uiState.update { state ->
            state.copy(selectedThreadIds = state.selectedThreadIds + threadId)
        }
    }

    fun toggleSelection(target: ThreadHistoryDao.HistoryWithLastAccess) {
        val threadId = target.history.threadId
        _uiState.update { state ->
            val newSelection = if (state.selectedThreadIds.contains(threadId)) {
                state.selectedThreadIds - threadId
            } else {
                state.selectedThreadIds + threadId
            }
            state.copy(selectedThreadIds = newSelection)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedThreadIds = emptySet()) }
    }

    fun deleteSelectedHistories() {
        val selected = _uiState.value.selectedThreadIds
        if (selected.isEmpty()) return
        viewModelScope.launch {
            repository.deleteHistories(selected)
            _uiState.update { it.copy(selectedThreadIds = emptySet()) }
        }
    }
}

data class HistoryUiState(
    val histories: List<ThreadHistoryDao.HistoryWithLastAccess> = emptyList(),
    val selectedThreadIds: Set<ThreadId> = emptySet(),
)
