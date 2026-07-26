package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.TabMutationResult
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.coordinator.BoardTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.controller.TabCommandResult
import com.websarva.wings.android.slevo.ui.bbsroute.TabSelectionResolution
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.session.BoardSessionState
import io.mockk.coVerify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `BoardTabsCoordinator` のタブ管理と固定切替を検証するテスト。
 */
class BoardTabsCoordinatorTest {

    /**
     * `togglePinBoardTab` で対象板タブの固定状態を切り替えることを確認する。
     */
    @Test
    fun togglePinBoardTab_togglesPinnedState() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.openBoardTab(
            BoardTabInfo(
                boardId = 1,
                boardName = "Test Board",
                boardUrl = "https://example.com/test/",
                serviceName = "example.com",
            )
        )
        val boardUrl = coordinator.openBoardTabs.value.first().boardUrl

        assertEquals(false, coordinator.openBoardTabs.value.first().isPinned)

        coordinator.togglePinBoardTab(boardUrl)

        assertEquals(true, coordinator.openBoardTabs.value.first().isPinned)

        coordinator.togglePinBoardTab(boardUrl)

        assertEquals(false, coordinator.openBoardTabs.value.first().isPinned)
    }

    /**
     * 既存の板タブを上書きした場合でも固定状態が維持されることを確認する。
     */
    @Test
    fun openBoardTab_preservesPinnedStateOnUpsert() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val originalTab = BoardTabInfo(
            boardId = 1,
            boardName = "Test Board",
            boardUrl = "https://example.com/test/",
            serviceName = "example.com",
            isPinned = true,
        )
        coordinator.openBoardTab(originalTab)

        val updatedTab = originalTab.copy(
            boardName = "Updated Board",
            isPinned = false,
        )
        coordinator.openBoardTab(updatedTab)

        val actual = coordinator.openBoardTabs.value.first()
        assertEquals(true, actual.isPinned)
        assertEquals("Updated Board", actual.boardName)
    }

    /**
     * 板タブ選択時に selected key が boardUrl で更新されることを確認する。
     */
    @Test
    fun selectBoardTab_updatesSelectedBoardTabKey() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val tab = BoardTabInfo(
            boardId = 1,
            boardName = "Test Board",
            boardUrl = "https://example.com/test/",
            serviceName = "example.com",
        )
        coordinator.openBoardTab(tab)

        coordinator.selectBoardTab(tab.boardUrl)

        assertEquals(tab.boardUrl, coordinator.selectedBoardTabKey.value)
        assertEquals(
            TabSelectionResolution.Selected(tab.boardUrl),
            coordinator.boardPresentationState.value.selection,
        )
    }

    /** 非空一覧を初めて公開したとき null 選択を先頭へ補正することを確認する。 */
    @Test
    fun firstLoadedBoardTabs_repairsNullSelectionAtomically() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val first = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        val second = BoardTabInfo(2, "B", "https://example.com/b/", "example.com")

        coordinator.openBoardTab(first)
        coordinator.openBoardTab(second)

        assertEquals(first.boardUrl, coordinator.selectedBoardTabKey.value)
        assertEquals(listOf(first, second), coordinator.boardPresentationState.value.tabs)
        assertEquals(
            TabSelectionResolution.Selected(first.boardUrl),
            coordinator.boardPresentationState.value.selection,
        )
    }

    /** 1,252 Board rowsへ100 rapid commandを適用しても key 一意性と安定順序を維持する。 */
    @Test
    fun largeBoardSnapshotAndRapidCommandsPreserveOrderWithoutBulkPersistence() {
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val coordinator = createCoordinator(tabsRepository)
        val initialTabs = (0 until 1_252).map { index -> testBoardTab("large-$index") }

        initialTabs.forEach(coordinator::openBoardTab)
        repeat(100) { coordinator.togglePinBoardTab(initialTabs.first().boardUrl) }

        assertEquals(initialTabs.map { it.boardUrl }, coordinator.openBoardTabs.value.map { it.boardUrl })
        assertEquals(1_252, coordinator.openBoardTabs.value.size)
        assertEquals(
            coordinator.openBoardTabs.value.size,
            coordinator.openBoardTabs.value.map { it.boardUrl }.toSet().size,
        )
        assertEquals(initialTabs.first().isPinned, coordinator.openBoardTabs.value.first().isPinned)
        coVerify(exactly = 0) { tabsRepository.saveOpenBoardTabs(any()) }
    }

    /** 最後の tab close は selected key と presentation state を同時に空へ遷移させる。 */
    @Test
    fun closeLastBoardTab_publishesEmptyPresentationState() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val tab = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        coordinator.openBoardTab(tab)
        coordinator.selectBoardTab(tab.boardUrl)

        coordinator.closeBoardTab(tab)

        assertEquals(emptyList<BoardTabInfo>(), coordinator.boardPresentationState.value.tabs)
        assertEquals(TabSelectionResolution.Empty, coordinator.boardPresentationState.value.selection)
        assertNull(coordinator.selectedBoardTabKey.value)
    }

    /** 非選択 tab close は現在の有効な key を維持する。 */
    @Test
    fun closeUnselectedBoardTab_keepsSelectedKeyInPresentationState() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val first = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        val second = BoardTabInfo(2, "B", "https://example.com/b/", "example.com")
        coordinator.openBoardTab(first)
        coordinator.openBoardTab(second)
        coordinator.selectBoardTab(second.boardUrl)

        coordinator.closeBoardTab(first)

        assertEquals(second.boardUrl, coordinator.boardPresentationState.value.tabs.single().boardUrl)
        assertEquals(TabSelectionResolution.Selected(second.boardUrl), coordinator.boardPresentationState.value.selection)
    }

    /**
     * 選択中タブを閉じた場合、selected key が隣接タブへ補正されることを確認する。
     */
    @Test
    fun closeBoardTab_updatesSelectedKeyToAdjacentTab() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val first = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        val second = BoardTabInfo(2, "B", "https://example.com/b/", "example.com")
        coordinator.openBoardTab(first)
        coordinator.openBoardTab(second)
        coordinator.selectBoardTab(first.boardUrl)

        coordinator.closeBoardTab(first)

        assertEquals(second.boardUrl, coordinator.selectedBoardTabKey.value)
    }

    /**
     * 最後の板タブを閉じた場合、selected key が null になることを確認する。
     */
    @Test
    fun closeLastBoardTab_clearsSelectedBoardTabKey() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val tab = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        coordinator.openBoardTab(tab)
        coordinator.selectBoardTab(tab.boardUrl)

        coordinator.closeBoardTab(tab)

        assertNull(coordinator.selectedBoardTabKey.value)
    }

    /**
     * タブを閉じたときに対象板タブのセッション状態だけが削除されることを確認する。
     */
    @Test
    fun closeBoardTab_removesOnlyTargetSessionState() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val first = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        val second = BoardTabInfo(2, "B", "https://example.com/b/", "example.com")
        coordinator.openBoardTab(first)
        coordinator.openBoardTab(second)
        coordinator.updateBoardSessionState(first.boardUrl) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("first"))
        }
        coordinator.updateBoardSessionState(second.boardUrl) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("second"))
        }

        coordinator.closeBoardTab(first)

        assertFalse(coordinator.boardSessionStates.value.containsKey(first.boardUrl))
        assertEquals("second", coordinator.getBoardSessionState(second.boardUrl).searchQuery)
    }

    /** Board targeted write の失敗が presentation 待ちではなく terminal failure になることを確認する。 */
    @Test
    fun ensureBoardTabCommand_repositoryFailureCompletesWithoutNavigationState() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenBoardTab(any()) } returns
            TabMutationResult.Failure(IllegalStateException("write failed"))
        databaseFlow.emit(emptyList())

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()

        val command = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.ensureBoardTabCommand(testBoardRoute())
        }
        runCurrent()
        val result = command.await()

        assertTrue(result is TabCommandResult.Failure)
        assertEquals("write failed", (result as TabCommandResult.Failure).cause.message)
        assertTrue(coordinator.openBoardTabs.value.isEmpty())
        coordinator.close()
    }

    /** write barrier と canonical emission を別々に進めても、確認前の pending projection を保持する。 */
    @Test
    fun boardFixture_separatesRepositoryWriteFromCanonicalConfirmation() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val writeStarted = CompletableDeferred<Unit>()
        val writeRelease = CompletableDeferred<Unit>()
        val target = testBoardTab("target")
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenBoardTab(any()) } coAnswers {
            writeStarted.complete(Unit)
            writeRelease.await()
            TabMutationResult.Success
        }
        databaseFlow.emit(emptyList())

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        val command = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.ensureBoardTabCommand(testBoardRoute("target"))
        }
        runCurrent()

        assertTrue(writeStarted.isCompleted)
        assertFalse(command.isCompleted)
        assertEquals(listOf(target), coordinator.openBoardTabs.value)

        writeRelease.complete(Unit)
        runCurrent()
        assertFalse(command.isCompleted)

        databaseFlow.emit(listOf(target))
        runCurrent()
        assertTrue(command.await() is TabCommandResult.Success)
        coVerify(exactly = 0) { tabsRepository.saveOpenBoardTabs(any()) }
        coordinator.close()
    }

    /** bound Controller の close が選択修復と session cleanup を canonical 確認後に完了することを確認する。 */
    @Test
    fun boundClose_repairsSelectionAndCleansSessionAfterCanonicalDeletion() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val first = testBoardTab("first")
        val second = testBoardTab("second")
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenBoardTab(first.boardUrl) } returns TabMutationResult.Success
        databaseFlow.emit(listOf(first, second))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        coordinator.selectBoardTab(first.boardUrl)
        coordinator.updateBoardSessionState(first.boardUrl) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("retained"))
        }

        coordinator.closeBoardTab(first)
        runCurrent()
        assertEquals(second.boardUrl, coordinator.selectedBoardTabKey.value)
        assertEquals(listOf(second), coordinator.openBoardTabs.value)
        assertEquals("retained", coordinator.getBoardSessionState(first.boardUrl).searchQuery)

        databaseFlow.emit(listOf(second))
        runCurrent()
        assertEquals(BoardSessionState(), coordinator.getBoardSessionState(first.boardUrl))
        assertEquals(listOf(second), coordinator.openBoardTabs.value)
        coordinator.close()
    }

    /** bound mode の中央 tab close が削除前 index の次の tab を選択することを確認する。 */
    @Test
    fun boundClose_middleSelectedTab_selectsTabAtRemovedIndex() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val first = testBoardTab("first")
        val middle = testBoardTab("middle")
        val last = testBoardTab("last")
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenBoardTab(middle.boardUrl) } returns TabMutationResult.Success
        databaseFlow.emit(listOf(first, middle, last))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        coordinator.selectBoardTab(middle.boardUrl)

        coordinator.closeBoardTab(middle)
        runCurrent()
        assertEquals(last.boardUrl, coordinator.selectedBoardTabKey.value)
        assertEquals(listOf(first, last), coordinator.openBoardTabs.value)

        databaseFlow.emit(listOf(first, last))
        runCurrent()
        coVerify(exactly = 1) { tabsRepository.deleteOpenBoardTab(middle.boardUrl) }
        coordinator.close()
    }

    /** bound mode の末尾 tab close が新しい末尾 tab を選択することを確認する。 */
    @Test
    fun boundClose_lastSelectedTab_selectsPreviousTab() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val first = testBoardTab("first")
        val middle = testBoardTab("middle")
        val last = testBoardTab("last")
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenBoardTab(last.boardUrl) } returns TabMutationResult.Success
        databaseFlow.emit(listOf(first, middle, last))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        coordinator.selectBoardTab(last.boardUrl)

        coordinator.closeBoardTab(last)
        runCurrent()
        assertEquals(middle.boardUrl, coordinator.selectedBoardTabKey.value)
        assertEquals(listOf(first, middle), coordinator.openBoardTabs.value)

        databaseFlow.emit(listOf(first, middle))
        runCurrent()
        coVerify(exactly = 1) { tabsRepository.deleteOpenBoardTab(last.boardUrl) }
        coordinator.close()
    }

    /** bound mode の非選択 tab close が現在の選択を維持することを確認する。 */
    @Test
    fun boundClose_unselectedTab_preservesSelection() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val first = testBoardTab("first")
        val middle = testBoardTab("middle")
        val last = testBoardTab("last")
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenBoardTab(middle.boardUrl) } returns TabMutationResult.Success
        databaseFlow.emit(listOf(first, middle, last))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        coordinator.selectBoardTab(last.boardUrl)

        coordinator.closeBoardTab(middle)
        runCurrent()
        assertEquals(last.boardUrl, coordinator.selectedBoardTabKey.value)
        assertEquals(listOf(first, last), coordinator.openBoardTabs.value)

        databaseFlow.emit(listOf(first, last))
        runCurrent()
        coVerify(exactly = 1) { tabsRepository.deleteOpenBoardTab(middle.boardUrl) }
        coordinator.close()
    }

    /** bound mode の唯一の tab close が null 選択と Empty presentation になることを確認する。 */
    @Test
    fun boundClose_soleTab_clearsSelectionAndPublishesEmpty() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val only = testBoardTab("only")
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenBoardTab(only.boardUrl) } returns TabMutationResult.Success
        databaseFlow.emit(listOf(only))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        coordinator.selectBoardTab(only.boardUrl)

        coordinator.closeBoardTab(only)
        runCurrent()
        assertNull(coordinator.selectedBoardTabKey.value)
        assertEquals(TabSelectionResolution.Empty, coordinator.boardPresentationState.value.selection)
        assertTrue(coordinator.openBoardTabs.value.isEmpty())

        databaseFlow.emit(emptyList())
        runCurrent()
        coVerify(exactly = 1) { tabsRepository.deleteOpenBoardTab(only.boardUrl) }
        coordinator.close()
    }

    /**
     * セッション状態更新が永続タブ保存を呼ばないことを確認する。
     */
    @Test
    fun updateBoardSessionState_doesNotPersistTabs() {
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val coordinator = createCoordinator(tabsRepository)
        val tab = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        coordinator.openBoardTab(tab)

        coordinator.updateBoardSessionState(tab.boardUrl) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("query"))
        }

        assertEquals("query", coordinator.getBoardSessionState(tab.boardUrl).searchQuery)
        coVerify(exactly = 0) { tabsRepository.saveOpenBoardTabs(any()) }
    }

    /**
     * 解決済み boardId を反映しても、固定状態とスクロール位置を保持したまま永続保存されることを確認する。
     */
    @Test
    fun updateBoardResolvedInfo_updatesBoardIdAndPreservesPersistentFields() {
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val coordinator = createCoordinator(tabsRepository)
        coordinator.openBoardTab(
            BoardTabInfo(
                boardId = 0,
                boardName = "Test Board",
                boardUrl = "https://example.com/test/",
                serviceName = "example.com",
                firstVisibleItemIndex = 12,
                firstVisibleItemScrollOffset = 34,
                isPinned = true,
            )
        )

        coordinator.updateBoardResolvedInfo(
            boardUrl = "https://example.com/test/",
            boardId = 42L,
            boardName = "Resolved Board",
        )

        val actual = coordinator.openBoardTabs.value.first()
        assertEquals(42L, actual.boardId)
        assertEquals("Resolved Board", actual.boardName)
        assertEquals(12, actual.firstVisibleItemIndex)
        assertEquals(34, actual.firstVisibleItemScrollOffset)
        assertEquals(true, actual.isPinned)
    }

    private fun createCoordinator(
        tabsRepository: TabsRepository,
        bookmarkRepository: BookmarkBoardRepository = mockk(relaxed = true),
    ): BoardTabsCoordinator {
        return BoardTabsCoordinator(
            tabsRepository = tabsRepository,
            bookmarkBoardRepository = bookmarkRepository,
        )
    }

    /** テスト用の Board route を作成する。 */
    private fun testBoardRoute(key: String = "target"): AppRoute.Board = AppRoute.Board(
        boardId = key.hashCode().toLong(),
        boardName = key,
        boardUrl = "https://example.com/$key/",
    )

    /** stable key を持つ Board tab を作成する。 */
    private fun testBoardTab(key: String): BoardTabInfo = BoardTabInfo(
        boardId = key.hashCode().toLong(),
        boardName = key,
        boardUrl = "https://example.com/$key/",
        serviceName = "example.com",
    )
}
