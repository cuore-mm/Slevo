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

/**
 * スレッド表示設定画面のUI状態を管理するViewModel。
 */
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

    /**
     * デフォルト並び順を更新する。
     */
    fun updateSort(isTree: Boolean) {
        viewModelScope.launch {
            repository.setTreeSort(isTree)
        }
    }

    /**
     * ミニマップ付きスクロールバー設定を更新する。
     */
    fun updateMinimapScrollbar(enabled: Boolean) {
        viewModelScope.launch {
            repository.setThreadMinimapScrollbarEnabled(enabled)
        }
    }
}

/**
 * スレッド表示設定画面の表示状態。
 */
data class SettingsThreadUiState(
    val isTreeSort: Boolean = false,
    val showMinimapScrollbar: Boolean = true,
)
