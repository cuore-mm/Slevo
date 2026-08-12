package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.component.TabListAnimationDefaults
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.util.resolveUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
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

    private val uiStateMutable = MutableStateFlow(TabListUiState())
    private var nextSearchFocusRequestId: Long = 0L

    val uiState: StateFlow<TabListUiState> = uiStateMutable.asStateFlow()

    // --- Search ---

    fun enterSearchMode() {
        cancelTabSelection()
        dismissBulkCloseMenu()
        nextSearchFocusRequestId += 1
        uiStateMutable.update { state ->
            state.copy(
                isSearchMode = true,
                pendingSearchFocusRequestId = nextSearchFocusRequestId,
            )
        }
    }

    /**
     * 検索モードを終了し、検索入力と未消費の UI 要求を初期状態へ戻す。
     */
    fun closeSearchMode() {
        uiStateMutable.update { state ->
            state.copy(
                isSearchMode = false,
                searchInputValue = TextFieldValue(""),
                pendingSearchFocusRequestId = null,
                pendingScrollToTopRequest = null,
            )
        }
    }

    /**
     * 検索バー入力の text と selection を更新し、必要なら検索結果の先頭表示要求を発行する。
     *
     * クエリ文字列の遷移で先頭表示要求を判断しつつ、selection だけが変わる場合も
     * 入力 state 全体は常に保持する。
     */
    fun updateSearchInput(inputValue: TextFieldValue, currentPage: Int) {
        val oldQuery = uiState.value.searchQuery
        val newQuery = inputValue.text

        // --- Query transition handling ---
        when {
            oldQuery.isBlank() && newQuery.isNotBlank() -> {
                uiStateMutable.update { state ->
                    state.copy(
                        searchInputValue = inputValue,
                        pendingScrollToTopRequest = TabListScrollToTopRequest(
                            page = currentPage,
                            query = newQuery,
                        ),
                    )
                }
            }

            oldQuery.isNotBlank() && newQuery.isNotBlank() && oldQuery != newQuery -> {
                uiStateMutable.update { state ->
                    state.copy(
                        searchInputValue = inputValue,
                        pendingScrollToTopRequest = TabListScrollToTopRequest(
                            page = currentPage,
                            query = newQuery,
                        ),
                    )
                }
            }

            oldQuery.isNotBlank() && newQuery.isBlank() -> {
                uiStateMutable.update { state ->
                    state.copy(
                        searchInputValue = inputValue,
                        pendingScrollToTopRequest = null,
                    )
                }
            }

            else -> {
                uiStateMutable.update { state ->
                    state.copy(searchInputValue = inputValue)
                }
            }
        }
    }

    /**
     * 検索クエリを更新し、必要なら検索結果の先頭表示要求を発行する。
     *
     * 空から非空、または別の非空クエリへ変わると、現在表示中ページの検索結果リストを
     * 先頭表示する要求を保持する。非空から空へ戻る場合は、通常リスト復元要求を発行せず
     * 検索結果向けの要求だけをクリアする。
     */
    fun updateSearchQuery(query: String, currentPage: Int) {
        updateSearchInput(
            inputValue = TextFieldValue(
                text = query,
                selection = TextRange(query.length),
            ),
            currentPage = currentPage,
        )
    }

    /**
     * 未消費の検索バー自動フォーカス要求を消費する。
     */
    fun consumePendingSearchFocusRequest() {
        uiStateMutable.update { state ->
            state.copy(pendingSearchFocusRequestId = null)
        }
    }

    /**
     * 復元待ちの先頭表示要求を消費する。
     *
     * UI 側が検索結果の先頭表示を実行した後に呼び出し、
     * 同じクエリに対する再実行を防ぐ。
     */
    fun consumePendingScrollToTopRequest() {
        uiStateMutable.update { state ->
            state.copy(pendingScrollToTopRequest = null)
        }
    }

    /**
     * 検索状態を完全に破棄する。
     *
     * 検索モード、検索クエリ、未消費の検索結果先頭表示要求をすべてクリアする。
     * BottomSheet dismiss 時など、表示コンテキストを終了するときに使用する。
     */
    fun resetSearchState() {
        uiStateMutable.update { state ->
            state.copy(
                isSearchMode = false,
                searchInputValue = TextFieldValue(""),
                pendingSearchFocusRequestId = null,
                pendingScrollToTopRequest = null,
            )
        }
    }

    // --- Long-press selection ---

    fun onBoardTabLongPressed(tab: BoardTabInfo, bounds: IntRect) {
        cancelTabSelection()
        uiStateMutable.update { state ->
            state.copy(
                selectedBoardTab = tab,
                selectedThreadTab = null,
                selectedTabBounds = bounds,
            )
        }
    }

    fun onThreadTabLongPressed(tab: ThreadTabInfo, bounds: IntRect) {
        cancelTabSelection()
        uiStateMutable.update { state ->
            state.copy(
                selectedBoardTab = null,
                selectedThreadTab = tab,
                selectedTabBounds = bounds,
            )
        }
    }

    fun cancelTabSelection() {
        uiStateMutable.update { state ->
            state.copy(
                selectedBoardTab = null,
                selectedThreadTab = null,
                selectedTabBounds = null,
                showBoardInfoBottomSheet = false,
                showThreadInfoBottomSheet = false,
            )
        }
    }

    fun toggleSelectedTabPin() {
        uiState.value.selectedBoardTab?.let { tab ->
            tabSessionStore.togglePinBoardTab(tab.boardUrl)
        }
        uiState.value.selectedThreadTab?.let { tab ->
            viewModelScope.launch { tabSessionStore.togglePinThreadTab(tab.id) }
        }
        cancelTabSelection()
    }

    fun openSelectedTabDetail() {
        uiState.value.selectedBoardTab?.let {
            uiStateMutable.update { state ->
                state.copy(
                    detailBoardTab = it,
                    showBoardInfoBottomSheet = true,
                    selectedBoardTab = null,
                    selectedThreadTab = null,
                    selectedTabBounds = null,
                )
            }
        }
        uiState.value.selectedThreadTab?.let {
            uiStateMutable.update { state ->
                state.copy(
                    detailThreadTab = it,
                    showThreadInfoBottomSheet = true,
                    selectedBoardTab = null,
                    selectedThreadTab = null,
                    selectedTabBounds = null,
                )
            }
        }
    }

    /** 選択中タブの退出を開始し、選択状態を解除する。 */
    fun requestCloseSelectedTab() {
        uiState.value.selectedBoardTab?.let { tab ->
            startBoardTabRemoval(tab)
        }
        uiState.value.selectedThreadTab?.let { tab ->
            startThreadTabRemoval(tab)
        }
        cancelTabSelection()
    }

    /** 閉じるボタンによる板タブ削除をアニメーション後に開始する。 */
    fun startBoardTabRemoval(tab: BoardTabInfo) {
        val accepted = addBoardRemovalKeys(listOf(tab.boardUrl))
        if (!accepted) return

        viewModelScope.launch {
            delay(TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong())
            tabSessionStore.closeBoardTab(tab)
        }
    }

    /** 閉じるボタンによるスレッドタブ削除をアニメーション後に開始する。 */
    fun startThreadTabRemoval(tab: ThreadTabInfo) {
        val accepted = addThreadRemovalKeys(listOf(tab.id.value))
        if (!accepted) return

        viewModelScope.launch {
            delay(TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong())
            tabSessionStore.requestCloseThreadTab(tab.threadKey, tab.boardUrl)
        }
    }

    /** UIへ削除中keyを公開し、板タブの二重削除を防止する。 */
    private fun addBoardRemovalKeys(boardUrls: List<String>): Boolean {
        val distinctUrls = boardUrls.distinct()
        if (distinctUrls.isEmpty()) return false
        var accepted = false
        uiStateMutable.update { state ->
            if (distinctUrls.any { it in state.removingBoardTabKeys }) {
                state
            } else {
                accepted = true
                state.copy(removingBoardTabKeys = state.removingBoardTabKeys + distinctUrls)
            }
        }
        return accepted
    }

    /** UIへ削除中keyを公開し、スレッドタブの二重削除を防止する。 */
    private fun addThreadRemovalKeys(threadIds: List<String>): Boolean {
        val distinctIds = threadIds.distinct()
        if (distinctIds.isEmpty()) return false
        var accepted = false
        uiStateMutable.update { state ->
            if (distinctIds.any { it in state.removingThreadTabKeys }) {
                state
            } else {
                accepted = true
                state.copy(removingThreadTabKeys = state.removingThreadTabKeys + distinctIds)
            }
        }
        return accepted
    }

    /** UIが正本一覧から消えた板タブの削除中状態を消費する。 */
    fun clearBoardRemovalKeys(boardUrls: Set<String>) {
        uiStateMutable.update { state ->
            state.copy(removingBoardTabKeys = state.removingBoardTabKeys - boardUrls)
        }
    }

    /** UIが正本一覧から消えたスレッドタブの削除中状態を消費する。 */
    fun clearThreadRemovalKeys(threadIds: Set<String>) {
        uiStateMutable.update { state ->
            state.copy(removingThreadTabKeys = state.removingThreadTabKeys - threadIds)
        }
    }

    /** その他メニューを指定されたアンカー位置で表示する。 */
    fun showBulkCloseMenu(anchorBounds: IntRect) {
        uiStateMutable.update { state ->
            state.copy(
                isBulkCloseMenuVisible = true,
                bulkCloseMenuBounds = anchorBounds,
            )
        }
    }

    /** その他メニューを閉じ、保持しているアンカー位置を破棄する。 */
    fun dismissBulkCloseMenu() {
        uiStateMutable.update { state ->
            state.copy(
                isBulkCloseMenuVisible = false,
                bulkCloseMenuBounds = null,
            )
        }
    }

    /** 表示中ページの未固定タブを退出アニメーション後に一括で閉じる。 */
    fun closeAllUnpinnedTabs(page: TabPage) {
        // メニューを先に閉じ、削除処理中も古いアンカーを表示し続けない。
        dismissBulkCloseMenu()
        when (page) {
            TabPage.BOARD -> {
                // --- Board removal ---
                val targets = tabSessionStore.openBoardTabs.value.filterNot(BoardTabInfo::isPinned)
                val keys = targets.map(BoardTabInfo::boardUrl).distinct()
                if (keys.isEmpty() || !addBoardRemovalKeys(keys)) return
                viewModelScope.launch {
                    delay(TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong())
                    tabSessionStore.closeAllUnpinnedTabs(page)
                }
            }

            TabPage.THREAD -> {
                // --- Thread removal ---
                val targets = tabSessionStore.openThreadTabs.value.filterNot(ThreadTabInfo::isPinned)
                val keys = targets.map { it.id.value }.distinct()
                if (keys.isEmpty() || !addThreadRemovalKeys(keys)) return
                viewModelScope.launch {
                    delay(TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong())
                    tabSessionStore.closeAllUnpinnedTabs(page)
                }
            }
        }
    }

    /** ページ変更時にタブ選択と一括クローズメニューを同時に解除する。 */
    fun onPageChanged() {
        cancelTabSelection()
        dismissBulkCloseMenu()
    }

    // --- BottomSheet ---

    fun dismissBoardInfoBottomSheet() {
        uiStateMutable.update { state ->
            state.copy(showBoardInfoBottomSheet = false)
        }
    }

    fun dismissThreadInfoBottomSheet() {
        uiStateMutable.update { state ->
            state.copy(showThreadInfoBottomSheet = false)
        }
    }

    // --- URL Dialog ---

    fun startUrlValidation() {
        uiStateMutable.update { state ->
            state.copy(isUrlValidating = true)
        }
    }

    fun finishUrlValidation() {
        uiStateMutable.update { state ->
            state.copy(isUrlValidating = false)
        }
    }

    fun setUrlDialogVisible(visible: Boolean) {
        uiStateMutable.update { state ->
            state.copy(
                showUrlDialog = visible,
                urlErrorMessage = if (visible) state.urlErrorMessage else null,
            )
        }
    }

    fun setUrlErrorMessage(message: String?) {
        uiStateMutable.update { state ->
            state.copy(urlErrorMessage = message)
        }
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
}
