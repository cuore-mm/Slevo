package com.websarva.wings.android.slevo.ui.tabs.store

import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.BbsServiceRepository
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.bbsroute.TabPresentationState
import com.websarva.wings.android.slevo.ui.tabs.coordinator.BoardTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsLoadState
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabRefreshProgress
import com.websarva.wings.android.slevo.ui.tabs.session.BoardSessionState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionRuntimeState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState
import com.websarva.wings.android.slevo.ui.tabs.session.holder.BoardTabSessionHolder
import com.websarva.wings.android.slevo.ui.tabs.session.holder.BoardTabSessionHolderFactory
import com.websarva.wings.android.slevo.ui.tabs.session.holder.ThreadTabSessionHolder
import com.websarva.wings.android.slevo.ui.tabs.session.holder.ThreadTabSessionHolderFactory
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.util.BoardUrlNormalizationInput
import com.websarva.wings.android.slevo.ui.util.normalizeBoardUrlTo5chIo
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
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
    private val threadTabSessionHolderFactory: ThreadTabSessionHolderFactory,
    private val boardTabSessionHolderFactory: BoardTabSessionHolderFactory,
    private val tabsRepository: TabsRepository,
    private val boardRepository: BoardRepository,
    private val bbsServiceRepository: BbsServiceRepository,
    private val settingsRepository: SettingsRepository,
    private val appLogger: AppLogger,
) : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val threadSessionHolders = mutableMapOf<String, ThreadTabSessionHolder>()
    private val boardSessionHolders = mutableMapOf<String, BoardTabSessionHolder>()

    // --- Session state exposure ---

    val openBoardTabs: StateFlow<List<BoardTabInfo>> = boardTabsCoordinator.openBoardTabs
    val openThreadTabs: StateFlow<List<ThreadTabInfo>> = threadTabsCoordinator.openThreadTabs
    val boardLoaded: StateFlow<Boolean> = boardTabsCoordinator.boardLoaded
    val threadLoaded: StateFlow<Boolean> = threadTabsCoordinator.threadLoaded
    val threadTabState: StateFlow<ThreadTabsLoadState> = threadTabsCoordinator.threadTabState
    val isRefreshing: StateFlow<Boolean> = threadTabsCoordinator.isRefreshing
    val refreshProgress: StateFlow<ThreadTabRefreshProgress?> = threadTabsCoordinator.refreshProgress
    val newResCounts: StateFlow<Map<String, Int>> = threadTabsCoordinator.newResCounts
    val selectedBoardTabKey: StateFlow<String?> = boardTabsCoordinator.selectedBoardTabKey
    val selectedThreadTabKey: StateFlow<String?> = threadTabsCoordinator.selectedThreadTabKey
    val boardPresentationState: StateFlow<TabPresentationState<BoardTabInfo, String>> =
        boardTabsCoordinator.boardPresentationState
    val threadPresentationState: StateFlow<TabPresentationState<ThreadTabInfo, String>> =
        threadTabsCoordinator.threadPresentationState
    val boardSessionStates: StateFlow<Map<String, BoardSessionState>> = boardTabsCoordinator.boardSessionStates
    val threadSessionStates: StateFlow<Map<String, ThreadSessionState>> = threadTabsCoordinator.threadSessionStates

    val boardPageAnimation: SharedFlow<Int> = boardTabsCoordinator.boardPageAnimation
    val threadPageAnimation: SharedFlow<Int> = threadTabsCoordinator.threadPageAnimation

    val lastSelectedTabsPage = tabsRepository.observeLastSelectedTabsPage()

    init {
        boardTabsCoordinator.bind(scope)
        threadTabsCoordinator.bind(scope)
    }

    // --- Tab operations ---

    fun ensureBoardTab(route: AppRoute.Board): Int {
        return boardTabsCoordinator.ensureBoardTab(route)
    }

    /**
     * 板タブを保証したうえで、対象タブを選択状態へ更新する。
     */
    fun ensureAndSelectBoardTab(route: AppRoute.Board): Int {
        val index = ensureBoardTab(route)
        if (index >= 0) {
            selectBoardTab(route.boardUrl)
        }
        return index
    }

    /**
     * 正規化済み板 route から板タブを登録し、選択状態へ更新する。
     */
    fun registerAndSelectBoardRoute(route: AppRoute.Board): Int = ensureAndSelectBoardTab(route)

    /**
     * 板 route を登録・選択し、atomic presentation state が target を Selected と確認するまで待機する。
     * 登録または確認が失敗した場合は navigation を開始しない。
     */
    suspend fun registerAndConfirmBoardRoute(route: AppRoute.Board): Boolean {
        return when (val result = boardTabsCoordinator.ensureBoardTabCommand(route)) {
            is com.websarva.wings.android.slevo.ui.tabs.controller.TabCommandResult.Success,
            is com.websarva.wings.android.slevo.ui.tabs.controller.TabCommandResult.NoOp -> {
                boardTabsCoordinator.selectBoardTabCommand(route.boardUrl) is
                    com.websarva.wings.android.slevo.ui.tabs.controller.TabCommandResult.Success
            }
            is com.websarva.wings.android.slevo.ui.tabs.controller.TabCommandResult.Failure -> false
        }
    }

    /**
     * 選択中の板タブ key を更新する。
     */
    fun selectBoardTab(boardUrl: String?) {
        boardTabsCoordinator.selectBoardTab(boardUrl)
    }

    fun closeBoardTab(tab: BoardTabInfo) {
        boardSessionHolders.remove(tab.boardUrl)?.dispose()
        boardTabsCoordinator.closeBoardTab(tab)
    }

    fun closeBoardTabByUrl(boardUrl: String) {
        boardSessionHolders.remove(boardUrl)?.dispose()
        boardTabsCoordinator.closeBoardTabByUrl(boardUrl)
    }

    /** 表示中ページの未固定タブをスナップショットし、bulk close 経路へ即時に委譲する。 */
    fun closeAllUnpinnedTabs(page: TabPage) {
        when (page) {
            TabPage.BOARD -> {
                // 公開 projection を一度だけ読み、処理中の一覧変化で対象をずらさない。
                val targets = openBoardTabs.value.filterNot(BoardTabInfo::isPinned)
                if (targets.isEmpty()) return
                closeBoardTabTargets(targets)
            }

            TabPage.THREAD -> {
                // Thread bulkはretained scopeでcanonical確認まで所有し、画面破棄後も継続する。
                val targets = openThreadTabs.value.filterNot(ThreadTabInfo::isPinned)
                if (targets.isEmpty()) return
                disposeThreadTabHolders(targets.map { it.id.value })
                scope.launch {
                    closeThreadTabsSafely(targets)
                }
            }
        }
    }

    /** クリック時の板タブsnapshotをretained scopeで待機後にbulk closeする。 */
    fun closeBoardTabsAfterDelay(targets: List<BoardTabInfo>, delayMillis: Long) {
        if (targets.isEmpty()) return
        scope.launch {
            delay(delayMillis)
            closeBoardTabTargets(targets)
        }
    }

    /** クリック時のスレッドタブsnapshotをretained scopeで待機後にbulk closeする。 */
    fun closeThreadTabsAfterDelay(targets: List<ThreadTabInfo>, delayMillis: Long) {
        if (targets.isEmpty()) return
        scope.launch {
            delay(delayMillis)
            closeThreadTabTargets(targets)
        }
    }

    /** 固定状態を再評価せず、受け取った板タブsnapshotだけをbulk closeする。 */
    private fun closeBoardTabTargets(targets: List<BoardTabInfo>) {
        val distinctTargets = targets.distinctBy(BoardTabInfo::boardUrl)
        if (distinctTargets.isEmpty()) return
        disposeBoardTabHolders(distinctTargets.map(BoardTabInfo::boardUrl))
        boardTabsCoordinator.closeBoardTabs(distinctTargets)
    }

    /** 固定状態を再評価せず、受け取ったスレッドタブsnapshotだけをbulk closeする。 */
    private suspend fun closeThreadTabTargets(targets: List<ThreadTabInfo>) {
        val distinctTargets = targets.distinctBy { it.id.value }
        if (distinctTargets.isEmpty()) return
        disposeThreadTabHolders(distinctTargets.map { it.id.value })
        closeThreadTabsSafely(distinctTargets)
    }

    /** Thread bulk失敗をログへ記録し、Storeのroot coroutineへ例外を伝播させない。 */
    private suspend fun closeThreadTabsSafely(targets: List<ThreadTabInfo>) {
        try {
            threadTabsCoordinator.closeThreadTabs(targets)
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: Throwable) {
            runCatching {
                appLogger.e(
                    message = "Thread bulk close failed for ${targets.size} tabs",
                    tag = "TabSessionStore",
                    throwable = exception,
                )
            }
        }
    }

    /** 指定された板URLの既存holderだけを一括でmapから取り出して破棄する。 */
    private fun disposeBoardTabHolders(boardUrls: List<String>) {
        boardUrls.distinct().mapNotNull { boardSessionHolders.remove(it) }.forEach { holder ->
            holder.dispose()
        }
    }

    /** 指定されたThreadId文字列の既存holderだけを一括でmapから取り出して破棄する。 */
    private fun disposeThreadTabHolders(threadIds: List<String>) {
        threadIds.distinct().mapNotNull { threadSessionHolders.remove(it) }.forEach { holder ->
            holder.dispose()
        }
    }

    fun updateBoardScrollPosition(boardUrl: String, firstVisibleIndex: Int, scrollOffset: Int) {
        boardTabsCoordinator.updateBoardScrollPosition(boardUrl, firstVisibleIndex, scrollOffset)
    }

    /** 板タブの stable key 順を retained Coordinator へ渡す。 */
    fun reorderBoardTabs(boardUrls: List<String>): Boolean =
        boardTabsCoordinator.reorderBoardTabs(boardUrls)

    fun animateBoardPage(offset: Int) {
        boardTabsCoordinator.animateBoardPage(offset)
    }

    /**
     * 指定板タブの揮発 UI セッション状態を返す。
     */
    fun getBoardSessionState(boardUrl: String): BoardSessionState {
        return boardTabsCoordinator.getBoardSessionState(boardUrl)
    }

    /**
     * 指定板タブの揮発 UI セッション状態を更新する。
     */
    fun updateBoardSessionState(
        boardUrl: String,
        transform: (BoardSessionState) -> BoardSessionState,
    ) {
        boardTabsCoordinator.updateBoardSessionState(boardUrl, transform)
    }

    /** 解決済みの boardId と名称を既存板タブへ反映する。 */
    fun updateBoardResolvedInfo(
        boardUrl: String,
        boardId: Long,
        boardName: String? = null,
    ) {
        boardTabsCoordinator.updateBoardResolvedInfo(boardUrl, boardId, boardName)
    }

    suspend fun ensureThreadTab(route: AppRoute.Thread): Int {
        return threadTabsCoordinator.ensureThreadTab(route)
    }

    /** 初回 Room スナップショットが正規状態になるまで待機する。 */
    suspend fun awaitThreadTabsReady(): ThreadTabsLoadState.Loaded =
        threadTabsCoordinator.threadTabState.filterIsInstance<ThreadTabsLoadState.Loaded>().first()

    /**
     * スレッドタブを保証したうえで、対象タブを選択状態へ更新する。
     */
    suspend fun ensureAndSelectThreadTab(route: AppRoute.Thread): Int {
        val index = ensureThreadTab(route)
        if (index < 0) return -1
        val threadId = com.websarva.wings.android.slevo.ui.util.parseBoardUrl(route.boardUrl)
            ?.let { (host, board) -> ThreadId.of(host, board, route.threadKey) }
            ?: return -1
        if (!isCanonicalThreadTab(threadId) || !threadTabsCoordinator.selectThreadTab(threadId)) return -1
        return index
    }

    /**
     * 正規化済みスレッド route からスレッドタブを登録し、選択状態へ更新する。
     */
    suspend fun registerAndSelectThreadRoute(route: AppRoute.Thread): Int = ensureAndSelectThreadTab(route)

    /** 正規化済みスレッド route を正規状態で確認できるまで登録し、選択は行わない。 */
    suspend fun registerThreadRoute(route: AppRoute.Thread): Int = ensureThreadTab(route)

    /**
     * 選択中のスレッドタブ key を更新する。
     */
    fun selectThreadTab(threadId: ThreadId?): Boolean = threadTabsCoordinator.selectThreadTab(threadId)

    /** 最新の正規 Room スナップショットに thread ID が存在するかどうかを返す。 */
    fun isCanonicalThreadTab(threadId: ThreadId): Boolean =
        threadTabsCoordinator.isCanonicalThreadTab(threadId)

    suspend fun closeThreadTab(tab: ThreadTabInfo) {
        threadSessionHolders.remove(tab.id.value)?.dispose()
        threadTabsCoordinator.closeThreadTab(tab)
    }

    suspend fun closeThreadTab(threadKey: String, boardUrl: String) {
        parseBoardUrl(boardUrl)?.let { (host, board) ->
            val threadId = ThreadId.of(host, board, threadKey)
            threadSessionHolders.remove(threadId.value)?.dispose()
        }
        threadTabsCoordinator.closeThreadTab(threadKey, boardUrl)
    }

    /**
     * 確定したスレッドタブ close を retained store scope へ移譲する。
     *
     * この scope は画面 Composition より長く存続するため、repository 書き込みと Room の正規確認まで
     * close 処理を所有する。Activity-retained component が破棄された場合は [close] によりキャンセルされる。
     */
    fun requestCloseThreadTab(threadKey: String, boardUrl: String) {
        scope.launch { closeThreadTab(threadKey, boardUrl) }
    }

    fun animateThreadPage(offset: Int) {
        threadTabsCoordinator.animateThreadPage(offset)
    }

    /**
     * 指定スレッドタブの揮発 UI セッション状態を返す。
     */
    fun getThreadSessionState(threadId: ThreadId): ThreadSessionState {
        return threadTabsCoordinator.getThreadSessionState(threadId)
    }

    /**
     * 指定スレッドタブの揮発 UI セッション状態を更新する。
     */
    fun updateThreadSessionState(
        threadId: ThreadId,
        transform: (ThreadSessionState) -> ThreadSessionState,
    ) {
        threadTabsCoordinator.updateThreadSessionState(threadId, transform)
    }

    /**
     * 指定スレッドタブの継続ランタイム状態を返す。
     */
    fun getThreadRuntimeState(threadId: ThreadId): ThreadSessionRuntimeState {
        return threadTabsCoordinator.getThreadRuntimeState(threadId)
    }

    /**
     * 指定スレッドタブの継続ランタイム状態を更新する。
     */
    fun updateThreadRuntimeState(
        threadId: ThreadId,
        transform: (ThreadSessionRuntimeState) -> ThreadSessionRuntimeState,
    ) {
        threadTabsCoordinator.updateThreadRuntimeState(threadId, transform)
    }

    /** 解決済みの boardId と名称を既存スレッドタブへ反映する。 */
    suspend fun updateThreadResolvedBoardInfo(
        threadId: ThreadId,
        boardId: Long,
        boardName: String? = null,
    ) {
        threadTabsCoordinator.updateThreadResolvedBoardInfo(threadId, boardId, boardName)
    }

    fun clearNewResCount(threadId: ThreadId) {
        threadTabsCoordinator.clearNewResCount(threadId)
    }

    /** スレッドタブの stable key 順を retained Coordinator へ渡す。 */
    fun reorderThreadTabs(threadIds: List<String>): Boolean =
        threadTabsCoordinator.reorderThreadTabs(threadIds)

    fun refreshOpenThreads() {
        threadTabsCoordinator.refreshOpenThreads()
    }

    fun cancelRefreshOpenThreads() {
        threadTabsCoordinator.cancelRefreshOpenThreads()
    }

    fun togglePinBoardTab(boardUrl: String) {
        boardTabsCoordinator.togglePinBoardTab(boardUrl)
    }

    suspend fun togglePinThreadTab(threadId: com.websarva.wings.android.slevo.data.model.ThreadId) {
        threadTabsCoordinator.togglePinThreadTab(threadId)
    }

    // --- Per-tab session holders ---

    /**
     * 指定スレッドタブのブックマークシート holder を返す。
     */
    fun threadBookmarkSheetHolder(tabKey: String): com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder {
        return threadSessionHolder(tabKey).bookmarkSheetHolder
    }

    /**
     * 指定スレッドタブの投稿ダイアログコントローラを返す。
     */
    fun threadPostDialogController(tabKey: String): com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController {
        return threadSessionHolder(tabKey).postDialogController
    }

    /**
     * 指定スレッドタブの投稿成功イベント Flow を返す。
     */
    fun threadPostDialogSuccessEvents(tabKey: String): kotlinx.coroutines.flow.SharedFlow<com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogSuccess> {
        return threadSessionHolder(tabKey).postSuccessEvents
    }

    /**
     * 指定スレッドタブの画像保存イベント Flow を返す。
     */
    fun threadImageSaveEvents(tabKey: String): kotlinx.coroutines.flow.SharedFlow<com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent> {
        return threadSessionHolder(tabKey).imageSaveEvents
    }

    /**
     * 指定スレッドタブの画像保存要求を処理する。
     */
    fun threadRequestImageSave(tabKey: String, context: android.content.Context, urls: List<String>) {
        threadSessionHolder(tabKey).requestImageSave(context, urls)
    }

    /**
     * 指定スレッドタブの画像保存権限要求結果を処理する。
     */
    fun threadOnImageSavePermissionResult(tabKey: String, context: android.content.Context, granted: Boolean) {
        threadSessionHolder(tabKey).onImageSavePermissionResult(context, granted)
    }

    /**
     * 指定スレッドタブの投稿ダイアログに画像をアップロードする。
     */
    fun threadUploadPostDialogImage(tabKey: String, context: android.content.Context, uri: android.net.Uri) {
        threadSessionHolder(tabKey).uploadPostDialogImage(context, uri)
    }

    private fun threadSessionHolder(tabKey: String): com.websarva.wings.android.slevo.ui.tabs.session.holder.ThreadTabSessionHolder {
        return threadSessionHolders.getOrPut(tabKey) {
            threadTabSessionHolderFactory.create(tabKey, ThreadId(tabKey))
        }
    }

    /**
     * 指定板タブのブックマークシート holder を返す。
     */
    fun boardBookmarkSheetHolder(tabKey: String): com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder {
        return boardSessionHolder(tabKey).bookmarkSheetHolder
    }

    /**
     * 指定板タブの投稿ダイアログコントローラを返す。
     */
    fun boardPostDialogController(tabKey: String): com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController {
        return boardSessionHolder(tabKey).postDialogController
    }

    /**
     * 指定板タブの投稿成功イベント Flow を返す。
     */
    fun boardPostDialogSuccessEvents(tabKey: String): kotlinx.coroutines.flow.SharedFlow<com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogSuccess> {
        return boardSessionHolder(tabKey).postSuccessEvents
    }

    /**
     * 指定板タブの投稿ダイアログに画像をアップロードする。
     */
    fun boardUploadPostDialogImage(tabKey: String, context: android.content.Context, uri: android.net.Uri) {
        boardSessionHolder(tabKey).uploadPostDialogImage(context, uri)
    }

    private fun boardSessionHolder(tabKey: String): com.websarva.wings.android.slevo.ui.tabs.session.holder.BoardTabSessionHolder {
        return boardSessionHolders.getOrPut(tabKey) {
            boardTabSessionHolderFactory.create(tabKey, tabKey)
        }
    }

    // --- Tabs page persistence ---

    fun setLastSelectedTabsPage(page: Int) {
        scope.launch { tabsRepository.setLastSelectedTabsPage(page) }
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
        threadSessionHolders.values.forEach { it.dispose() }
        threadSessionHolders.clear()
        boardSessionHolders.values.forEach { it.dispose() }
        boardSessionHolders.clear()
        boardTabsCoordinator.close()
        threadTabsCoordinator.close()
        scope.cancel()
    }
}
