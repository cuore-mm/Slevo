package com.websarva.wings.android.bbsviewer.ui.appbar

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class TopAppBarViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(TopAppBarUiState())
    val uiState: StateFlow<TopAppBarUiState> = _uiState.asStateFlow()

    fun setTopAppBar(
        isCenter: Boolean = true,
        title: String,
    ) {
        _uiState.value = _uiState.value.copy(isCenter = isCenter)
        _uiState.value = _uiState.value.copy(title = title)
    }

    fun addTitle(title: String):String {
        return "${uiState.value.title} > $title"
    }
}
