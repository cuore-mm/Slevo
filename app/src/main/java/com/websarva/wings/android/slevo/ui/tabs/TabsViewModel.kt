package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.ui.unit.IntRect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.board.viewmodel.BoardViewModel
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabRefreshProgress
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModel
import com.websarva.wings.android.slevo.ui.util.resolveUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * タブ一覧画面の UI 状態と操作を統合する ViewModel。
 *
 * セッション状態と操作は [TabSessionStore] へ委譲し、
 * 検索、長押し選択、詳細 BottomSheet、URL入力ダイアログなどの
 * 画面固有 UI 状態を保持する。
 */
@HiltViewModel
class TabsViewModel @Inject constructor(
    private val tabSessionStore: TabSessionStore,
) : ViewModel() {

    // --- Session state from TabSessionStore ---

    val openBoardTabs: StateFlow<List<BoardTabInfo>> = tabSessionStore.openBoardTabs
    val openThreadTabs: StateFlow<List<ThreadTabInfo>> = tabSessionStore.openThreadTabs
    val boardLoaded: StateFlow<Boolean> = tabSessionStore.boardLoaded
    val threadLoaded: StateFlow<Boolean> = tabSessionStore.threadLoaded
    val isRefreshing: StateFlow<Boolean> = tabSessionStore.isRefreshing
    val refreshProgress: StateFlow<ThreadTabRefreshProgress?> = tabSessionStore.refreshProgress
    val newResCounts: StateFlow<Map<String, Int>> = tabSessionStore.newResCounts

    val boardCurrentPage: StateFlow<Int> = tabSessionStore.boardCurrentPage
    val threadCurrentPage: StateFlow<Int> = tabSessionStore.threadCurrentPage
    val boardPageAnimation: SharedFlow<Int> = tabSessionStore.boardPageAnimation
    val threadPageAnimation: SharedFlow<Int> = tabSessionStore.threadPageAnimation

    val lastSelectedPage = tabSessionStore.lastSelectedPage
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // --- UI state (screen-specific) ---

    private val urlValidationState = MutableStateFlow(false)
    private val urlDialogState = MutableStateFlow(false)
    private val urlErrorState = MutableStateFlow<String?>(null)
    private val tabSelectionState = MutableStateFlow(TabSelectionState())
    private val detailBoardTabState = MutableStateFlow<BoardTabInfo?>(null)
    private val detailThreadTabState = MutableStateFlow<ThreadTabInfo?>(null)
    private val showBoardInfoBottomSheetState = MutableStateFlow(false)
    private val showThreadInfoBottomSheetState = MutableStateFlow(false)
    private val pendingCloseBoardTabState = MutableStateFlow<BoardTabInfo?>(null)
    private val pendingCloseThreadTabState = MutableStateFlow<ThreadTabInfo?>(null)
    private val isSearchModeState = MutableStateFlow(false)
    private val searchQueryState = MutableStateFlow("")

    private val urlDialogUiState = combine(
        urlValidationState, urlDialogState, urlErrorState,
    ) { isUrlValidating, showUrlDialog, urlErrorMessage ->
        UrlDialogState(isUrlValidating, showUrlDialog, urlErrorMessage)
    }

    private val visualUiState = combine(
        combine(detailBoardTabState, detailThreadTabState, showBoardInfoBottomSheetState, showThreadInfoBottomSheetState) { dBoard, dThread, sBoard, sThread ->
            TabDetailState(dBoard, dThread, sBoard, sThread)
        },
        combine(isSearchModeState, searchQueryState) { isSearchMode, searchQuery ->
            SearchUiState(isSearchMode, searchQuery)
        }
    ) { detail, search ->
        VisualUiState(detail, search)
    }

    private val boardTabsState = combine(openBoardTabs, boardLoaded) { tabs, loaded ->
        BoardTabsState(tabs, loaded)
    }

    private val threadTabsState = combine(
        openThreadTabs, threadLoaded, isRefreshing, refreshProgress, newResCounts,
    ) { tabs, loaded, refreshing, progress, counts ->
        ThreadTabsState(tabs, loaded, refreshing, progress, counts)
    }

    private val pendingCloseState = combine(
        pendingCloseBoardTabState, pendingCloseThreadTabState,
    ) { pBoard, pThread ->
        PendingCloseState(pBoard, pThread)
    }

    private val baseUiState = combine(
        boardTabsState, threadTabsState, urlDialogUiState, tabSelectionState, visualUiState,
    ) { boardState, threadState, urlState, selectionState, visualState ->
        TabsUiState(
            openThreadTabs = threadState.openThreadTabs,
            openBoardTabs = boardState.openBoardTabs,
            boardLoaded = boardState.boardLoaded,
            threadLoaded = threadState.threadLoaded,
            isRefreshing = threadState.isRefreshing,
            refreshProgress = threadState.refreshProgress,
            newResCounts = threadState.newResCounts,
            isUrlValidating = urlState.isUrlValidating,
            showUrlDialog = urlState.showUrlDialog,
            urlErrorMessage = urlState.urlErrorMessage,
            isSearchMode = visualState.searchState.isSearchMode,
            searchQuery = visualState.searchState.searchQuery,
            selectedBoardTab = selectionState.selectedBoardTab,
            selectedThreadTab = selectionState.selectedThreadTab,
            selectedTabBounds = selectionState.selectedTabBounds,
            detailBoardTab = visualState.detailState.detailBoardTab,
            detailThreadTab = visualState.detailState.detailThreadTab,
            showBoardInfoBottomSheet = visualState.detailState.showBoardInfoBottomSheet,
            showThreadInfoBottomSheet = visualState.detailState.showThreadInfoBottomSheet,
        )
    }

    val uiState: StateFlow<TabsUiState> = combine(
        baseUiState, pendingCloseState,
    ) { base, pending ->
        base.copy(
            pendingCloseBoardTab = pending.pendingBoardTab,
            pendingCloseThreadTab = pending.pendingThreadTab,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TabsUiState())

    // --- Child ViewModel access ---

    fun getOrCreateThreadViewModel(viewModelKey: String): ThreadViewModel {
        return tabSessionStore.getOrCreateThreadViewModel(viewModelKey)
    }

    fun getOrCreateBoardViewModel(boardUrl: String): BoardViewModel {
        return tabSessionStore.getOrCreateBoardViewModel(boardUrl)
    }

    // --- Session operations (delegate to TabSessionStore) ---

    fun ensureBoardTab(route: AppRoute.Board): Int = tabSessionStore.ensureBoardTab(route)
    fun closeBoardTab(tab: BoardTabInfo) = tabSessionStore.closeBoardTab(tab)
    fun closeBoardTabByUrl(boardUrl: String) = tabSessionStore.closeBoardTabByUrl(boardUrl)
    fun updateBoardScrollPosition(boardUrl: String, firstVisibleIndex: Int, scrollOffset: Int) =
        tabSessionStore.updateBoardScrollPosition(boardUrl, firstVisibleIndex, scrollOffset)
    fun setBoardCurrentPage(page: Int) = tabSessionStore.setBoardCurrentPage(page)
    fun animateBoardPage(offset: Int) = tabSessionStore.animateBoardPage(offset)
    fun ensureThreadTab(route: AppRoute.Thread): Int = tabSessionStore.ensureThreadTab(route)
    fun closeThreadTab(tab: ThreadTabInfo) = tabSessionStore.closeThreadTab(tab)
    fun closeThreadTab(threadKey: String, boardUrl: String) = tabSessionStore.closeThreadTab(threadKey, boardUrl)
    fun setThreadCurrentPage(page: Int) = tabSessionStore.setThreadCurrentPage(page)
    fun animateThreadPage(offset: Int) = tabSessionStore.animateThreadPage(offset)
    fun clearNewResCount(threadId: ThreadId) = tabSessionStore.clearNewResCount(threadId)
    fun refreshOpenThreads() = tabSessionStore.refreshOpenThreads()
    fun cancelRefreshOpenThreads() = tabSessionStore.cancelRefreshOpenThreads()
    fun setLastSelectedPage(page: Int) = viewModelScope.launch { tabSessionStore.setLastSelectedPage(page) }

    suspend fun normalizeBoardRouteForNavigation(route: AppRoute.Board): AppRoute.Board =
        tabSessionStore.normalizeBoardRouteForNavigation(route)
    suspend fun normalizeThreadRouteForNavigation(route: AppRoute.Thread): AppRoute.Thread =
        tabSessionStore.normalizeThreadRouteForNavigation(route)
    suspend fun resolveBoardHost(boardKey: String, sourceUrl: String? = null): String? =
        tabSessionStore.resolveBoardHost(boardKey, sourceUrl)
    suspend fun resolveBoardInfo(boardId: Long?, boardUrl: String, boardName: String): BoardInfo? =
        tabSessionStore.resolveBoardInfo(boardId, boardUrl, boardName)

    // --- Long-press selection ---

    fun onBoardTabLongPressed(tab: BoardTabInfo, bounds: IntRect) {
        cancelTabSelection()
        tabSelectionState.value = TabSelectionState(selectedBoardTab = tab, selectedTabBounds = bounds)
    }

    fun onThreadTabLongPressed(tab: ThreadTabInfo, bounds: IntRect) {
        cancelTabSelection()
        tabSelectionState.value = TabSelectionState(selectedThreadTab = tab, selectedTabBounds = bounds)
    }

    fun cancelTabSelection() {
        tabSelectionState.value = TabSelectionState()
        showBoardInfoBottomSheetState.value = false
        showThreadInfoBottomSheetState.value = false
    }

    fun toggleSelectedTabPin() {
        tabSelectionState.value.selectedBoardTab?.let { tabSessionStore.togglePinBoardTab(it.boardUrl) }
        tabSelectionState.value.selectedThreadTab?.let { tabSessionStore.togglePinThreadTab(it.id) }
        cancelTabSelection()
    }

    fun openSelectedTabDetail() {
        tabSelectionState.value.selectedBoardTab?.let { detailBoardTabState.value = it; showBoardInfoBottomSheetState.value = true }
        tabSelectionState.value.selectedThreadTab?.let { detailThreadTabState.value = it; showThreadInfoBottomSheetState.value = true }
        tabSelectionState.value = TabSelectionState()
    }

    fun requestCloseSelectedTab() {
        tabSelectionState.value.selectedBoardTab?.let { pendingCloseBoardTabState.value = it }
        tabSelectionState.value.selectedThreadTab?.let { pendingCloseThreadTabState.value = it }
        cancelTabSelection()
    }

    fun consumePendingCloseRequest() {
        pendingCloseBoardTabState.value = null
        pendingCloseThreadTabState.value = null
    }

    fun onPageChanged() { cancelTabSelection() }

    // --- Search ---

    fun enterSearchMode() { cancelTabSelection(); isSearchModeState.value = true }
    fun closeSearchMode() { isSearchModeState.value = false; searchQueryState.value = "" }
    fun updateSearchQuery(query: String) { searchQueryState.value = query }

    // --- BottomSheet ---

    fun dismissBoardInfoBottomSheet() { showBoardInfoBottomSheetState.value = false }
    fun dismissThreadInfoBottomSheet() { showThreadInfoBottomSheetState.value = false }

    // --- URL Dialog ---

    fun startUrlValidation() { urlValidationState.value = true }
    fun finishUrlValidation() { urlValidationState.value = false }
    fun setUrlDialogVisible(visible: Boolean) {
        urlDialogState.value = visible
        if (!visible) urlErrorState.value = null
    }
    fun setUrlErrorMessage(message: String?) { urlErrorState.value = message }

    // --- URL Open ---

    sealed class UrlOpenResult {
        data class NavigateBoard(val route: AppRoute.Board) : UrlOpenResult()
        data class NavigateThread(val route: AppRoute.Thread) : UrlOpenResult()
        data class Error(val message: String?) : UrlOpenResult()
    }

    suspend fun openUrlInput(url: String, invalidUrlMessage: String): UrlOpenResult {
        startUrlValidation()
        return try {
            when (val resolved = resolveUrl(url)) {
                is com.websarva.wings.android.slevo.ui.util.ResolvedUrl.ItestBoard -> {
                    val host = resolveBoardHost(resolved.boardKey, resolved.rawUrl)
                    if (host != null) {
                        val boardUrl = "https://$host/${resolved.boardKey}/"
                        val route = normalizeBoardRouteForNavigation(AppRoute.Board(boardName = boardUrl, boardUrl = boardUrl))
                        setUrlErrorMessage(null); setUrlDialogVisible(false)
                        UrlOpenResult.NavigateBoard(route)
                    } else {
                        UrlOpenResult.Error(invalidUrlMessage)
                    }
                }
                is com.websarva.wings.android.slevo.ui.util.ResolvedUrl.Thread -> {
                    val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                    val route = normalizeThreadRouteForNavigation(AppRoute.Thread(threadKey = resolved.threadKey, boardUrl = boardUrl, boardName = resolved.boardKey, threadTitle = null))
                    setUrlErrorMessage(null); setUrlDialogVisible(false)
                    UrlOpenResult.NavigateThread(route)
                }
                is com.websarva.wings.android.slevo.ui.util.ResolvedUrl.Board -> {
                    val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                    val route = normalizeBoardRouteForNavigation(AppRoute.Board(boardName = boardUrl, boardUrl = boardUrl))
                    setUrlErrorMessage(null); setUrlDialogVisible(false)
                    UrlOpenResult.NavigateBoard(route)
                }
                else -> UrlOpenResult.Error(invalidUrlMessage)
            }
        } finally {
            finishUrlValidation()
        }
    }

    // --- Lifecycle ---

    override fun onCleared() {
        super.onCleared()
        tabSessionStore.releaseAllViewModels()
    }

    // --- Internal state classes ---

    private data class BoardTabsState(val openBoardTabs: List<BoardTabInfo>, val boardLoaded: Boolean)
    private data class ThreadTabsState(val openThreadTabs: List<ThreadTabInfo>, val threadLoaded: Boolean, val isRefreshing: Boolean, val refreshProgress: ThreadTabRefreshProgress?, val newResCounts: Map<String, Int>)
    private data class UrlDialogState(val isUrlValidating: Boolean = false, val showUrlDialog: Boolean = false, val urlErrorMessage: String? = null)
    private data class TabSelectionState(val selectedBoardTab: BoardTabInfo? = null, val selectedThreadTab: ThreadTabInfo? = null, val selectedTabBounds: IntRect? = null)
    private data class TabDetailState(val detailBoardTab: BoardTabInfo? = null, val detailThreadTab: ThreadTabInfo? = null, val showBoardInfoBottomSheet: Boolean = false, val showThreadInfoBottomSheet: Boolean = false)
    private data class SearchUiState(val isSearchMode: Boolean = false, val searchQuery: String = "")
    private data class VisualUiState(val detailState: TabDetailState, val searchState: SearchUiState)
    private data class PendingCloseState(val pendingBoardTab: BoardTabInfo? = null, val pendingThreadTab: ThreadTabInfo? = null)
}
