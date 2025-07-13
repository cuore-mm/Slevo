package com.websarva.wings.android.bbsviewer.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ThreadHistoryDao
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
}

data class HistoryUiState(
    val histories: List<ThreadHistoryDao.HistoryWithLastAccess> = emptyList()
)
