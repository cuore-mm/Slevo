package com.websarva.wings.android.bbsviewer.ui.bottombar

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class BottomBarViewModel @Inject constructor() : ViewModel(){
    private val _uiState = MutableStateFlow(BottomBarUiState())
    val uiState: StateFlow<BottomBarUiState> = _uiState.asStateFlow()
}
