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

/**
 * ジェスチャー設定画面の状態を管理する ViewModel。
 *
 * 設定値の購読、編集ダイアログ表示状態、各種更新操作を集約する。
 */
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
                            showActionHints = settings.showActionHints,
                            gestureItems = settings.toGestureItems(),
                        )
                    }
                }
        }
    }

    /**
     * ジェスチャー機能の有効/無効を切り替える。
     */
    fun toggleGesture(enabled: Boolean) {
        if (!enabled) {
            _uiState.update { it.copy(selectedDirection = null) }
        }
        viewModelScope.launch {
            repository.setGestureEnabled(enabled)
        }
    }

    /**
     * アクションヒント表示設定を切り替える。
     */
    fun toggleGestureShowActionHints(show: Boolean) {
        viewModelScope.launch {
            repository.setGestureShowActionHints(show)
        }
    }

    /**
     * 編集対象の方向を選択し、アクション選択ダイアログを開く。
     */
    fun onGestureItemClick(direction: GestureDirection) {
        _uiState.update { it.copy(selectedDirection = direction) }
    }

    /**
     * アクション選択ダイアログを閉じる。
     */
    fun dismissGestureDialog() {
        _uiState.update { it.copy(selectedDirection = null) }
    }

    /**
     * 設定初期化確認ダイアログを開く。
     */
    fun showResetDialog() {
        _uiState.update { it.copy(isResetDialogVisible = true) }
    }

    /**
     * 設定初期化確認ダイアログを閉じる。
     */
    fun dismissResetDialog() {
        _uiState.update { it.copy(isResetDialogVisible = false) }
    }

    /**
     * 指定方向に割り当てるアクションを保存し、ダイアログを閉じる。
     */
    fun assignGestureAction(direction: GestureDirection, action: GestureAction?) {
        viewModelScope.launch {
            repository.setGestureAction(direction, action)
            _uiState.update { it.copy(selectedDirection = null) }
        }
    }

    /**
     * ジェスチャー設定を既定値に戻し、関連ダイアログを閉じる。
     */
    fun resetGestureSettings() {
        _uiState.update { it.copy(selectedDirection = null, isResetDialogVisible = false) }
        viewModelScope.launch {
            repository.resetGestureSettings()
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

/**
 * ジェスチャー設定画面で描画に必要な UI 状態。
 */
data class SettingsGestureUiState(
    val isGestureEnabled: Boolean = GestureSettings.DEFAULT.isEnabled,
    val showActionHints: Boolean = GestureSettings.DEFAULT.showActionHints,
    val gestureItems: List<GestureItem> = GestureDirection.entries.map { direction ->
        GestureItem(direction = direction, action = GestureSettings.DEFAULT.assignments[direction])
    },
    val selectedDirection: GestureDirection? = null,
    val isResetDialogVisible: Boolean = false,
)

/**
 * 方向ごとのジェスチャー割り当てを表す表示用モデル。
 */
data class GestureItem(
    val direction: GestureDirection,
    val action: GestureAction?,
)
