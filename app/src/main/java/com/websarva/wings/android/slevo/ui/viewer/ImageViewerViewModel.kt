package com.websarva.wings.android.slevo.ui.viewer

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 画像ビューア画面の UI 状態を管理する ViewModel。
 *
 * 画面内メニューの開閉状態など、描画に必要な状態を保持する。
 */
@HiltViewModel
class ImageViewerViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(ImageViewerUiState())
    val uiState: StateFlow<ImageViewerUiState> = _uiState.asStateFlow()

    fun showTopBarMenu() {
        _uiState.update { it.copy(isTopBarMenuExpanded = true) }
    }

    fun hideTopBarMenu() {
        _uiState.update { it.copy(isTopBarMenuExpanded = false) }
    }
}

/**
 * 画像ビューア画面の描画状態。
 *
 * トップバーのその他メニュー開閉を保持する。
 */
data class ImageViewerUiState(
    val isTopBarMenuExpanded: Boolean = false,
)
