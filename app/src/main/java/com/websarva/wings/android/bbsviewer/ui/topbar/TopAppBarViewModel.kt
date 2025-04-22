package com.websarva.wings.android.bbsviewer.ui.topbar

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class TopAppBarViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(TopAppBarUiState())
    val uiState: StateFlow<TopAppBarUiState> = _uiState.asStateFlow()

    fun setTopAppBar(type: AppBarType) {
        _uiState.update { it.copy(type = type) }
    }

    fun addTitle(title: String): String {
        return "${uiState.value.title} > $title"
    }
}
