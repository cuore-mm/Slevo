package com.websarva.wings.android.slevo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.ThemeMode
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 全般設定のUI状態を管理するViewModel。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // DataStore からの状態変化を購読して UI ステートに反映
        viewModelScope.launch {
            repository.observeThemeMode()
                .collect { mode ->
                    _uiState.update { it.copy(themeMode = mode) }
                }
        }
    }

    /**
     * 選択されたテーマモードを保存する。
     */
    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }
}

/**
 * 全般設定画面の表示状態。
 */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
