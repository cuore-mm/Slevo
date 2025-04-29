package com.websarva.wings.android.bbsviewer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.bbsviewer.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // DataStore からの状態変化を購読して UI ステートに反映
        viewModelScope.launch {
            repository.observeIsDarkMode()
                .collect { isDark ->
                    _uiState.update { it.copy(isDark = isDark) }
                }
        }
    }

    /** トグル呼び出しでテーマを切り替え、DataStore に保存 */
    fun toggleTheme() {
        val next = !_uiState.value.isDark
        viewModelScope.launch {
            repository.setDarkMode(next)
        }
    }
}

data class SettingsUiState(
    val isDark: Boolean = false
)
