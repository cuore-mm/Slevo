package com.websarva.wings.android.slevo.ui.tabs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.BbsServiceRepository
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.ui.board.viewmodel.BoardViewModel
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModel
import com.websarva.wings.android.slevo.ui.util.BoardUrlNormalizationInput
import com.websarva.wings.android.slevo.ui.util.normalizeBoardUrlTo5chIo
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
 * 板/スレッドタブの状態を集約し、更新進捗や URL 入力ダイアログの状態も管理する。
 */
@HiltViewModel
class TabsViewModel @Inject constructor(
    private val tabsRepository: TabsRepository,
    private val boardRepository: BoardRepository,
    private val bbsServiceRepository: BbsServiceRepository,
    private val boardTabsCoordinator: BoardTabsCoordinator,
    private val threadTabsCoordinator: ThreadTabsCoordinator,
    private val tabViewModelRegistry: TabViewModelRegistry,
    private val settingsRepository: SettingsRepository,
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
        threadTabsCoordinator.refreshProgress,
        threadTabsCoordinator.newResCounts,
    ) { openThreadTabs, threadLoaded, isRefreshing, refreshProgress, newResCounts ->
        ThreadTabsState(
            openThreadTabs = openThreadTabs,
            threadLoaded = threadLoaded,
            isRefreshing = isRefreshing,
            refreshProgress = refreshProgress,
            newResCounts = newResCounts,
        )
    }

    private val urlValidationState = MutableStateFlow(false)
    private val urlDialogState = MutableStateFlow(false)
    private val urlErrorState = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TabsUiState> = combine(
        boardTabsState,
        threadTabsState,
        urlValidationState,
        urlDialogState,
        urlErrorState,
    ) { boardState, threadState, isUrlValidating, showUrlDialog, urlErrorMessage ->
        TabsUiState(
            openThreadTabs = threadState.openThreadTabs,
            openBoardTabs = boardState.openBoardTabs,
            boardLoaded = boardState.boardLoaded,
            threadLoaded = threadState.threadLoaded,
            isRefreshing = threadState.isRefreshing,
            refreshProgress = threadState.refreshProgress,
            newResCounts = threadState.newResCounts,
            isUrlValidating = isUrlValidating,
            showUrlDialog = showUrlDialog,
            urlErrorMessage = urlErrorMessage,
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

    /**
     * 板routeを永続化済み設定値に従って正規化する。
     */
    suspend fun normalizeBoardRouteForNavigation(route: AppRoute.Board): AppRoute.Board {
        val isEnabled = settingsRepository.getIsRedirect5chNetToIoEnabled()
        val normalizedUrl = normalizeBoardUrlTo5chIo(
            BoardUrlNormalizationInput(
                boardUrl = route.boardUrl,
                isEnabled = isEnabled,
            )
        )
        if (normalizedUrl == route.boardUrl) return route
        return route.copy(boardUrl = normalizedUrl)
    }

    /**
     * スレrouteを永続化済み設定値に従って正規化する。
     */
    suspend fun normalizeThreadRouteForNavigation(route: AppRoute.Thread): AppRoute.Thread {
        val isEnabled = settingsRepository.getIsRedirect5chNetToIoEnabled()
        val normalizedUrl = normalizeBoardUrlTo5chIo(
            BoardUrlNormalizationInput(
                boardUrl = route.boardUrl,
                isEnabled = isEnabled,
            )
        )
        if (normalizedUrl == route.boardUrl) return route
        return route.copy(boardUrl = normalizedUrl)
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

    fun animateThreadPage(offset: Int) {
        threadTabsCoordinator.animateThreadPage(offset)
    }

    fun clearNewResCount(threadId: ThreadId) {
        threadTabsCoordinator.clearNewResCount(threadId)
    }

    fun refreshOpenThreads() {
        threadTabsCoordinator.refreshOpenThreads()
    }

    /**
     * スレッドタブ一覧の更新処理をキャンセルする。
     */
    fun cancelRefreshOpenThreads() {
        threadTabsCoordinator.cancelRefreshOpenThreads()
    }

    fun startUrlValidation() {
        urlValidationState.value = true
    }

    fun finishUrlValidation() {
        urlValidationState.value = false
    }

    /**
     * URL入力ダイアログの表示状態を切り替える。
     */
    fun setUrlDialogVisible(visible: Boolean) {
        urlDialogState.value = visible
        if (!visible) {
            urlErrorState.value = null
        }
    }

    /**
     * URL入力ダイアログに表示するエラーメッセージを更新する。
     */
    fun setUrlErrorMessage(message: String?) {
        urlErrorState.value = message
    }

    /**
     * boardKey からホストを解決する。
     * DBに無い場合は bbsmenu を参照して補完する。
     */
    suspend fun resolveBoardHost(boardKey: String, sourceUrl: String? = null): String? {
        val menuDomain = resolveMenuDomainForHostLookup(sourceUrl)
        val cachedHost = boardRepository.resolveHostByBoardKey(
            boardKey = boardKey,
            requiredDomain = menuDomain,
        )
        if (cachedHost != null) return cachedHost
        return bbsServiceRepository.resolveHostByBoardKeyFromMenu(
                boardKey = boardKey,
                menuDomain = menuDomain,
            )
    }

    /**
     * itest板URLの入力元と設定値から、host補完に使うメニュードメインを決定する。
     */
    private suspend fun resolveMenuDomainForHostLookup(sourceUrl: String?): String? {
        val sourceHost = sourceUrl
            ?.let { kotlin.runCatching { java.net.URI(it).host?.lowercase() }.getOrNull() }
            ?: return null
        return when (sourceHost) {
            "itest.5ch.net" -> {
                // 初期読込中のキャッシュ値に依存せず、永続化済み設定値を直接参照する。
                if (settingsRepository.getIsRedirect5chNetToIoEnabled()) "5ch.io" else "5ch.net"
            }
            "itest.5ch.io" -> "5ch.io"
            else -> null
        }
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

/**
 * 板タブ一覧のロード状態とタブ情報をまとめる内部状態。
 */
private data class BoardTabsState(
    val openBoardTabs: List<BoardTabInfo>,
    val boardLoaded: Boolean,
)

/**
 * スレッドタブ一覧のロード状態、更新進捗、新着件数をまとめる内部状態。
 */
private data class ThreadTabsState(
    val openThreadTabs: List<ThreadTabInfo>,
    val threadLoaded: Boolean,
    val isRefreshing: Boolean,
    val refreshProgress: ThreadTabRefreshProgress?,
    val newResCounts: Map<String, Int>,
)
