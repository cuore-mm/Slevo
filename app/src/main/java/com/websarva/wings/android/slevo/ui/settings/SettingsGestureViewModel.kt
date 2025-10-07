package com.websarva.wings.android.slevo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsGestureViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsGestureUiState())
    val uiState: StateFlow<SettingsGestureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeGestureSettings()
                .collect { settings ->
                    _uiState.update { state ->
                        state.copy(
                            isGestureEnabled = settings.isEnabled,
                            gestureItems = settings.toGestureItems(),
                        )
                    }
                }
        }
    }

    fun toggleGesture(enabled: Boolean) {
        if (!enabled) {
            _uiState.update { it.copy(selectedDirection = null) }
        }
        viewModelScope.launch {
            repository.setGestureEnabled(enabled)
        }
    }

    fun onGestureItemClick(direction: GestureDirection) {
        _uiState.update { it.copy(selectedDirection = direction) }
    }

    fun dismissGestureDialog() {
        _uiState.update { it.copy(selectedDirection = null) }
    }

    fun assignGestureAction(direction: GestureDirection, action: GestureAction?) {
        viewModelScope.launch {
            repository.setGestureAction(direction, action)
            _uiState.update { it.copy(selectedDirection = null) }
        }
    }

    private fun GestureSettings.toGestureItems(): List<GestureItem> =
        GestureDirection.entries.map { direction ->
            GestureItem(
                direction = direction,
                action = assignments[direction],
            )
        }
}

data class SettingsGestureUiState(
    val isGestureEnabled: Boolean = GestureSettings.DEFAULT.isEnabled,
    val gestureItems: List<GestureItem> = GestureDirection.entries.map { direction ->
        GestureItem(direction = direction, action = GestureSettings.DEFAULT.assignments[direction])
    },
    val selectedDirection: GestureDirection? = null,
)

data class GestureItem(
    val direction: GestureDirection,
    val action: GestureAction?,
)
