package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.ui.unit.IntRect
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
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
    private val viewModel by lazy { TabListViewModel(tabSessionStore) }

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

    // --- Pending close ---

    /**
     * 選択タブの削除リクエスト時に pendingClose 状態が設定されることを確認する。
     */
    @Test
    fun requestCloseSelectedTab_setsPendingClose() = runTest {
        val tab = BoardTabInfo(boardId = 1, boardName = "Test", boardUrl = "https://example.com/test/", serviceName = "example.com")
        viewModel.onBoardTabLongPressed(tab, IntRect(0, 0, 100, 100))

        viewModel.requestCloseSelectedTab()

        val state = viewModel.uiState.first()
        assertEquals(tab, state.pendingCloseBoardTab)
        assertFalse(state.isInLongPressSelectionMode)
    }

    /**
     * consumePendingCloseRequest で pendingClose 状態がクリアされることを確認する。
     */
    @Test
    fun consumePendingCloseRequest_clearsPending() = runTest {
        val tab = BoardTabInfo(boardId = 1, boardName = "Test", boardUrl = "https://example.com/test/", serviceName = "example.com")
        viewModel.onBoardTabLongPressed(tab, IntRect(0, 0, 100, 100))
        viewModel.requestCloseSelectedTab()
        assertEquals(tab, viewModel.uiState.first().pendingCloseBoardTab)

        viewModel.consumePendingCloseRequest()

        val state = viewModel.uiState.first()
        assertNull(state.pendingCloseBoardTab)
        assertNull(state.pendingCloseThreadTab)
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
    fun toggleSelectedTabPin_forThread_delegatesToSessionStore() {
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

        verify { tabSessionStore.togglePinThreadTab(tab.id) }
    }

    // --- Search ---

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
    }

    /**
     * resetSearchState で検索モード・検索クエリ・未消費の先頭表示要求がすべてクリアされることを確認する。
     */
    @Test
    fun resetSearchState_clearsAllSearchState() = runTest {
        viewModel.enterSearchMode()
        viewModel.updateSearchQuery("query", currentPage = 0)

        viewModel.resetSearchState()

        val state = viewModel.uiState.first()
        assertFalse(state.isSearchMode)
        assertEquals("", state.searchQuery)
        assertNull(state.pendingScrollToTopRequest)
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
}
