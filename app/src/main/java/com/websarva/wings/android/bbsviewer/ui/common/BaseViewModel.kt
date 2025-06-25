package com.websarva.wings.android.bbsviewer.ui.common

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

abstract class BaseViewModel<S> : ViewModel() where S : BaseUiState<S> {
    protected abstract val _uiState: MutableStateFlow<S>
    val uiState: StateFlow<S> get() = _uiState

    // Common methods
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
