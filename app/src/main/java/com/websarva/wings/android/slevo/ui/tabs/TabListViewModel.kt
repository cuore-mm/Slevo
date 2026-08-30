package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.component.TabListAnimationDefaults
import com.websarva.wings.android.slevo.ui.tabs.component.logTabReorder
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
        cancelReorder()
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
        cancelReorder()
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
     * 画面が破棄されても残る ViewModel の未確定 reorder draft と長押し選択も同時に破棄する。
     */
    fun resetSearchState() {
        uiStateMutable.update { state ->
            state.copy(
                isSearchMode = false,
                searchInputValue = TextFieldValue(""),
                pendingSearchFocusRequestId = null,
                pendingScrollToTopRequest = null,
                boardReorderDraft = null,
                threadReorderDraft = null,
                selectedBoardTab = null,
                selectedThreadTab = null,
                selectedTabBounds = null,
                tabActionMenuMode = TabActionMenuMode.None,
                selectionModePage = null,
                selectedBoardTabKeys = emptySet(),
                selectedThreadTabIds = emptySet(),
            )
        }
    }

    // --- Long-press selection ---

    fun onBoardTabLongPressed(tab: BoardTabInfo, bounds: IntRect) {
        if (uiState.value.isInSelectionMode) return
        logTabReorder { "BOARD_LONG_PRESS_VM key=${tab.boardUrl}" }
        cancelTabSelection()
        uiStateMutable.update { state ->
            state.copy(
                selectedBoardTab = tab,
                selectedThreadTab = null,
                selectedTabBounds = bounds,
                tabActionMenuMode = if (state.isSearchMode) TabActionMenuMode.Open else TabActionMenuMode.Preview,
            )
        }
    }

    fun onThreadTabLongPressed(tab: ThreadTabInfo, bounds: IntRect) {
        if (uiState.value.isInSelectionMode) return
        logTabReorder { "THREAD_LONG_PRESS_VM key=${tab.id.value}" }
        cancelTabSelection()
        uiStateMutable.update { state ->
            state.copy(
                selectedBoardTab = null,
                selectedThreadTab = tab,
                selectedTabBounds = bounds,
                tabActionMenuMode = if (state.isSearchMode) TabActionMenuMode.Open else TabActionMenuMode.Preview,
            )
        }
    }

    /** 長押し後に指を離した場合、タブ操作メニューを操作可能にする。 */
    fun openSelectedTabMenu() {
        if (!uiState.value.isInLongPressSelectionMode) return
        uiStateMutable.update { state -> state.copy(tabActionMenuMode = TabActionMenuMode.Open) }
    }

    fun cancelTabSelection() {
        uiStateMutable.update { state ->
            state.copy(
                selectedBoardTab = null,
                selectedThreadTab = null,
                selectedTabBounds = null,
                tabActionMenuMode = TabActionMenuMode.None,
                showBoardInfoBottomSheet = false,
                showThreadInfoBottomSheet = false,
            )
        }
    }

    /** 選択モードを表示中ページで開始し、必要なら起点タブを選択済みにする。 */
    fun startSelectionMode(page: TabPage, initialBoardUrl: String? = null, initialThreadId: ThreadId? = null) {
        cancelTabSelection()
        dismissBulkCloseMenu()
        cancelReorder()
        uiStateMutable.update { state ->
            state.copy(
                selectionModePage = page,
                selectedBoardTabKeys = if (page == TabPage.BOARD && initialBoardUrl != null) {
                    setOf(initialBoardUrl)
                } else emptySet(),
                selectedThreadTabIds = if (page == TabPage.THREAD && initialThreadId != null) {
                    setOf(initialThreadId)
                } else emptySet(),
            )
        }
    }

    /** 選択モードを終了し、選択集合と選択メニューを破棄する。 */
    fun exitSelectionMode() {
        uiStateMutable.update { state ->
            state.copy(
                selectionModePage = null,
                selectedBoardTabKeys = emptySet(),
                selectedThreadTabIds = emptySet(),
                isBulkCloseMenuVisible = false,
                bulkCloseMenuBounds = null,
            )
        }
    }

    /** 板タブの選択状態をstable key単位で切り替える。 */
    fun toggleBoardTabSelection(boardUrl: String) {
        if (uiState.value.selectionModePage != TabPage.BOARD) return
        uiStateMutable.update { state ->
            val keys = state.selectedBoardTabKeys
            state.copy(selectedBoardTabKeys = if (boardUrl in keys) keys - boardUrl else keys + boardUrl)
        }
    }

    /** スレッドタブの選択状態をstable key単位で切り替える。 */
    fun toggleThreadTabSelection(threadId: ThreadId) {
        if (uiState.value.selectionModePage != TabPage.THREAD) return
        uiStateMutable.update { state ->
            val ids = state.selectedThreadTabIds
            state.copy(selectedThreadTabIds = if (threadId in ids) ids - threadId else ids + threadId)
        }
    }

    /** canonical一覧に存在しない選択keyだけを除去する。 */
    fun pruneSelection(boardUrls: Set<String>, threadIds: Set<ThreadId>) {
        uiStateMutable.update { state ->
            state.copy(
                selectedBoardTabKeys = state.selectedBoardTabKeys.intersect(boardUrls),
                selectedThreadTabIds = state.selectedThreadTabIds.intersect(threadIds),
            )
        }
    }

    /** 板タブの並び替えを開始し、現在のstable key順をdraftへ保存する。 */
    fun startBoardReorder() {
        val keys = tabSessionStore.openBoardTabs.value.map(BoardTabInfo::boardUrl)
        val isSearchMode = uiState.value.isSearchMode
        val isSelectionMode = uiState.value.isInSelectionMode
        if (keys.isEmpty() || isSearchMode || isSelectionMode) {
            logTabReorder {
                "BOARD_REORDER_START_REJECT keyCount=${keys.size} isSearchMode=$isSearchMode"
            }
            return
        }
        logTabReorder { "BOARD_REORDER_START keyCount=${keys.size}" }
        uiStateMutable.update { state ->
            state.copy(
                boardReorderDraft = ReorderDraft(keys, keys),
                selectedBoardTab = null,
                selectedThreadTab = null,
                selectedTabBounds = null,
                tabActionMenuMode = TabActionMenuMode.None,
                showBoardInfoBottomSheet = false,
                showThreadInfoBottomSheet = false,
            )
        }
    }

    /** スレッドタブの並び替えを開始し、現在のstable key順をdraftへ保存する。 */
    fun startThreadReorder() {
        val keys = tabSessionStore.openThreadTabs.value.map { it.id.value }
        val isSearchMode = uiState.value.isSearchMode
        val isSelectionMode = uiState.value.isInSelectionMode
        if (keys.isEmpty() || isSearchMode || isSelectionMode) {
            logTabReorder {
                "THREAD_REORDER_START_REJECT keyCount=${keys.size} isSearchMode=$isSearchMode"
            }
            return
        }
        logTabReorder { "THREAD_REORDER_START keyCount=${keys.size}" }
        uiStateMutable.update { state ->
            state.copy(
                threadReorderDraft = ReorderDraft(keys, keys),
                selectedBoardTab = null,
                selectedThreadTab = null,
                selectedTabBounds = null,
                tabActionMenuMode = TabActionMenuMode.None,
                showBoardInfoBottomSheet = false,
                showThreadInfoBottomSheet = false,
            )
        }
    }

    /** 板タブの移動イベントをdraftへ反映し、永続化はドロップまで遅延する。 */
    fun moveBoardReorder(from: BoardTabInfo, to: BoardTabInfo) {
        logTabReorder { "BOARD_DRAFT_MOVE from=${from.boardUrl} to=${to.boardUrl}" }
        uiStateMutable.update { state ->
            val draft = state.boardReorderDraft ?: return@update state
            state.copy(
                boardReorderDraft = draft.copy(
                    currentOrder = moveKeyBeforeTarget(
                        draft.currentOrder,
                        from.boardUrl,
                        to.boardUrl,
                    )
                )
            )
        }
    }

    /** スレッドタブの移動イベントをdraftへ反映し、永続化はドロップまで遅延する。 */
    fun moveThreadReorder(from: ThreadTabInfo, to: ThreadTabInfo) {
        logTabReorder { "THREAD_DRAFT_MOVE from=${from.id.value} to=${to.id.value}" }
        uiStateMutable.update { state ->
            val draft = state.threadReorderDraft ?: return@update state
            state.copy(
                threadReorderDraft = draft.copy(
                    currentOrder = moveKeyBeforeTarget(
                        draft.currentOrder,
                        from.id.value,
                        to.id.value,
                    )
                )
            )
        }
    }

    /** 板タブのドロップをCoordinatorへ渡し、draftを破棄する。 */
    fun finishBoardReorder() {
        val draft = uiState.value.boardReorderDraft
        if (draft == null) {
            logTabReorder { "BOARD_DRAFT_FINISH_NO_DRAFT" }
            return
        }
        val accepted = tabSessionStore.reorderBoardTabs(draft.currentOrder)
        logTabReorder {
            "BOARD_DRAFT_FINISH accepted=$accepted keyCount=${draft.currentOrder.size}"
        }
        uiStateMutable.update { it.copy(boardReorderDraft = null) }
    }

    /** スレッドタブのドロップをCoordinatorへ渡し、draftを破棄する。 */
    fun finishThreadReorder() {
        val draft = uiState.value.threadReorderDraft
        if (draft == null) {
            logTabReorder { "THREAD_DRAFT_FINISH_NO_DRAFT" }
            return
        }
        val accepted = tabSessionStore.reorderThreadTabs(draft.currentOrder)
        logTabReorder {
            "THREAD_DRAFT_FINISH accepted=$accepted keyCount=${draft.currentOrder.size}"
        }
        uiStateMutable.update { it.copy(threadReorderDraft = null) }
    }

    /** pointer cancel または画面終了時に未確定の順序と長押しプレビューを破棄する。 */
    fun cancelReorder() {
        logTabReorder {
            "DRAFT_CANCEL board=${uiState.value.boardReorderDraft != null} " +
                "thread=${uiState.value.threadReorderDraft != null}"
        }
        uiStateMutable.update { state ->
            state.copy(
                boardReorderDraft = null,
                threadReorderDraft = null,
                selectedBoardTab = null,
                selectedThreadTab = null,
                selectedTabBounds = null,
                tabActionMenuMode = TabActionMenuMode.None,
            )
        }
    }

    /** アクセシビリティ操作で板タブを隣接位置へ移動する。 */
    fun moveBoardTabByOffset(boardUrl: String, offset: Int): Boolean {
        val keys = tabSessionStore.openBoardTabs.value.map(BoardTabInfo::boardUrl).toMutableList()
        val index = keys.indexOf(boardUrl)
        val target = index + offset
        if (index < 0 || target !in keys.indices) return false
        keys.removeAt(index)
        keys.add(target, boardUrl)
        tabSessionStore.reorderBoardTabs(keys)
        return true
    }

    /** アクセシビリティ操作でスレッドタブを隣接位置へ移動する。 */
    fun moveThreadTabByOffset(threadId: String, offset: Int): Boolean {
        val keys = tabSessionStore.openThreadTabs.value.map { it.id.value }.toMutableList()
        val index = keys.indexOf(threadId)
        val target = index + offset
        if (index < 0 || target !in keys.indices) return false
        keys.removeAt(index)
        keys.add(target, threadId)
        tabSessionStore.reorderThreadTabs(keys)
        return true
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

    /** 選択中タブを一覧順のsnapshotとして一括固定または固定解除する。 */
    fun setSelectedTabsPinned(page: TabPage) {
        dismissBulkCloseMenu()
        when (page) {
            TabPage.BOARD -> {
                val targets = tabSessionStore.openBoardTabs.value.filter {
                    it.boardUrl in uiState.value.selectedBoardTabKeys
                }
                if (targets.isEmpty()) return
                val shouldPin = targets.any { !it.isPinned }
                tabSessionStore.setBoardTabsPinned(targets, shouldPin)
            }

            TabPage.THREAD -> {
                val targets = tabSessionStore.openThreadTabs.value.filter {
                    it.id in uiState.value.selectedThreadTabIds
                }
                if (targets.isEmpty()) return
                val shouldPin = targets.any { !it.isPinned }
                viewModelScope.launch { tabSessionStore.setThreadTabsPinned(targets, shouldPin) }
            }
        }
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

    /** 選択中タブを一覧順のsnapshotとして退出アニメーション後に一括で閉じる。 */
    fun closeSelectedTabs(page: TabPage) {
        dismissBulkCloseMenu()
        when (page) {
            TabPage.BOARD -> {
                val targets = tabSessionStore.openBoardTabs.value.filter {
                    it.boardUrl in uiState.value.selectedBoardTabKeys
                }
                val keys = targets.map(BoardTabInfo::boardUrl).distinct()
                if (keys.isEmpty() || !addBoardRemovalKeys(keys)) return
                tabSessionStore.closeBoardTabsAfterDelay(
                    targets = targets,
                    delayMillis = TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong(),
                )
            }

            TabPage.THREAD -> {
                val targets = tabSessionStore.openThreadTabs.value.filter {
                    it.id in uiState.value.selectedThreadTabIds
                }
                val keys = targets.map { it.id.value }.distinct()
                if (keys.isEmpty() || !addThreadRemovalKeys(keys)) return
                tabSessionStore.closeThreadTabsAfterDelay(
                    targets = targets,
                    delayMillis = TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong(),
                )
            }
        }
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
        if (uiState.value.isInSelectionMode && uiState.value.selectedTabCount == 0) return
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
                tabSessionStore.closeBoardTabsAfterDelay(
                    targets = targets,
                    delayMillis = TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong(),
                )
            }

            TabPage.THREAD -> {
                // --- Thread removal ---
                val targets = tabSessionStore.openThreadTabs.value.filterNot(ThreadTabInfo::isPinned)
                val keys = targets.map { it.id.value }.distinct()
                if (keys.isEmpty() || !addThreadRemovalKeys(keys)) return
                tabSessionStore.closeThreadTabsAfterDelay(
                    targets = targets,
                    delayMillis = TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong(),
                )
            }
        }
    }

    /** ページ変更時にタブ選択と一括クローズメニューを同時に解除する。 */
    fun onPageChanged(page: Int? = null) {
        cancelTabSelection()
        cancelReorder()
        dismissBulkCloseMenu()
        val selectionPage = uiState.value.selectionModePage
        if (selectionPage != null && page != null && selectionPage.index != page) {
            exitSelectionMode()
        }
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
