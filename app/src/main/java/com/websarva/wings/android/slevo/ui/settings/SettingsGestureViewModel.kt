package com.websarva.wings.android.slevo.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.websarva.wings.android.slevo.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class SettingsGestureViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsGestureUiState())
    val uiState: StateFlow<SettingsGestureUiState> = _uiState.asStateFlow()

    fun toggleGesture(enabled: Boolean) {
        _uiState.update { it.copy(isGestureEnabled = enabled) }
    }

    fun onGestureItemClick(direction: GestureDirection) {
        _uiState.update { it.copy(selectedDirection = direction) }
    }

    fun dismissGestureDialog() {
        _uiState.update { it.copy(selectedDirection = null) }
    }

    fun assignGestureAction(direction: GestureDirection, action: GestureAction?) {
        _uiState.update { state ->
            val updatedItems = state.gestureItems.map { item ->
                if (item.direction == direction) {
                    item.copy(action = action)
                } else {
                    item
                }
            }
            state.copy(
                gestureItems = updatedItems,
                selectedDirection = null
            )
        }
    }
}

data class SettingsGestureUiState(
    val isGestureEnabled: Boolean = false,
    val gestureItems: List<GestureItem> = GestureDirection.values().map { direction ->
        GestureItem(direction = direction, action = null)
    },
    val selectedDirection: GestureDirection? = null,
)

data class GestureItem(
    val direction: GestureDirection,
    val action: GestureAction?,
)

enum class GestureDirection(@StringRes val labelRes: Int) {
    Right(R.string.gesture_direction_right),
    RightUp(R.string.gesture_direction_right_up),
    RightLeft(R.string.gesture_direction_right_left),
    RightDown(R.string.gesture_direction_right_down),
    Left(R.string.gesture_direction_left),
    LeftUp(R.string.gesture_direction_left_up),
    LeftRight(R.string.gesture_direction_left_right),
    LeftDown(R.string.gesture_direction_left_down),
}

enum class GestureAction(@StringRes val labelRes: Int) {
    ToTop(R.string.gesture_action_to_top),
    ToBottom(R.string.gesture_action_to_bottom),
    Refresh(R.string.refresh),
    PostOrCreateThread(R.string.gesture_action_post_or_create_thread),
    Search(R.string.search),
}
