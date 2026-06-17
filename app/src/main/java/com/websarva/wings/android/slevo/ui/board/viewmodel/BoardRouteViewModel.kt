package com.websarva.wings.android.slevo.ui.board.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.common.bookmark.BoardTarget
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

/**
 * 板画面 route 単位でタブ表示状態を提供する ViewModel。
 *
 * 1つの route ViewModel が tab key ごとの `BoardUiState` を遅延生成し、
 * 選択中タブ変更では同一 ViewModel のまま表示状態だけを切り替える。
 */
@HiltViewModel
class BoardRouteViewModel @Inject constructor(
    private val tabSessionStore: TabSessionStore,
    private val boardViewModelFactory: BoardViewModelFactory,
) : ViewModel() {

    private companion object {
        const val UI_STATE_STOP_TIMEOUT_MILLIS = 5_000L
    }

    private val viewModelCache = mutableMapOf<String, BoardViewModel>()
    private val uiStateCache = mutableMapOf<String, StateFlow<BoardUiState>>()
    private val postSuccessCollectJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch {
            tabSessionStore.openBoardTabs.collect { tabs ->
                evictClosedTabs(tabs.map { tab -> tab.boardUrl }.toSet())
            }
        }
        viewModelScope.launch {
            tabSessionStore.openBoardTabs.collect { tabs ->
                attachPostSuccessCollectors(tabs)
            }
        }
    }

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
     * 指定板タブのブックマークシート holder を返す。
     */
    fun bookmarkSheetHolderFor(tabKey: String): BookmarkBottomSheetStateHolder {
        return tabSessionStore.boardBookmarkSheetHolder(tabKey)
    }

    /**
     * 指定板タブのブックマークシートを開く。
     */
    fun openBookmarkSheet(tabKey: String) {
        val state = boardViewModelFor(tabKey).uiState.value
        val boardInfo = state.boardInfo
        if (boardInfo.url.isBlank()) {
            // URLが空の場合はシートを開かない。
            return
        }
        val targets = listOf(
            BoardTarget(
                boardInfo = boardInfo,
                currentGroupId = state.bookmarkStatusState.selectedGroup?.id,
            )
        )
        tabSessionStore.boardBookmarkSheetHolder(tabKey).open(targets)
    }

    /**
     * 指定板タブの投稿ダイアログコントローラを返す。
     */
    fun postDialogActionsFor(tabKey: String): PostDialogController {
        return tabSessionStore.boardPostDialogController(tabKey)
    }

    /**
     * 指定板タブの投稿ダイアログに画像をアップロードする。
     */
    fun uploadPostDialogImage(tabKey: String, context: Context, uri: Uri) {
        tabSessionStore.boardUploadPostDialogImage(tabKey, context, uri)
    }

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
            boardViewModelFactory.create(tabKey).also { viewModel ->
                initializeBoardViewModel(viewModel, findTab(tabKey))
            }
        }
    }

    /**
     * タブ key ごとの共有 `UiState` Flow を組み立てる。
     */
    private fun createUiStateFlow(tabKey: String): StateFlow<BoardUiState> {
        val viewModel = boardViewModelFor(tabKey)
        preparePostDialogIdentityHistory(tabKey, viewModel)
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
     * boardId が確定したタイミングで、新しい holder 側の投稿ダイアログに履歴監視を準備する。
     */
    private fun preparePostDialogIdentityHistory(tabKey: String, viewModel: BoardViewModel) {
        viewModelScope.launch {
            viewModel.uiState
                .map { it.boardInfo.boardId }
                .distinctUntilChanged()
                .filter { it != 0L }
                .take(1)
                .collect { boardId ->
                    tabSessionStore.boardPostDialogController(tabKey).prepareIdentityHistory(boardId)
                }
        }
    }

    /**
     * 開いているタブ全ての投稿成功イベントを監視し、対象板の一覧を更新する。
     */
    private fun attachPostSuccessCollectors(tabs: List<BoardTabInfo>) {
        val currentKeys = tabs.map { it.boardUrl }.toSet()
        postSuccessCollectJobs.keys.filterNot { it in currentKeys }.forEach { key ->
            postSuccessCollectJobs.remove(key)?.cancel()
        }
        tabs.forEach { tab ->
            if (postSuccessCollectJobs.containsKey(tab.boardUrl)) {
                return@forEach
            }
            postSuccessCollectJobs[tab.boardUrl] = viewModelScope.launch {
                tabSessionStore.boardPostDialogController(tab.boardUrl)
                    .postSuccessEvents
                    .collect {
                        refreshBoard(tab.boardUrl)
                    }
            }
        }
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

    /**
     * 開いているタブ一覧から外れたキャッシュだけを解放する。
     */
    private fun evictClosedTabs(openKeys: Set<String>) {
        val removedKeys = viewModelCache.keys.filterNot(openKeys::contains)
        removedKeys.forEach { key ->
            viewModelCache.remove(key)?.disposeResources()
            uiStateCache.remove(key)
        }
    }

    /**
     * route ViewModel 終了時に内部キャッシュの旧 ViewModel を解放する。
     */
    override fun onCleared() {
        viewModelCache.values.forEach { it.disposeResources() }
        viewModelCache.clear()
        uiStateCache.clear()
        super.onCleared()
    }
}
