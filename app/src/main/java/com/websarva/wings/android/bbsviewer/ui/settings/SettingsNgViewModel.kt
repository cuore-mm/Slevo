package com.websarva.wings.android.bbsviewer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.bbsviewer.data.model.NgType
import com.websarva.wings.android.bbsviewer.data.repository.NgRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsNgViewModel @Inject constructor(
    private val repository: NgRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsNgUiState())
    val uiState: StateFlow<SettingsNgUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeNgs().collect { ngs ->
                _uiState.update { it.copy(ngs = ngs) }
            }
        }
    }

    fun selectTab(type: NgType) {
        _uiState.update { it.copy(selectedTab = type) }
    }

    fun startEdit(ng: NgEntity) {
        _uiState.update { it.copy(editingNg = ng) }
    }

    fun endEdit() {
        _uiState.update { it.copy(editingNg = null) }
    }
}

data class SettingsNgUiState(
    val ngs: List<NgEntity> = emptyList(),
    val selectedTab: NgType = NgType.USER_ID,
    val editingNg: NgEntity? = null,
)

