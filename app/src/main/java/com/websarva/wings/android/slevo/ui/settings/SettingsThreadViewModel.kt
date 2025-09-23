package com.websarva.wings.android.slevo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsThreadViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsThreadUiState())
    val uiState: StateFlow<SettingsThreadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeIsTreeSort().collect { isTree ->
                _uiState.update { it.copy(isTreeSort = isTree) }
            }
        }
        viewModelScope.launch {
            repository.observeIsThreadMinimapScrollbarEnabled().collect { enabled ->
                _uiState.update { it.copy(showMinimapScrollbar = enabled) }
            }
        }
    }

    fun updateSort(isTree: Boolean) {
        viewModelScope.launch {
            repository.setTreeSort(isTree)
        }
    }

    fun updateMinimapScrollbar(enabled: Boolean) {
        viewModelScope.launch {
            repository.setThreadMinimapScrollbarEnabled(enabled)
        }
    }
}

data class SettingsThreadUiState(
    val isTreeSort: Boolean = false,
    val showMinimapScrollbar: Boolean = true,
)
