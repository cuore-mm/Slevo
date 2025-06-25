package com.websarva.wings.android.bbsviewer.ui.common

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

abstract class BaseViewModel<S> : ViewModel() where S : BaseUiState<S> {
    protected abstract val _uiState: MutableStateFlow<S>
    val uiState: StateFlow<S> get() = _uiState

    // 公開する共通メソッド
    fun openAddGroupDialog() {
        _uiState.update { it.copyState(showAddGroupDialog = true) }
    }

    fun closeAddGroupDialog() {
        _uiState.update {
            it.copyState(
                showAddGroupDialog = false,
                enteredGroupName = "",
                selectedColor = "#FF0000" // デフォルトカラー
            )
        }
    }

    fun setEnteredGroupName(name: String) {
        _uiState.update { it.copyState(enteredGroupName = name) }
    }

    fun setSelectedColor(color: String) {
        _uiState.update { it.copyState(selectedColor = color) }
    }

    fun openTabListSheet() {
        _uiState.update { it.copyState(showTabListSheet = true) }
    }

    fun closeTabListSheet() {
        _uiState.update { it.copyState(showTabListSheet = false) }
    }

    fun release() {
        onCleared()
    }
}
