package com.websarva.wings.android.slevo.ui.tabs.store

import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.BbsServiceRepository
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.ui.board.viewmodel.BoardViewModel
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.coordinator.BoardTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabRefreshProgress
import com.websarva.wings.android.slevo.ui.tabs.registry.TabViewModelRegistry
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModel
import com.websarva.wings.android.slevo.ui.util.BoardUrlNormalizationInput
import com.websarva.wings.android.slevo.ui.util.normalizeBoardUrlTo5chIo
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import javax.inject.Inject

/**
 * アプリ内タブセッションの状態と操作を管理する非ViewModelコンポーネント。
 *
 * 開いている板/スレッドタブ、ページ状態、スレッド更新、子ViewModelキャッシュなど、
 * 特定画面に紐づかないタブセッション管理の正本を保持する。
 * 構成変更時にもセッション状態を維持するため ActivityRetainedScoped とする。
 */
@ActivityRetainedScoped
class TabSessionStore @Inject constructor(
    internal val boardTabsCoordinator: BoardTabsCoordinator,
    internal val threadTabsCoordinator: ThreadTabsCoordinator,
    internal val tabViewModelRegistry: TabViewModelRegistry,
    private val tabsRepository: TabsRepository,
    private val boardRepository: BoardRepository,
    private val bbsServiceRepository: BbsServiceRepository,
    private val settingsRepository: SettingsRepository,
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // --- Session state exposure ---

    val openBoardTabs: StateFlow<List<BoardTabInfo>> = boardTabsCoordinator.openBoardTabs
    val openThreadTabs: StateFlow<List<ThreadTabInfo>> = threadTabsCoordinator.openThreadTabs
    val boardLoaded: StateFlow<Boolean> = boardTabsCoordinator.boardLoaded
    val threadLoaded: StateFlow<Boolean> = threadTabsCoordinator.threadLoaded
    val isRefreshing: StateFlow<Boolean> = threadTabsCoordinator.isRefreshing
    val refreshProgress: StateFlow<ThreadTabRefreshProgress?> = threadTabsCoordinator.refreshProgress
    val newResCounts: StateFlow<Map<String, Int>> = threadTabsCoordinator.newResCounts

    val boardCurrentPage: StateFlow<Int> = boardTabsCoordinator.boardCurrentPage
    val threadCurrentPage: StateFlow<Int> = threadTabsCoordinator.threadCurrentPage
    val boardPageAnimation: SharedFlow<Int> = boardTabsCoordinator.boardPageAnimation
    val threadPageAnimation: SharedFlow<Int> = threadTabsCoordinator.threadPageAnimation

    val lastSelectedPage = tabsRepository.observeLastSelectedPage()

    init {
        boardTabsCoordinator.bind(scope)
        threadTabsCoordinator.bind(scope)
    }

    // --- Child ViewModel access ---

    fun getOrCreateThreadViewModel(viewModelKey: String): ThreadViewModel {
        return tabViewModelRegistry.getOrCreateThreadViewModel(viewModelKey)
    }

    fun getOrCreateBoardViewModel(boardUrl: String): BoardViewModel {
        return tabViewModelRegistry.getOrCreateBoardViewModel(boardUrl)
    }

    fun releaseBoardViewModel(boardUrl: String) {
        tabViewModelRegistry.releaseBoardViewModel(boardUrl)
    }

    fun releaseThreadViewModel(viewModelKey: String) {
        tabViewModelRegistry.releaseThreadViewModel(viewModelKey)
    }

    fun releaseAllViewModels() {
        tabViewModelRegistry.releaseAll()
    }

    // --- Tab operations ---

    fun ensureBoardTab(route: AppRoute.Board): Int {
        return boardTabsCoordinator.ensureBoardTab(route)
    }

    fun closeBoardTab(tab: BoardTabInfo) {
        boardTabsCoordinator.closeBoardTab(tab)
    }

    fun closeBoardTabByUrl(boardUrl: String) {
        boardTabsCoordinator.closeBoardTabByUrl(boardUrl)
    }

    fun updateBoardScrollPosition(boardUrl: String, firstVisibleIndex: Int, scrollOffset: Int) {
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

    fun cancelRefreshOpenThreads() {
        threadTabsCoordinator.cancelRefreshOpenThreads()
    }

    fun togglePinBoardTab(boardUrl: String) {
        boardTabsCoordinator.togglePinBoardTab(boardUrl)
    }

    fun togglePinThreadTab(threadId: com.websarva.wings.android.slevo.data.model.ThreadId) {
        threadTabsCoordinator.togglePinThreadTab(threadId)
    }

    // --- Page persistence ---

    fun setLastSelectedPage(page: Int) {
        scope.launch { tabsRepository.setLastSelectedPage(page) }
    }

    // --- Navigation helpers ---

    suspend fun normalizeBoardRouteForNavigation(route: AppRoute.Board): AppRoute.Board {
        val isEnabled = settingsRepository.getIsRedirect5chNetToIoEnabled()
        val normalizedUrl = normalizeBoardUrlTo5chIo(
            BoardUrlNormalizationInput(boardUrl = route.boardUrl, isEnabled = isEnabled)
        )
        if (normalizedUrl == route.boardUrl) return route
        return route.copy(boardUrl = normalizedUrl)
    }

    suspend fun normalizeThreadRouteForNavigation(route: AppRoute.Thread): AppRoute.Thread {
        val isEnabled = settingsRepository.getIsRedirect5chNetToIoEnabled()
        val normalizedUrl = normalizeBoardUrlTo5chIo(
            BoardUrlNormalizationInput(boardUrl = route.boardUrl, isEnabled = isEnabled)
        )
        if (normalizedUrl == route.boardUrl) return route
        return route.copy(boardUrl = normalizedUrl)
    }

    // --- URL resolution helpers ---

    suspend fun resolveBoardHost(boardKey: String, sourceUrl: String? = null): String? {
        val menuDomain = resolveMenuDomainForHostLookup(sourceUrl)
        val cachedHost = boardRepository.resolveHostByBoardKey(boardKey = boardKey, requiredDomain = menuDomain)
        if (cachedHost != null) return cachedHost
        return bbsServiceRepository.resolveHostByBoardKeyFromMenu(boardKey = boardKey, menuDomain = menuDomain)
    }

    private suspend fun resolveMenuDomainForHostLookup(sourceUrl: String?): String? {
        val sourceHost = sourceUrl
            ?.let { kotlin.runCatching { java.net.URI(it).host?.lowercase() }.getOrNull() }
            ?: return null
        return when (sourceHost) {
            "itest.5ch.net" -> {
                if (settingsRepository.getIsRedirect5chNetToIoEnabled()) "5ch.io" else "5ch.net"
            }
            "itest.5ch.io" -> "5ch.io"
            else -> null
        }
    }

    suspend fun resolveBoardInfo(boardId: Long?, boardUrl: String, boardName: String): BoardInfo? {
        boardId?.takeIf { it != 0L }?.let { return BoardInfo(it, boardName, boardUrl) }
        boardRepository.findBoardByUrl(boardUrl)?.let { entity ->
            return BoardInfo(entity.boardId, entity.name, entity.url)
        }
        val name = boardRepository.fetchBoardName("${boardUrl}SETTING.TXT") ?: return null
        val id = boardRepository.ensureBoard(BoardInfo(0L, name, boardUrl))
        return BoardInfo(id, name, boardUrl)
    }

    // --- Lifecycle ---

    /**
     * Activity retained scope終了時に未完了ジョブをキャンセルする。
     *
     * Hilt は [Closeable] を実装した [ActivityRetainedScoped] インスタンスの
     * [close] をコンポーネント破棄時に自動で呼び出す。
     */
    override fun close() {
        scope.cancel()
    }
}
