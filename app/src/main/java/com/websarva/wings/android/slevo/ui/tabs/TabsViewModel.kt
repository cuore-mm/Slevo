package com.websarva.wings.android.slevo.ui.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.BbsServiceRepository
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.ui.board.viewmodel.BoardViewModel
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TabsViewModel @Inject constructor(
    private val tabsRepository: TabsRepository,
    private val boardRepository: BoardRepository,
    private val bbsServiceRepository: BbsServiceRepository,
    private val boardTabsCoordinator: BoardTabsCoordinator,
    private val threadTabsCoordinator: ThreadTabsCoordinator,
    private val tabViewModelRegistry: TabViewModelRegistry,
) : ViewModel() {

    private val boardTabsState = combine(
        boardTabsCoordinator.openBoardTabs,
        boardTabsCoordinator.boardLoaded,
    ) { openBoardTabs, boardLoaded ->
        BoardTabsState(openBoardTabs, boardLoaded)
    }

    private val threadTabsState = combine(
        threadTabsCoordinator.openThreadTabs,
        threadTabsCoordinator.threadLoaded,
        threadTabsCoordinator.isRefreshing,
        threadTabsCoordinator.newResCounts,
    ) { openThreadTabs, threadLoaded, isRefreshing, newResCounts ->
        ThreadTabsState(openThreadTabs, threadLoaded, isRefreshing, newResCounts)
    }

    private val urlValidationState = MutableStateFlow(false)

    val uiState: StateFlow<TabsUiState> = combine(
        boardTabsState,
        threadTabsState,
        urlValidationState,
    ) { boardState, threadState, isUrlValidating ->
        TabsUiState(
            openThreadTabs = threadState.openThreadTabs,
            openBoardTabs = boardState.openBoardTabs,
            boardLoaded = boardState.boardLoaded,
            threadLoaded = threadState.threadLoaded,
            isRefreshing = threadState.isRefreshing,
            newResCounts = threadState.newResCounts,
            isUrlValidating = isUrlValidating,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TabsUiState())

    val boardCurrentPage: StateFlow<Int> = boardTabsCoordinator.boardCurrentPage
    val threadCurrentPage: StateFlow<Int> = threadTabsCoordinator.threadCurrentPage
    val boardPageAnimation: SharedFlow<Int> = boardTabsCoordinator.boardPageAnimation
    val threadPageAnimation: SharedFlow<Int> = threadTabsCoordinator.threadPageAnimation

    val lastSelectedPage = tabsRepository.observeLastSelectedPage()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        boardTabsCoordinator.bind(viewModelScope)
        threadTabsCoordinator.bind(viewModelScope)
    }

    fun getOrCreateThreadViewModel(viewModelKey: String): ThreadViewModel {
        return tabViewModelRegistry.getOrCreateThreadViewModel(viewModelKey)
    }

    fun getOrCreateBoardViewModel(boardUrl: String): BoardViewModel {
        return tabViewModelRegistry.getOrCreateBoardViewModel(boardUrl)
    }

    fun setLastSelectedPage(page: Int) {
        viewModelScope.launch { tabsRepository.setLastSelectedPage(page) }
    }

    fun ensureBoardTab(route: AppRoute.Board): Int {
        return boardTabsCoordinator.ensureBoardTab(route)
    }

    fun openBoardTab(boardTabInfo: BoardTabInfo) {
        boardTabsCoordinator.openBoardTab(boardTabInfo)
    }

    fun closeBoardTab(tab: BoardTabInfo) {
        boardTabsCoordinator.closeBoardTab(tab)
    }

    fun closeBoardTabByUrl(boardUrl: String) {
        boardTabsCoordinator.closeBoardTabByUrl(boardUrl)
    }

    fun updateBoardScrollPosition(
        boardUrl: String,
        firstVisibleIndex: Int,
        scrollOffset: Int,
    ) {
        boardTabsCoordinator.updateBoardScrollPosition(boardUrl, firstVisibleIndex, scrollOffset)
    }

    fun setBoardCurrentPage(page: Int) {
        boardTabsCoordinator.setBoardCurrentPage(page)
    }

    fun moveBoardPage(offset: Int) {
        boardTabsCoordinator.moveBoardPage(offset)
    }

    fun animateBoardPage(offset: Int) {
        boardTabsCoordinator.animateBoardPage(offset)
    }

    fun ensureThreadTab(route: AppRoute.Thread): Int {
        return threadTabsCoordinator.ensureThreadTab(route)
    }

    fun closeThreadTab(tab: ThreadTabInfo) {
        threadTabsCoordinator.closeThreadTab(tab)
    }

    fun closeThreadTab(threadKey: String, boardUrl: String) {
        threadTabsCoordinator.closeThreadTab(threadKey, boardUrl)
    }

    fun setThreadCurrentPage(page: Int) {
        threadTabsCoordinator.setThreadCurrentPage(page)
    }

    fun moveThreadPage(offset: Int) {
        threadTabsCoordinator.moveThreadPage(offset)
    }

    fun animateThreadPage(offset: Int) {
        threadTabsCoordinator.animateThreadPage(offset)
    }

    fun clearNewResCount(threadId: ThreadId) {
        threadTabsCoordinator.clearNewResCount(threadId)
    }

    fun refreshOpenThreads() {
        threadTabsCoordinator.refreshOpenThreads()
    }

    fun startUrlValidation() {
        urlValidationState.value = true
    }

    fun finishUrlValidation() {
        urlValidationState.value = false
    }

    /**
     * boardKey からホストを解決する。
     * DBに無い場合は bbsmenu を参照して補完する。
     */
    suspend fun resolveBoardHost(boardKey: String): String? {
        return boardRepository.resolveHostByBoardKey(boardKey)
            ?: bbsServiceRepository.resolveHostByBoardKeyFromMenu(boardKey)
    }

    fun getTabInfo(threadId: ThreadId): ThreadTabInfo? {
        return threadTabsCoordinator.getTabInfo(threadId)
    }

    suspend fun resolveBoardInfo(
        boardId: Long?,
        boardUrl: String,
        boardName: String,
    ): BoardInfo? {
        boardId?.takeIf { it != 0L }?.let { return BoardInfo(it, boardName, boardUrl) }

        boardRepository.findBoardByUrl(boardUrl)?.let { entity ->
            return BoardInfo(entity.boardId, entity.name, entity.url)
        }

        val name = boardRepository.fetchBoardName("${boardUrl}SETTING.TXT") ?: return null
        val id = boardRepository.ensureBoard(BoardInfo(0L, name, boardUrl))
        return BoardInfo(id, name, boardUrl)
    }

    override fun onCleared() {
        super.onCleared()
        tabViewModelRegistry.releaseAll()
    }
}

private data class BoardTabsState(
    val openBoardTabs: List<BoardTabInfo>,
    val boardLoaded: Boolean,
)

private data class ThreadTabsState(
    val openThreadTabs: List<ThreadTabInfo>,
    val threadLoaded: Boolean,
    val isRefreshing: Boolean,
    val newResCounts: Map<String, Int>,
)
