package com.websarva.wings.android.slevo.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel<S> : ViewModel() where S : BaseUiState<S> {
    protected abstract val _uiState: MutableStateFlow<S>
    val uiState: StateFlow<S> get() = _uiState

    private var isInitialized = false

    /**
     * ViewModelの初期化を行う標準メソッド。
     * UI側からはこのメソッドを呼び出すように統一する。
     * @param force trueの場合、すでに初期化済みでも強制的に再実行する（更新処理など）
     */
    fun initialize(force: Boolean = false) {
        if (!force && isInitialized) return // 強制でない限り、再初期化はしない
        isInitialized = true
        viewModelScope.launch {
            // 具象クラスで実装されるデータ読み込み処理を呼び出す
            loadData(isRefresh = force)
        }
    }

    /**
     * データの読み込み処理。具象クラスで必ず実装する。
     * @param isRefresh trueの場合はキャッシュを無視した強制的な更新を意図する
     */
    protected abstract suspend fun loadData(isRefresh: Boolean)

    fun release() {
        onCleared()
    }
}
