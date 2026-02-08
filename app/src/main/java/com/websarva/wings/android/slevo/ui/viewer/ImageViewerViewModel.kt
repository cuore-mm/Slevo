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

    fun toggleTopBarMenu() {
        _uiState.update { state ->
            state.copy(isTopBarMenuExpanded = !state.isTopBarMenuExpanded)
        }
    }

    fun hideTopBarMenu() {
        _uiState.update { it.copy(isTopBarMenuExpanded = false) }
    }

    /**
     * 画像URLを対象に NG ダイアログを開く。
     */
    fun openImageNgDialog(url: String) {
        if (url.isBlank()) {
            // Guard: 空URLは NG 登録対象にしない。
            return
        }
        _uiState.update {
            it.copy(
                showImageNgDialog = true,
                imageNgTargetUrl = url,
            )
        }
    }

    /**
     * 画像URLの NG ダイアログを閉じる。
     */
    fun closeImageNgDialog() {
        _uiState.update {
            it.copy(
                showImageNgDialog = false,
                imageNgTargetUrl = null,
            )
        }
    }
}

/**
 * 画像ビューア画面の描画状態。
 *
 * トップバーのその他メニュー開閉を保持する。
 */
data class ImageViewerUiState(
    val isTopBarMenuExpanded: Boolean = false,
    val showImageNgDialog: Boolean = false,
    val imageNgTargetUrl: String? = null,
)
