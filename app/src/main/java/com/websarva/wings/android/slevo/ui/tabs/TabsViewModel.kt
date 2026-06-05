package com.websarva.wings.android.slevo.ui.tabs

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

    // --- UI state (URL validation only; retained for BbsRouteScaffold compatibility) ---

    private val urlValidationState = MutableStateFlow(false)

    val uiState: StateFlow<TabsUiState> = combine(
        openBoardTabs, boardLoaded, openThreadTabs, threadLoaded,
        isRefreshing, refreshProgress, newResCounts, urlValidationState,
    ) { array: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        TabsUiState(
            openBoardTabs = array[0] as List<BoardTabInfo>,
            boardLoaded = array[1] as Boolean,
            openThreadTabs = array[2] as List<ThreadTabInfo>,
            threadLoaded = array[3] as Boolean,
            isRefreshing = array[4] as Boolean,
            refreshProgress = array[5] as ThreadTabRefreshProgress?,
            newResCounts = array[6] as Map<String, Int>,
            isUrlValidating = array[7] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TabsUiState())

    /** セッション状態を含む [TabsUiState]（[uiState] と同等）。 */
    val sessionUiState: StateFlow<TabsUiState> = uiState

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

    // --- URL validation (retained for BbsRouteScaffold compatibility) ---

    fun startUrlValidation() { urlValidationState.value = true }
    fun finishUrlValidation() { urlValidationState.value = false }

    // --- Lifecycle ---

    override fun onCleared() {
        super.onCleared()
        tabSessionStore.releaseAllViewModels()
    }

}
