package com.websarva.wings.android.slevo.ui.board.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 板画面 route 単位でタブ表示状態を提供する ViewModel。
 *
 * 1つの route ViewModel が tab key ごとの `BoardUiState` を遅延生成し、
 * 選択中タブ変更では同一 ViewModel のまま表示状態だけを切り替える。
 */
@HiltViewModel
class BoardRouteViewModel @Inject constructor(
    private val tabSessionStore: TabSessionStore,
) : ViewModel() {

    private companion object {
        const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L
    }

    private val viewModelCache = mutableMapOf<String, BoardViewModel>()
    private val uiStateCache = mutableMapOf<String, StateFlow<BoardUiState>>()

    /** 現在選択中の板タブ key。 */
    val selectedTabKey: StateFlow<String?> = tabSessionStore.selectedBoardTabKey

    /**
     * 選択中タブの `UiState` を返す。
     */
    val selectedUiState: StateFlow<BoardUiState> = selectedTabKey
        .flatMapLatest { tabKey ->
            if (tabKey == null) {
                flowOf(BoardUiState())
            } else {
                uiStateFor(tabKey)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
            initialValue = BoardUiState(),
        )

    /**
     * 指定タブ key の `UiState` Flow を返す。
     */
    fun uiStateFor(tabKey: String): StateFlow<BoardUiState> {
        return uiStateCache.getOrPut(tabKey) {
            createUiStateFlow(tabKey)
        }
    }

    /**
     * `uiStateFor` と同じ内容を `Flow` として公開する互換 API。
     */
    fun observeUiState(tabKey: String): Flow<BoardUiState> = uiStateFor(tabKey)

    /**
     * Task 6 移行中に既存の操作 API を再利用するため、対象タブの旧 ViewModel を返す。
     *
     * UI は `UiState` 購読を route ViewModel 経由へ切り替えつつ、詳細な操作委譲だけを
     * 互換レイヤーとして既存 ViewModel に橋渡しする。
     */
    fun legacyViewModel(tabKey: String): BoardViewModel = boardViewModelFor(tabKey)

    /**
     * 指定板タブのスレッド一覧を更新する。
     *
     * ViewModel の再生成ではなく、対象タブの既存 ViewModel へ更新要求を送る。
     */
    fun refreshBoard(tabKey: String) {
        boardViewModelFor(tabKey).refreshBoardData()
    }

    /**
     * 選択中タブ key を更新する。
     */
    fun selectTab(boardUrl: String?) {
        tabSessionStore.selectBoardTab(boardUrl)
    }

    /**
     * タブ key に対応する `BoardViewModel` を取得し、必要なら初期化を行う。
     */
    private fun boardViewModelFor(tabKey: String): BoardViewModel {
        return viewModelCache.getOrPut(tabKey) {
            tabSessionStore.getOrCreateBoardViewModel(tabKey).also { viewModel ->
                initializeBoardViewModel(viewModel, findTab(tabKey))
            }
        }
    }

    /**
     * タブ key ごとの共有 `UiState` Flow を組み立てる。
     */
    private fun createUiStateFlow(tabKey: String): StateFlow<BoardUiState> {
        val viewModel = boardViewModelFor(tabKey)
        return tabSessionStore.openBoardTabs
            .map { tabs -> tabs.find { tab -> tab.boardUrl == tabKey } }
            .flatMapLatest { tab ->
                // --- Initialization ---
                initializeBoardViewModel(viewModel, tab)

                // --- Output ---
                if (tab == null) {
                    flowOf(BoardUiState())
                } else {
                    viewModel.uiState
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(UI_STATE_STOP_TIMEOUT_MILLIS),
                initialValue = viewModel.uiState.value,
            )
    }

    /**
     * タブ情報がある場合だけ既存板 ViewModel を初期化する。
     */
    private fun initializeBoardViewModel(
        viewModel: BoardViewModel,
        tab: BoardTabInfo?,
    ) {
        if (tab == null) {
            return
        }
        viewModel.initializeBoard(
            boardInfo = BoardInfo(
                boardId = tab.boardId,
                name = tab.boardName,
                url = tab.boardUrl,
            ),
        )
    }

    /**
     * 現在の open tabs から対象タブ情報を取得する。
     */
    private fun findTab(tabKey: String): BoardTabInfo? {
        return tabSessionStore.openBoardTabs.value.find { tab -> tab.boardUrl == tabKey }
    }
}
