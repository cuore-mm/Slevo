package com.websarva.wings.android.slevo.ui.bbsroute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 共通の初期化フローを提供する基底ViewModel。
 *
 * 画面ごとのUI状態を扱い、初期化の補助処理を共通化する。
 */
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
     * 初期化済みフラグをリセットし、次回の初期化を許可する。
     */
    protected fun resetInitialization() {
        isInitialized = false
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

/**
 * 画面固有の初期化フローをフェーズ順で統一するための基底ViewModel。
 *
 * 初期化の順序を固定し、画面ごとの差分は各フェーズで実装する。
 */
abstract class InitFlowViewModel<S, Args> : BaseViewModel<S>() where S : BaseUiState<S> {
    private var initializedKey: String? = null

    /**
     * 初期化フローを決められた順序で実行する。
     *
     * @param args 初期化に必要な入力
     * @param force trueの場合、同一キーでも再初期化する
     */
    fun initializeFlow(args: Args, force: Boolean = false) {
        // --- Guard ---
        val initKey = buildInitKey(args)
        val previousKey = initializedKey
        if (!force && previousKey == initKey) {
            // 同一キーの重複初期化は行わない。
            return
        }
        if (!force && previousKey != null && previousKey != initKey) {
            // キー変更時はロード許可をリセットする。
            resetInitialization()
        }
        initializedKey = initKey

        // --- UI state ---
        applyInitialUiState(args)

        // --- Data complement ---
        launchDataComplement(args)

        // --- Observers ---
        startObservers(args)

        // --- Initial load ---
        startInitialLoad(force)
    }

    /**
     * 初期化キーを生成する。
     */
    protected abstract fun buildInitKey(args: Args): String

    /**
     * UIState の初期値を反映する。
     */
    protected abstract fun applyInitialUiState(args: Args)

    /**
     * 永続データの補完や追加取得を開始する。
     */
    protected abstract fun launchDataComplement(args: Args)

    /**
     * 監視系のフローを開始する。
     */
    protected abstract fun startObservers(args: Args)

    /**
     * 初期ロードを開始する。
     */
    protected open fun startInitialLoad(force: Boolean) {
        initialize(force)
    }
}
