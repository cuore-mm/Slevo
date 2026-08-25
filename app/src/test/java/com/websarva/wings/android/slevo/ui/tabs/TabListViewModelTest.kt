package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.component.TabListAnimationDefaults
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [TabListViewModel] の画面固有 UI 状態の管理を検証するテスト。
 */
class TabListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val tabSessionStore = mockk<TabSessionStore>(relaxed = true)
    private val openBoardTabs = MutableStateFlow<List<BoardTabInfo>>(emptyList())
    private val openThreadTabs = MutableStateFlow<List<ThreadTabInfo>>(emptyList())

    init {
        every { tabSessionStore.openBoardTabs } returns openBoardTabs
        every { tabSessionStore.openThreadTabs } returns openThreadTabs
    }

    private val viewModel by lazy { TabListViewModel(tabSessionStore) }

    /** その他メニューのアンカー位置を設定した状態を返す。 */
    private fun showBulkCloseMenu() {
        viewModel.showBulkCloseMenu(IntRect(10, 20, 110, 120))
    }

    // --- Search ---

    /**
     * 検索モード開始時に `isSearchMode` が true になり、既存の選択が解除されることを確認する。
     */
    @Test
    fun enterSearchMode_setsIsSearchModeTrue() = runTest {
        viewModel.onBoardTabLongPressed(
            BoardTabInfo(boardId = 1, boardName = "Test", boardUrl = "https://example.com/test/", serviceName = "example.com"),
            IntRect(0, 0, 100, 100),
        )
        assertTrue(viewModel.uiState.first().isInLongPressSelectionMode)

        viewModel.enterSearchMode()

        val state = viewModel.uiState.first()
        assertTrue(state.isSearchMode)
        assertFalse(state.isInLongPressSelectionMode)
        assertTrue(state.pendingSearchFocusRequestId != null)
    }

    /**
     * 検索モード終了時に `isSearchMode` が false になり、検索クエリが空になることを確認する。
     */
    @Test
    fun closeSearchMode_clearsSearchModeAndQuery() = runTest {
        viewModel.enterSearchMode()
        viewModel.updateSearchQuery("query", currentPage = 0)
        assertEquals("query", viewModel.uiState.first().searchQuery)

        viewModel.closeSearchMode()

        val state = viewModel.uiState.first()
        assertFalse(state.isSearchMode)
        assertEquals("", state.searchQuery)
        assertNull(state.pendingSearchFocusRequestId)
    }

    // --- Long-press selection ---

    /**
     * 板タブ長押し時に選択状態と bounds が設定されることを確認する。
     */
    @Test
    fun onBoardTabLongPressed_setsSelection() = runTest {
        val tab = BoardTabInfo(boardId = 1, boardName = "Test", boardUrl = "https://example.com/test/", serviceName = "example.com")
        val bounds = IntRect(10, 20, 110, 120)

        viewModel.onBoardTabLongPressed(tab, bounds)

        val state = viewModel.uiState.first()
        assertEquals(tab, state.selectedBoardTab)
        assertEquals(bounds, state.selectedTabBounds)
        assertTrue(state.isInLongPressSelectionMode)
    }

    /** Previewで止まったメニューを指離れでOpenへ進め、cancelで初期状態へ戻すことを確認する。 */
    @Test
    fun selectedTabMenu_transitionsFromPreviewToOpenAndBackToNone() = runTest {
        viewModel.onBoardTabLongPressed(
            BoardTabInfo(
                boardId = 1,
                boardName = "Test",
                boardUrl = "https://example.com/test/",
                serviceName = "example.com",
            ),
            IntRect(0, 0, 100, 100),
        )
        assertEquals(TabActionMenuMode.Preview, viewModel.uiState.first().tabActionMenuMode)

        viewModel.openSelectedTabMenu()
        assertEquals(TabActionMenuMode.Open, viewModel.uiState.first().tabActionMenuMode)

        viewModel.cancelTabSelection()
        assertEquals(TabActionMenuMode.None, viewModel.uiState.first().tabActionMenuMode)
    }

    /** draft移動は表示状態だけを更新し、drop時に一度だけ並び順をStoreへ渡すことを確認する。 */
    @Test
    fun boardReorder_updatesDraftUntilDrop() = runTest {
        val first = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        val second = BoardTabInfo(2, "B", "https://example.com/b/", "example.com")
        val third = BoardTabInfo(3, "C", "https://example.com/c/", "example.com")
        openBoardTabs.value = listOf(first, second, third)

        viewModel.startBoardReorder()
        viewModel.moveBoardReorder(first, third)

        assertEquals(
            listOf(first.boardUrl, second.boardUrl, third.boardUrl),
            viewModel.uiState.first().boardReorderDraft?.originalOrder,
        )
        assertEquals(
            listOf(second.boardUrl, third.boardUrl, first.boardUrl),
            viewModel.uiState.first().boardReorderDraft?.currentOrder,
        )
        verify(exactly = 0) { tabSessionStore.reorderBoardTabs(any()) }

        viewModel.finishBoardReorder()

        verify(exactly = 1) {
            tabSessionStore.reorderBoardTabs(
                listOf(second.boardUrl, third.boardUrl, first.boardUrl),
            )
        }
        assertNull(viewModel.uiState.first().boardReorderDraft)
    }

    /** cancelはStoreを呼ばず、board/thread双方の未確定draftを破棄することを確認する。 */
    @Test
    fun cancelReorder_discardsDraftWithoutPersisting() = runTest {
        val tab = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        openBoardTabs.value = listOf(tab)

        viewModel.startBoardReorder()
        viewModel.cancelReorder()

        assertNull(viewModel.uiState.first().boardReorderDraft)
        assertNull(viewModel.uiState.first().threadReorderDraft)
        verify(exactly = 0) { tabSessionStore.reorderBoardTabs(any()) }
        verify(exactly = 0) { tabSessionStore.reorderThreadTabs(any()) }
    }

    /** 長押し後のpointer cancelでPreview選択とアンカーも残さないことを確認する。 */
    @Test
    fun cancelReorder_clearsLongPressPreview() = runTest {
        val tab = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        viewModel.onBoardTabLongPressed(tab, IntRect(0, 0, 100, 100))

        viewModel.cancelReorder()

        val state = viewModel.uiState.first()
        assertNull(state.selectedBoardTab)
        assertNull(state.selectedTabBounds)
        assertEquals(TabActionMenuMode.None, state.tabActionMenuMode)
    }

    /** 上下移動は境界で失敗し、可能な移動だけ通常のreorder facadeへ渡すことを確認する。 */
    @Test
    fun accessibilityMove_ignoresBoundaryAndDelegatesValidMove() {
        val first = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        val second = BoardTabInfo(2, "B", "https://example.com/b/", "example.com")
        val third = BoardTabInfo(3, "C", "https://example.com/c/", "example.com")
        openBoardTabs.value = listOf(first, second, third)

        assertFalse(viewModel.moveBoardTabByOffset(first.boardUrl, -1))
        assertTrue(viewModel.moveBoardTabByOffset(second.boardUrl, -1))

        verify(exactly = 1) {
            tabSessionStore.reorderBoardTabs(listOf(second.boardUrl, first.boardUrl, third.boardUrl))
        }
    }

    /**
     * 選択解除時にすべての選択状態と BottomSheet フラグがクリアされることを確認する。
     */
    @Test
    fun cancelTabSelection_clearsSelection() = runTest {
        viewModel.onBoardTabLongPressed(
            BoardTabInfo(boardId = 1, boardName = "Test", boardUrl = "https://example.com/test/", serviceName = "example.com"),
            IntRect(0, 0, 100, 100),
        )
        viewModel.openSelectedTabDetail()
        assertTrue(viewModel.uiState.first().showBoardInfoBottomSheet)

        viewModel.cancelTabSelection()

        val state = viewModel.uiState.first()
        assertNull(state.selectedBoardTab)
        assertNull(state.selectedTabBounds)
        assertFalse(state.showBoardInfoBottomSheet)
    }

    /**
     * 詳細表示時に選択タブが detail 状態へ移行し、選択状態が解除されることを確認する。
     */
    @Test
    fun openSelectedTabDetail_movesSelectionToDetail() = runTest {
        val tab = BoardTabInfo(boardId = 1, boardName = "Test", boardUrl = "https://example.com/test/", serviceName = "example.com")
        viewModel.onBoardTabLongPressed(tab, IntRect(0, 0, 100, 100))

        viewModel.openSelectedTabDetail()

        val state = viewModel.uiState.first()
        assertNull(state.selectedBoardTab)
        assertEquals(tab, state.detailBoardTab)
        assertTrue(state.showBoardInfoBottomSheet)
    }

    // --- BottomSheet ---

    /**
     * BottomSheet 非表示操作でフラグが false になることを確認する。
     */
    @Test
    fun dismissBoardInfoBottomSheet_hidesSheet() = runTest {
        val tab = BoardTabInfo(boardId = 1, boardName = "Test", boardUrl = "https://example.com/test/", serviceName = "example.com")
        viewModel.onBoardTabLongPressed(tab, IntRect(0, 0, 100, 100))
        viewModel.openSelectedTabDetail()
        assertTrue(viewModel.uiState.first().showBoardInfoBottomSheet)

        viewModel.dismissBoardInfoBottomSheet()

        assertFalse(viewModel.uiState.first().showBoardInfoBottomSheet)
    }

    // --- Removal state ---

    /** 選択タブの削除リクエスト時に削除中keyが設定されることを確認する。 */
    @Test
    fun requestCloseSelectedTab_setsRemovingKey() = runTest {
        val tab = BoardTabInfo(boardId = 1, boardName = "Test", boardUrl = "https://example.com/test/", serviceName = "example.com")
        viewModel.onBoardTabLongPressed(tab, IntRect(0, 0, 100, 100))

        viewModel.requestCloseSelectedTab()

        val state = viewModel.uiState.first()
        assertEquals(setOf(tab.boardUrl), state.removingBoardTabKeys)
        assertFalse(state.isInLongPressSelectionMode)
    }

    /** 板タブの閉じる処理が退出時間後に一度だけStoreへ委譲されることを確認する。 */
    @Test
    fun startBoardTabRemoval_delegatesAfterRemovalDuration() = runTest {
        val tab = BoardTabInfo(
            boardId = 1,
            boardName = "Test",
            boardUrl = "https://example.com/test/",
            serviceName = "example.com",
        )
        every { tabSessionStore.closeBoardTab(tab) } answers {
            openBoardTabs.value = emptyList()
        }

        viewModel.startBoardTabRemoval(tab)
        verify(exactly = 0) { tabSessionStore.closeBoardTab(tab) }

        advanceTimeBy(TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong())
        runCurrent()

        verify(exactly = 1) { tabSessionStore.closeBoardTab(tab) }
    }

    /** 同じ板タブの削除要求を重ねてもStore呼び出しを増やさないことを確認する。 */
    @Test
    fun startBoardTabRemoval_ignoresDuplicateKey() = runTest {
        val tab = BoardTabInfo(
            boardId = 1,
            boardName = "Test",
            boardUrl = "https://example.com/test/",
            serviceName = "example.com",
        )
        every { tabSessionStore.closeBoardTab(tab) } answers {
            openBoardTabs.value = emptyList()
        }

        viewModel.startBoardTabRemoval(tab)
        viewModel.startBoardTabRemoval(tab)
        advanceTimeBy(TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong())
        runCurrent()

        verify(exactly = 1) { tabSessionStore.closeBoardTab(tab) }
    }

    /** スレッドタブの閉じる処理が退出時間後にretained APIへ一度だけ委譲されることを確認する。 */
    @Test
    fun startThreadTabRemoval_delegatesAfterRemovalDuration() = runTest {
        val tab = ThreadTabInfo(
            id = com.websarva.wings.android.slevo.data.model.ThreadId.of("example.com", "board", "1"),
            title = "Thread",
            boardName = "board",
            boardUrl = "https://example.com/board/",
            boardId = 1L,
        )
        every {
            tabSessionStore.requestCloseThreadTab(tab.threadKey, tab.boardUrl)
        } answers {
            openThreadTabs.value = emptyList()
        }

        viewModel.startThreadTabRemoval(tab)
        verify(exactly = 0) {
            tabSessionStore.requestCloseThreadTab(tab.threadKey, tab.boardUrl)
        }

        advanceTimeBy(TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong())
        runCurrent()

        verify(exactly = 1) {
            tabSessionStore.requestCloseThreadTab(tab.threadKey, tab.boardUrl)
        }
    }

    /** BoardとThreadで同じ文字列のkeyを使っても削除中状態を共有しないことを確認する。 */
    @Test
    fun removalKeys_areSeparatedByTabPage() = runTest {
        every { tabSessionStore.closeBoardTab(any()) } answers { }
        every { tabSessionStore.requestCloseThreadTab(any(), any()) } answers { }
        viewModel.startBoardTabRemoval(
            BoardTabInfo(
                boardId = 1,
                boardName = "Board",
                boardUrl = "same-key",
                serviceName = "example.com",
            ),
        )
        viewModel.startThreadTabRemoval(
            ThreadTabInfo(
                id = com.websarva.wings.android.slevo.data.model.ThreadId.of("example.com", "board", "same-key"),
                title = "Thread",
                boardName = "board",
                boardUrl = "https://example.com/board/",
                boardId = 1L,
            ),
        )

        val state = viewModel.uiState.first()
        assertEquals(setOf("same-key"), state.removingBoardTabKeys)
        assertEquals(setOf("example.com/board/same-key"), state.removingThreadTabKeys)
    }

    /** 削除対象が正本一覧から消えた後に削除中keyを消費できることを確認する。 */
    @Test
    fun clearRemovalKeys_clearsRemovingState() = runTest {
        val tab = BoardTabInfo(boardId = 1, boardName = "Test", boardUrl = "https://example.com/test/", serviceName = "example.com")
        viewModel.onBoardTabLongPressed(tab, IntRect(0, 0, 100, 100))
        viewModel.requestCloseSelectedTab()
        assertEquals(setOf(tab.boardUrl), viewModel.uiState.first().removingBoardTabKeys)

        viewModel.clearBoardRemovalKeys(setOf(tab.boardUrl))

        val state = viewModel.uiState.first()
        assertTrue(state.removingBoardTabKeys.isEmpty())
        assertTrue(state.removingThreadTabKeys.isEmpty())
    }

    // --- Bulk close menu ---

    /** 初期状態では一括クローズメニューが非表示でアンカーも存在しないことを確認する。 */
    @Test
    fun bulkCloseMenu_isHiddenInitially() = runTest {
        val state = viewModel.uiState.first()

        assertFalse(state.isBulkCloseMenuVisible)
        assertNull(state.bulkCloseMenuBounds)
    }

    /** その他メニューを開くと表示フラグとアンカーが同時に設定されることを確認する。 */
    @Test
    fun showBulkCloseMenu_setsVisibleAndAnchor() = runTest {
        showBulkCloseMenu()

        val state = viewModel.uiState.first()
        assertTrue(state.isBulkCloseMenuVisible)
        assertEquals(IntRect(10, 20, 110, 120), state.bulkCloseMenuBounds)
    }

    /** dismiss 時に一括クローズメニューの表示状態とアンカーがクリアされることを確認する。 */
    @Test
    fun dismissBulkCloseMenu_clearsVisibleAndAnchor() = runTest {
        showBulkCloseMenu()

        viewModel.dismissBulkCloseMenu()

        val state = viewModel.uiState.first()
        assertFalse(state.isBulkCloseMenuVisible)
        assertNull(state.bulkCloseMenuBounds)
    }

    /** ページ変更時に旧ページの一括クローズメニューを持ち越さないことを確認する。 */
    @Test
    fun onPageChanged_dismissesBulkCloseMenu() = runTest {
        showBulkCloseMenu()

        viewModel.onPageChanged()

        assertFalse(viewModel.uiState.first().isBulkCloseMenuVisible)
        assertNull(viewModel.uiState.first().bulkCloseMenuBounds)
    }

    /** 一括クローズ実行時にメニューを閉じ、対象スナップショットを Store へ渡すことを確認する。 */
    @Test
    fun closeAllUnpinnedTabs_dismissesMenuAndDelegatesPage() = runTest {
        val tabs = MutableStateFlow(
            listOf(
                ThreadTabInfo(
                    id = com.websarva.wings.android.slevo.data.model.ThreadId.of("example.com", "board", "1"),
                    title = "Thread",
                    boardName = "board",
                    boardUrl = "https://example.com/board/",
                    boardId = 1L,
                    isPinned = false,
                ),
            ),
        )
        val target = tabs.value.single()
        every { tabSessionStore.openThreadTabs } returns tabs
        every {
            tabSessionStore.closeThreadTabsAfterDelay(
                targets = listOf(target),
                delayMillis = TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong(),
            )
        } answers {
            tabs.value = emptyList()
        }
        showBulkCloseMenu()

        viewModel.closeAllUnpinnedTabs(TabPage.THREAD)

        val state = viewModel.uiState.first()
        assertFalse(state.isBulkCloseMenuVisible)
        assertNull(state.bulkCloseMenuBounds)
        assertTrue(state.removingThreadTabKeys.isNotEmpty())
        verify {
            tabSessionStore.closeThreadTabsAfterDelay(
                targets = listOf(target),
                delayMillis = TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong(),
            )
        }
        tabs.value = emptyList()
        runCurrent()
    }

    /** Board bulkも対象keyを同時に登録し、対象スナップショットをStoreへ一度だけ渡すことを確認する。 */
    @Test
    fun closeAllUnpinnedTabs_forBoard_passesSnapshotToStore() = runTest {
        val target = BoardTabInfo(
            boardId = 1,
            boardName = "Board",
            boardUrl = "https://example.com/board/",
            serviceName = "example.com",
        )
        openBoardTabs.value = listOf(target)
        every {
            tabSessionStore.closeBoardTabsAfterDelay(
                targets = listOf(target),
                delayMillis = TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong(),
            )
        } answers {
            openBoardTabs.value = emptyList()
        }

        viewModel.closeAllUnpinnedTabs(TabPage.BOARD)

        assertEquals(
            setOf("https://example.com/board/"),
            viewModel.uiState.first().removingBoardTabKeys,
        )
        verify {
            tabSessionStore.closeBoardTabsAfterDelay(
                targets = listOf(target),
                delayMillis = TabListAnimationDefaults.ITEM_REMOVAL_MILLIS.toLong(),
            )
        }
    }

    // --- URL Dialog ---

    /**
     * URLダイアログ表示時に `showUrlDialog` が true になることを確認する。
     */
    @Test
    fun setUrlDialogVisible_showsDialog() = runTest {
        viewModel.setUrlDialogVisible(true)
        assertTrue(viewModel.uiState.first().showUrlDialog)
    }

    /**
     * URLダイアログ非表示時にエラーメッセージもクリアされることを確認する。
     */
    @Test
    fun setUrlDialogVisible_hidesDialogAndClearsError() = runTest {
        viewModel.setUrlErrorMessage("error")
        viewModel.setUrlDialogVisible(false)

        val state = viewModel.uiState.first()
        assertFalse(state.showUrlDialog)
        assertNull(state.urlErrorMessage)
    }

    // --- URL open ---

    /**
     * 板URL入力が板遷移結果を返し、入力中フラグを終了することを確認する。
     */
    @Test
    fun openUrlInput_returnsNavigateBoardResult() = runTest {
        val route = AppRoute.Board(
            boardName = "https://agree.5ch.io/operate/",
            boardUrl = "https://agree.5ch.io/operate/",
        )
        coEvery { tabSessionStore.normalizeBoardRouteForNavigation(any()) } answers { firstArg() }

        val result = viewModel.openUrlInput(
            url = "https://agree.5ch.io/operate/",
            invalidUrlMessage = "invalid",
        )

        assertEquals(UrlOpenResult.NavigateBoard(route), result)
        assertFalse(viewModel.uiState.first().isUrlValidating)
    }

    /**
     * スレURL入力がスレ遷移結果を返し、入力中フラグを終了することを確認する。
     */
    @Test
    fun openUrlInput_returnsNavigateThreadResult() = runTest {
        val route = AppRoute.Thread(
            threadKey = "1234567890",
            boardUrl = "https://agree.5ch.io/operate/",
            boardName = "operate",
            threadTitle = null,
        )
        coEvery { tabSessionStore.normalizeThreadRouteForNavigation(any()) } answers { firstArg() }

        val result = viewModel.openUrlInput(
            url = "https://agree.5ch.io/test/read.cgi/operate/1234567890/",
            invalidUrlMessage = "invalid",
        )

        assertEquals(UrlOpenResult.NavigateThread(route), result)
        assertFalse(viewModel.uiState.first().isUrlValidating)
    }

    /**
     * 解決できないURL入力はエラー結果を返し、入力中フラグを終了することを確認する。
     */
    @Test
    fun openUrlInput_returnsErrorForUnknownUrl() = runTest {
        val result = viewModel.openUrlInput(
            url = "https://example.com/unknown/path/",
            invalidUrlMessage = "invalid",
        )

        assertEquals(UrlOpenResult.Error("invalid"), result)
        assertFalse(viewModel.uiState.first().isUrlValidating)
    }

    // --- Pin toggle ---

    /**
     * 選択中の板タブ固定切替時に [TabSessionStore.togglePinBoardTab] が呼ばれることを確認する。
     */
    @Test
    fun toggleSelectedTabPin_delegatesToSessionStore() {
        val tab = BoardTabInfo(boardId = 1, boardName = "Test", boardUrl = "https://example.com/test/", serviceName = "example.com")
        viewModel.onBoardTabLongPressed(tab, IntRect(0, 0, 100, 100))

        viewModel.toggleSelectedTabPin()

        verify { tabSessionStore.togglePinBoardTab("https://example.com/test/") }
    }

    /**
     * 選択中のスレッドタブ固定切替時に [TabSessionStore.togglePinThreadTab] が呼ばれることを確認する。
     */
    @Test
    fun toggleSelectedTabPin_forThread_delegatesToSessionStore() = runTest {
        val tab = ThreadTabInfo(
            id = com.websarva.wings.android.slevo.data.model.ThreadId.of("medaka.5ch.io", "test", "1234567890"),
            title = "Test Thread",
            boardName = "Test Board",
            boardUrl = "https://medaka.5ch.io/test/",
            resCount = 10,
            boardId = 1,
        )
        viewModel.onThreadTabLongPressed(tab, IntRect(0, 0, 100, 100))

        viewModel.toggleSelectedTabPin()

        coVerify { tabSessionStore.togglePinThreadTab(tab.id) }
    }

    // --- Search ---

    /**
     * 検索入力更新時に文字列と selection が保持されることを確認する。
     */
    @Test
    fun updateSearchInput_preservesSelection() = runTest {
        viewModel.updateSearchInput(
            TextFieldValue(text = "query", selection = TextRange(2, 4)),
            currentPage = 0,
        )

        val state = viewModel.uiState.first()
        assertEquals("query", state.searchQuery)
        assertEquals(TextRange(2, 4), state.searchInputValue.selection)
    }

    /**
     * 検索開始時に現在表示中ページの先頭表示要求が発行されることを確認する。
     */
    @Test
    fun updateSearchQuery_blankToNonBlank_setsPendingScrollToTopRequest() = runTest {
        viewModel.updateSearchQuery("query", currentPage = 1)

        val state = viewModel.uiState.first()
        assertEquals("query", state.searchQuery)
        assertEquals(TabListScrollToTopRequest(page = 1, query = "query"), state.pendingScrollToTopRequest)
    }

    /**
     * 検索クエリが非空から空へ変わると、通常リスト復元要求を発行せず先頭表示要求だけをクリアすることを確認する。
     */
    @Test
    fun updateSearchQuery_nonBlankToBlank_clearsPendingScrollToTopRequest() = runTest {
        viewModel.updateSearchQuery("query", currentPage = 0)
        assertEquals("query", viewModel.uiState.first().searchQuery)

        viewModel.updateSearchQuery("", currentPage = 0)

        val state = viewModel.uiState.first()
        assertEquals("", state.searchQuery)
        assertNull(state.pendingScrollToTopRequest)
    }

    /**
     * 検索クエリが非空から別の非空へ変わると、新しいクエリ向けの先頭表示要求が発行されることを確認する。
     */
    @Test
    fun updateSearchQuery_nonBlankToDifferentNonBlank_setsPendingScrollToTopRequest() = runTest {
        viewModel.updateSearchQuery("old", currentPage = 0)
        assertEquals("old", viewModel.uiState.first().searchQuery)

        viewModel.updateSearchQuery("new", currentPage = 1)

        val state = viewModel.uiState.first()
        assertEquals("new", state.searchQuery)
        assertEquals(TabListScrollToTopRequest(page = 1, query = "new"), state.pendingScrollToTopRequest)
    }

    /**
     * closeSearchMode で検索状態がクリアされ、未消費の先頭表示要求も破棄されることを確認する。
     */
    @Test
    fun closeSearchMode_clearsSearchAndPendingScrollRequest() = runTest {
        viewModel.enterSearchMode()
        viewModel.updateSearchQuery("query", currentPage = 0)

        viewModel.closeSearchMode()

        val state = viewModel.uiState.first()
        assertFalse(state.isSearchMode)
        assertEquals("", state.searchQuery)
        assertNull(state.pendingScrollToTopRequest)
        assertNull(state.pendingSearchFocusRequestId)
    }

    /**
     * 検索終了時に関連する検索 UI 状態が単一の state としてまとめてクリアされることを確認する。
     */
    @Test
    fun closeSearchMode_clearsRelatedSearchFieldsTogether() = runTest {
        viewModel.enterSearchMode()
        viewModel.updateSearchInput(
            TextFieldValue(text = "query", selection = TextRange(1, 3)),
            currentPage = 1,
        )

        viewModel.closeSearchMode()

        val state = viewModel.uiState.first()
        assertFalse(state.isSearchMode)
        assertEquals(TextFieldValue(""), state.searchInputValue)
        assertNull(state.pendingSearchFocusRequestId)
        assertNull(state.pendingScrollToTopRequest)
    }

    /**
     * resetSearchState で検索モード・検索クエリ・未消費の先頭表示要求がすべてクリアされることを確認する。
     */
    @Test
    fun resetSearchState_clearsAllSearchState() = runTest {
        viewModel.enterSearchMode()
        viewModel.updateSearchQuery("query", currentPage = 0)
        openBoardTabs.value = listOf(
            BoardTabInfo(1, "A", "https://example.com/a/", "example.com"),
        )
        viewModel.closeSearchMode()
        viewModel.startBoardReorder()

        viewModel.resetSearchState()

        val state = viewModel.uiState.first()
        assertFalse(state.isSearchMode)
        assertEquals("", state.searchQuery)
        assertNull(state.pendingScrollToTopRequest)
        assertNull(state.pendingSearchFocusRequestId)
        assertNull(state.boardReorderDraft)
        assertEquals(TabActionMenuMode.None, state.tabActionMenuMode)
    }

    /**
     * 先頭表示要求を consume すると、同じ要求が再発行されないことを確認する。
     */
    @Test
    fun consumePendingScrollToTopRequest_clearsPendingRequest() = runTest {
        viewModel.updateSearchQuery("query", currentPage = 0)

        val state = viewModel.uiState.first()
        assertEquals(TabListScrollToTopRequest(page = 0, query = "query"), state.pendingScrollToTopRequest)

        viewModel.consumePendingScrollToTopRequest()
        assertNull(viewModel.uiState.first().pendingScrollToTopRequest)
    }

    /**
     * 検索バー自動フォーカス要求を consume すると、戻る操作相当の再Compositionでは再発行されないことを確認する。
     */
    @Test
    fun consumePendingSearchFocusRequest_clearsPendingRequest() = runTest {
        viewModel.enterSearchMode()
        assertTrue(viewModel.uiState.first().pendingSearchFocusRequestId != null)

        viewModel.consumePendingSearchFocusRequest()

        assertNull(viewModel.uiState.first().pendingSearchFocusRequestId)
    }
}
