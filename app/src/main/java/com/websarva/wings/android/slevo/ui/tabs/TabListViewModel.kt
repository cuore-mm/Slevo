package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.util.resolveUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * タブ一覧画面専用の UI 状態と操作を管理する ViewModel。
 *
 * 検索モード、長押し選択、詳細 BottomSheet、URL入力ダイアログなど、
 * タブ一覧画面固有の一時 UI 状態を画面ライフサイクルに紐付けて管理する。
 * タブセッション状態は [TabSessionStore] を正本として参照し、
 * セッション操作は [TabSessionStore] へ委譲する。
 */
@HiltViewModel
class TabListViewModel @Inject constructor(
    val tabSessionStore: TabSessionStore,
) : ViewModel() {

    private val isSearchModeState = MutableStateFlow(false)
    private val searchQueryState = MutableStateFlow("")
    private val scrollSnapshotState = MutableStateFlow<TabSearchScrollSnapshot?>(null)
    private val scrollCommandState = MutableStateFlow<TabListScrollCommand?>(null)
    private val previousSearchQueryState = MutableStateFlow("")
    private val tabSelectionState = MutableStateFlow(TabSelectionState())
    private val detailBoardTabState = MutableStateFlow<BoardTabInfo?>(null)
    private val detailThreadTabState = MutableStateFlow<ThreadTabInfo?>(null)
    private val showBoardInfoBottomSheetState = MutableStateFlow(false)
    private val showThreadInfoBottomSheetState = MutableStateFlow(false)
    private val pendingCloseBoardTabState = MutableStateFlow<BoardTabInfo?>(null)
    private val pendingCloseThreadTabState = MutableStateFlow<ThreadTabInfo?>(null)
    private val urlValidationState = MutableStateFlow(false)
    private val urlDialogState = MutableStateFlow(false)
    private val urlErrorState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TabListUiState> = combine(
        isSearchModeState,
        searchQueryState,
        scrollCommandState,
        tabSelectionState,
        pendingCloseBoardTabState,
        pendingCloseThreadTabState,
        detailBoardTabState,
        detailThreadTabState,
        showBoardInfoBottomSheetState,
        showThreadInfoBottomSheetState,
        urlValidationState,
        urlDialogState,
        urlErrorState,
    ) { array ->
        TabListUiState(
            isSearchMode = array[0] as Boolean,
            searchQuery = array[1] as String,
            scrollCommand = array[2] as TabListScrollCommand?,
            selectedBoardTab = (array[3] as TabSelectionState).selectedBoardTab,
            selectedThreadTab = (array[3] as TabSelectionState).selectedThreadTab,
            selectedTabBounds = (array[3] as TabSelectionState).selectedTabBounds,
            pendingCloseBoardTab = array[4] as BoardTabInfo?,
            pendingCloseThreadTab = array[5] as ThreadTabInfo?,
            detailBoardTab = array[6] as BoardTabInfo?,
            detailThreadTab = array[7] as ThreadTabInfo?,
            showBoardInfoBottomSheet = array[8] as Boolean,
            showThreadInfoBottomSheet = array[9] as Boolean,
            isUrlValidating = array[10] as Boolean,
            showUrlDialog = array[11] as Boolean,
            urlErrorMessage = array[12] as String?,
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, TabListUiState())

    // --- Search ---

    fun enterSearchMode() {
        cancelTabSelection()
        isSearchModeState.value = true
    }

    fun closeSearchMode() {
        val oldQuery = searchQueryState.value
        // 検索結果表示中（非空クエリ）→ 検索解除の場合、復元命令を発行する
        if (oldQuery.isNotBlank()) {
            scrollSnapshotState.value?.let { snapshot ->
                scrollCommandState.value = TabListScrollCommand.Restore(snapshot)
            }
        }
        isSearchModeState.value = false
        searchQueryState.value = ""
        previousSearchQueryState.value = ""
    }

    fun updateSearchQuery(query: String) {
        val oldQuery = searchQueryState.value
        val newQuery = query

        when {
            // 空 → 非空: 検索開始。スクロール位置を保存してからクエリを更新
            oldQuery.isBlank() && newQuery.isNotBlank() -> {
                // スクロール位置は UI 側から saveScrollSnapshotBeforeSearch で保存される
                searchQueryState.value = newQuery
                previousSearchQueryState.value = newQuery
            }

            // 非空 → 別の非空: クエリ変更。現在表示中ページの検索結果を先頭表示
            oldQuery.isNotBlank() && newQuery.isNotBlank() && oldQuery != newQuery -> {
                searchQueryState.value = newQuery
                previousSearchQueryState.value = newQuery
                // スクロール命令は UI 側で現在ページを判定して発行する
            }

            // 非空 → 空: 検索解除。closeSearchMode と同様に復元命令を発行
            oldQuery.isNotBlank() && newQuery.isBlank() -> {
                scrollSnapshotState.value?.let { snapshot ->
                    scrollCommandState.value = TabListScrollCommand.Restore(snapshot)
                }
                searchQueryState.value = ""
                previousSearchQueryState.value = ""
            }

            // その他（空→空、同じ非空）: 単純に更新
            else -> {
                searchQueryState.value = newQuery
                previousSearchQueryState.value = newQuery
            }
        }
    }

    /**
     * 検索開始前のスクロール位置を保存する。
     *
     * 検索クエリが空から非空へ変わる直前に、UI 側から呼び出す。
     */
    fun saveScrollSnapshotBeforeSearch(snapshot: TabSearchScrollSnapshot) {
        scrollSnapshotState.value = snapshot
    }

    /**
     * 検索クエリ変更時に、指定ページの検索結果を先頭表示する命令を発行する。
     *
     * @param page 0: 板一覧, 1: スレッド一覧
     */
    fun issueScrollToTopCommand(page: Int) {
        scrollCommandState.value = TabListScrollCommand.ScrollToTop(page)
    }

    /**
     * スクロール命令を消費する。
     *
     * UI 側が命令を実行した後に呼び出し、同じ命令の再実行を防ぐ。
     */
    fun consumeScrollCommand() {
        scrollCommandState.value = null
    }

    /**
     * 検索状態を完全に破棄する。
     *
     * 検索モード、検索クエリ、復元スナップショット、未消費スクロール命令をすべてクリアする。
     * BottomSheet dismiss 時など、表示コンテキストを終了するときに使用する。
     */
    fun resetSearchState() {
        isSearchModeState.value = false
        searchQueryState.value = ""
        scrollSnapshotState.value = null
        scrollCommandState.value = null
        previousSearchQueryState.value = ""
    }

    // --- Long-press selection ---

    fun onBoardTabLongPressed(tab: BoardTabInfo, bounds: IntRect) {
        cancelTabSelection()
        tabSelectionState.value = TabSelectionState(
            selectedBoardTab = tab,
            selectedTabBounds = bounds,
        )
    }

    fun onThreadTabLongPressed(tab: ThreadTabInfo, bounds: IntRect) {
        cancelTabSelection()
        tabSelectionState.value = TabSelectionState(
            selectedThreadTab = tab,
            selectedTabBounds = bounds,
        )
    }

    fun cancelTabSelection() {
        tabSelectionState.value = TabSelectionState()
        showBoardInfoBottomSheetState.value = false
        showThreadInfoBottomSheetState.value = false
    }

    fun toggleSelectedTabPin() {
        tabSelectionState.value.selectedBoardTab?.let { tab ->
            tabSessionStore.togglePinBoardTab(tab.boardUrl)
        }
        tabSelectionState.value.selectedThreadTab?.let { tab ->
            tabSessionStore.togglePinThreadTab(tab.id)
        }
        cancelTabSelection()
    }

    fun openSelectedTabDetail() {
        tabSelectionState.value.selectedBoardTab?.let {
            detailBoardTabState.value = it
            showBoardInfoBottomSheetState.value = true
        }
        tabSelectionState.value.selectedThreadTab?.let {
            detailThreadTabState.value = it
            showThreadInfoBottomSheetState.value = true
        }
        tabSelectionState.value = TabSelectionState()
    }

    fun requestCloseSelectedTab() {
        tabSelectionState.value.selectedBoardTab?.let { tab ->
            pendingCloseBoardTabState.value = tab
        }
        tabSelectionState.value.selectedThreadTab?.let { tab ->
            pendingCloseThreadTabState.value = tab
        }
        cancelTabSelection()
    }

    fun consumePendingCloseRequest() {
        pendingCloseBoardTabState.value = null
        pendingCloseThreadTabState.value = null
    }

    fun onPageChanged() {
        cancelTabSelection()
    }

    // --- BottomSheet ---

    fun dismissBoardInfoBottomSheet() {
        showBoardInfoBottomSheetState.value = false
    }

    fun dismissThreadInfoBottomSheet() {
        showThreadInfoBottomSheetState.value = false
    }

    // --- URL Dialog ---

    fun startUrlValidation() {
        urlValidationState.value = true
    }

    fun finishUrlValidation() {
        urlValidationState.value = false
    }

    fun setUrlDialogVisible(visible: Boolean) {
        urlDialogState.value = visible
        if (!visible) {
            urlErrorState.value = null
        }
    }

    fun setUrlErrorMessage(message: String?) {
        urlErrorState.value = message
    }

    /**
     * URL入力文字列を解決し、板またはスレッドの遷移先を決定する。
     *
     * 解決に失敗した場合はエラーメッセージを設定し、[UrlOpenResult.Error] を返す。
     */
    suspend fun openUrlInput(url: String, invalidUrlMessage: String): UrlOpenResult {
        startUrlValidation()
        return try {
            when (val resolved = resolveUrl(url)) {
                is com.websarva.wings.android.slevo.ui.util.ResolvedUrl.ItestBoard -> {
                    val host = tabSessionStore.resolveBoardHost(resolved.boardKey, resolved.rawUrl)
                    if (host != null) {
                        val boardUrl = "https://$host/${resolved.boardKey}/"
                        val route = tabSessionStore.normalizeBoardRouteForNavigation(
                            AppRoute.Board(boardName = boardUrl, boardUrl = boardUrl)
                        )
                        setUrlErrorMessage(null)
                        setUrlDialogVisible(false)
                        UrlOpenResult.NavigateBoard(route)
                    } else {
                        UrlOpenResult.Error(invalidUrlMessage)
                    }
                }
                is com.websarva.wings.android.slevo.ui.util.ResolvedUrl.Thread -> {
                    val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                    val route = tabSessionStore.normalizeThreadRouteForNavigation(
                        AppRoute.Thread(
                            threadKey = resolved.threadKey,
                            boardUrl = boardUrl,
                            boardName = resolved.boardKey,
                            threadTitle = null,
                        )
                    )
                    setUrlErrorMessage(null)
                    setUrlDialogVisible(false)
                    UrlOpenResult.NavigateThread(route)
                }
                is com.websarva.wings.android.slevo.ui.util.ResolvedUrl.Board -> {
                    val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                    val route = tabSessionStore.normalizeBoardRouteForNavigation(
                        AppRoute.Board(boardName = boardUrl, boardUrl = boardUrl)
                    )
                    setUrlErrorMessage(null)
                    setUrlDialogVisible(false)
                    UrlOpenResult.NavigateBoard(route)
                }
                else -> UrlOpenResult.Error(invalidUrlMessage)
            }
        } finally {
            finishUrlValidation()
        }
    }

    // --- Internal ---

    private data class TabSelectionState(
        val selectedBoardTab: BoardTabInfo? = null,
        val selectedThreadTab: ThreadTabInfo? = null,
        val selectedTabBounds: IntRect? = null,
    )
}
