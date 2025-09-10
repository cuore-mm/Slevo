package com.websarva.wings.android.slevo.ui.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 画面全体で利用するページインデックスを保持する ViewModel。
 * 初期値は -1 とし、未設定であることを示す。
 */
@HiltViewModel
class RoutePagerViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(RoutePagerUiState())
    val uiState: StateFlow<RoutePagerUiState> = _uiState.asStateFlow()

    fun setCurrentPage(page: Int) {
        _uiState.update { it.copy(currentPage = page) }
    }
}

/**
 * Pager 用の UiState。currentPage が -1 の場合は未設定として扱う。
 */
data class RoutePagerUiState(
    val currentPage: Int = -1
)
