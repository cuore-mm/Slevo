package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.ui.unit.IntRect
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
import com.websarva.wings.android.slevo.ui.tabs.coordinator.BoardTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabRefreshProgress
import com.websarva.wings.android.slevo.ui.tabs.registry.TabViewModelRegistry
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadViewModel
import com.websarva.wings.android.slevo.ui.util.BoardUrlNormalizationInput
import com.websarva.wings.android.slevo.ui.util.normalizeBoardUrlTo5chIo
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

    private val tabSelectionState = MutableStateFlow(TabSelectionState())
    private val detailBoardTabState = MutableStateFlow<BoardTabInfo?>(null)
    private val detailThreadTabState = MutableStateFlow<ThreadTabInfo?>(null)
    private val showBoardInfoBottomSheetState = MutableStateFlow(false)
    private val showThreadInfoBottomSheetState = MutableStateFlow(false)
    private val pendingCloseBoardTabState = MutableStateFlow<BoardTabInfo?>(null)
    private val pendingCloseThreadTabState = MutableStateFlow<ThreadTabInfo?>(null)

    /**
     * URL入力ダイアログの検証・表示・エラー状態をまとめる内部状態。
     */
    private val urlDialogUiState = combine(
        urlValidationState,
        urlDialogState,
        urlErrorState,
    ) { isUrlValidating, showUrlDialog, urlErrorMessage ->
        UrlDialogState(
            isUrlValidating = isUrlValidating,
            showUrlDialog = showUrlDialog,
            urlErrorMessage = urlErrorMessage,
        )
    }

    /**
     * 長押し選択中のタブ情報と bounds をまとめる内部状態。
     */
    private val tabSelectionUiState = tabSelectionState

    /**
     * 詳細 BottomSheet の表示対象タブと表示フラグをまとめる内部状態。
     */
    private val tabDetailState = combine(
        detailBoardTabState,
        detailThreadTabState,
        showBoardInfoBottomSheetState,
        showThreadInfoBottomSheetState,
    ) { detailBoardTab, detailThreadTab, showBoardInfo, showThreadInfo ->
        TabDetailState(
            detailBoardTab = detailBoardTab,
            detailThreadTab = detailThreadTab,
            showBoardInfoBottomSheet = showBoardInfo,
            showThreadInfoBottomSheet = showThreadInfo,
        )
    }

    /**
     * 長押しメニューからの削除要求をまとめる内部状態。
     */
    private val pendingCloseState = combine(
        pendingCloseBoardTabState,
        pendingCloseThreadTabState,
    ) { pendingBoardTab, pendingThreadTab ->
        PendingCloseState(
            pendingBoardTab = pendingBoardTab,
            pendingThreadTab = pendingThreadTab,
        )
    }

    private val baseUiState = combine(
        boardTabsState,
        threadTabsState,
        urlDialogUiState,
        tabSelectionUiState,
        tabDetailState,
    ) { boardState, threadState, urlState, selectionState, detailState ->
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
            selectedBoardTab = selectionState.selectedBoardTab,
            selectedThreadTab = selectionState.selectedThreadTab,
            selectedTabBounds = selectionState.selectedTabBounds,
            detailBoardTab = detailState.detailBoardTab,
            detailThreadTab = detailState.detailThreadTab,
            showBoardInfoBottomSheet = detailState.showBoardInfoBottomSheet,
            showThreadInfoBottomSheet = detailState.showThreadInfoBottomSheet,
        )
    }

    val uiState: StateFlow<TabsUiState> = combine(
        baseUiState,
        pendingCloseState,
    ) { baseState, pendingClose ->
        baseState.copy(
            pendingCloseBoardTab = pendingClose.pendingBoardTab,
            pendingCloseThreadTab = pendingClose.pendingThreadTab,
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

    // --- Long-press selection ---

    /**
     * 板タブを長押ししたときに選択状態を開始する。
     * 対象タブと画面上の bounds を保存し、アクションメニュー表示に備える。
     */
    fun onBoardTabLongPressed(tab: BoardTabInfo, bounds: IntRect) {
        cancelTabSelection()
        tabSelectionState.value = TabSelectionState(
            selectedBoardTab = tab,
            selectedTabBounds = bounds,
        )
    }

    /**
     * スレッドタブを長押ししたときに選択状態を開始する。
     * 対象タブと画面上の bounds を保存し、アクションメニュー表示に備える。
     */
    fun onThreadTabLongPressed(tab: ThreadTabInfo, bounds: IntRect) {
        cancelTabSelection()
        tabSelectionState.value = TabSelectionState(
            selectedThreadTab = tab,
            selectedTabBounds = bounds,
        )
    }

    /**
     * 長押し選択状態とアクションメニューを解除する。
     * overlay タップ、メニュー dismissal、戻るキー、ページ切替、選択中タブ消失などから共通利用する。
     */
    fun cancelTabSelection() {
        tabSelectionState.value = TabSelectionState()
        showBoardInfoBottomSheetState.value = false
        showThreadInfoBottomSheetState.value = false
    }

    /**
     * 選択中のタブの固定状態を切り替える。
     * 選択解除後に固定状態を保存する。
     */
    fun toggleSelectedTabPin() {
        tabSelectionState.value.selectedBoardTab?.let { tab ->
            boardTabsCoordinator.togglePinBoardTab(tab.boardUrl)
        }
        tabSelectionState.value.selectedThreadTab?.let { tab ->
            threadTabsCoordinator.togglePinThreadTab(tab.id)
        }
        cancelTabSelection()
    }

    /**
     * 選択中タブの詳細 BottomSheet を表示する。
     * メニュー選択後に選択解除し、対応する BottomSheet を表示する。
     */
    fun openSelectedTabDetail() {
        tabSelectionState.value.selectedBoardTab?.let {
            detailBoardTabState.value = it
            showBoardInfoBottomSheetState.value = true
        }
        tabSelectionState.value.selectedThreadTab?.let {
            detailThreadTabState.value = it
            showThreadInfoBottomSheetState.value = true
        }
        // BottomSheet state と detail state は残し、長押し選択 state だけ解除する。
        tabSelectionState.value = TabSelectionState()
    }

    /**
     * 選択中のタブを閉じる。
     * 選択解除後にタブを削除する。
     */
    fun requestCloseSelectedTab() {
        tabSelectionState.value.selectedBoardTab?.let { tab ->
            pendingCloseBoardTabState.value = tab
        }
        tabSelectionState.value.selectedThreadTab?.let { tab ->
            pendingCloseThreadTabState.value = tab
        }
        cancelTabSelection()
    }

    /**
     * 長押しメニューからの削除要求を消費済みにする。
     */
    fun consumePendingCloseRequest() {
        pendingCloseBoardTabState.value = null
        pendingCloseThreadTabState.value = null
    }

    /**
     * ページ切替時に長押し選択状態を解除する。
     */
    fun onPageChanged() {
        cancelTabSelection()
    }

    /**
     * 板タブ詳細 BottomSheet の表示を閉じる。
     */
    fun dismissBoardInfoBottomSheet() {
        showBoardInfoBottomSheetState.value = false
    }

    /**
     * スレッドタブ詳細 BottomSheet の表示を閉じる。
     */
    fun dismissThreadInfoBottomSheet() {
        showThreadInfoBottomSheetState.value = false
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

    /**
     * URL入力処理の結果を表す sealed class。
     * 画面側はこの結果に基づいて navigation API を呼び出す。
     */
    sealed class UrlOpenResult {
        data class NavigateBoard(val route: AppRoute.Board) : UrlOpenResult()
        data class NavigateThread(val route: AppRoute.Thread) : UrlOpenResult()
        data class Error(val message: String?) : UrlOpenResult()
    }

    /**
     * URL 入力文字列を解析し、遷移先 route またはエラーを返す。
     * 検証状態の開始と終了をこのメソッド内で完結させる。
     */
    suspend fun openUrlInput(url: String, invalidUrlMessage: String): UrlOpenResult {
        startUrlValidation()
        return try {
            when (val resolved = resolveUrl(url)) {
                is com.websarva.wings.android.slevo.ui.util.ResolvedUrl.ItestBoard -> {
                    val host = resolveBoardHost(
                        boardKey = resolved.boardKey,
                        sourceUrl = resolved.rawUrl,
                    )
                    if (host != null) {
                        val boardUrl = "https://$host/${resolved.boardKey}/"
                        val route = normalizeBoardRouteForNavigation(
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
                    val route = normalizeThreadRouteForNavigation(
                        AppRoute.Thread(
                            threadKey = resolved.threadKey,
                            boardUrl = boardUrl,
                            boardName = resolved.boardKey,
                            threadTitle = null
                        )
                    )
                    setUrlErrorMessage(null)
                    setUrlDialogVisible(false)
                    UrlOpenResult.NavigateThread(route)
                }

                is com.websarva.wings.android.slevo.ui.util.ResolvedUrl.Board -> {
                    val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                    val route = normalizeBoardRouteForNavigation(
                        AppRoute.Board(boardName = boardUrl, boardUrl = boardUrl)
                    )
                    setUrlErrorMessage(null)
                    setUrlDialogVisible(false)
                    UrlOpenResult.NavigateBoard(route)
                }

                else -> {
                    UrlOpenResult.Error(invalidUrlMessage)
                }
            }
        } finally {
            finishUrlValidation()
        }
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

/**
 * URL入力ダイアログの検証・表示・エラー状態をまとめる内部状態。
 */
private data class UrlDialogState(
    val isUrlValidating: Boolean = false,
    val showUrlDialog: Boolean = false,
    val urlErrorMessage: String? = null,
)

/**
 * 長押し選択中のタブ情報と bounds をまとめる内部状態。
 */
private data class TabSelectionState(
    val selectedBoardTab: BoardTabInfo? = null,
    val selectedThreadTab: ThreadTabInfo? = null,
    val selectedTabBounds: IntRect? = null,
)

/**
 * 詳細 BottomSheet の表示対象タブと表示フラグをまとめる内部状態。
 */
private data class TabDetailState(
    val detailBoardTab: BoardTabInfo? = null,
    val detailThreadTab: ThreadTabInfo? = null,
    val showBoardInfoBottomSheet: Boolean = false,
    val showThreadInfoBottomSheet: Boolean = false,
)

/**
 * 長押しメニューからの削除要求をまとめる内部状態。
 */
private data class PendingCloseState(
    val pendingBoardTab: BoardTabInfo? = null,
    val pendingThreadTab: ThreadTabInfo? = null,
)
