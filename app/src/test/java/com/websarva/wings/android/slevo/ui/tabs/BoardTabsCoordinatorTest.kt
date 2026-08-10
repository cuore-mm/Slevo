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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

    /** Board bulk close が固定タブを残し、逐次closeと同じ最終選択へ収束することを確認する。 */
    @Test
    fun closeBoardTabs_keepsPinnedTabAndMatchesSequentialSelection() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val pinned = testBoardTab("pinned").copy(isPinned = true)
        val selected = testBoardTab("selected")
        val last = testBoardTab("last")
        coordinator.openBoardTab(pinned)
        coordinator.openBoardTab(selected)
        coordinator.openBoardTab(last)
        coordinator.selectBoardTab(selected.boardUrl)

        coordinator.closeBoardTabs(listOf(selected, last))

        assertEquals(listOf(pinned.boardUrl), coordinator.openBoardTabs.value.map { it.boardUrl })
        assertEquals(pinned.boardUrl, coordinator.selectedBoardTabKey.value)
    }

    /** 選択 key を正本として先頭・中央・末尾からの有効な page animation と境界 no-op を確認する。 */
    @Test
    fun animateBoardPage_usesSelectedIndexAndIgnoresBoundaryTargets() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val first = testBoardTab("first")
        val middle = testBoardTab("middle")
        val last = testBoardTab("last")
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        databaseFlow.emit(listOf(first, middle, last))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        val emittedTargets = mutableListOf<Int>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.boardPageAnimation.collect { emittedTargets += it }
        }
        runCurrent()

        coordinator.selectBoardTab(first.boardUrl)
        coordinator.animateBoardPage(1)
        runCurrent()
        coordinator.animateBoardPage(-1)
        runCurrent()
        coordinator.selectBoardTab(middle.boardUrl)
        coordinator.animateBoardPage(-1)
        runCurrent()
        coordinator.animateBoardPage(1)
        runCurrent()
        coordinator.selectBoardTab(last.boardUrl)
        coordinator.animateBoardPage(-1)
        runCurrent()
        coordinator.animateBoardPage(1)
        runCurrent()

        assertEquals(listOf(1, 0, 2, 1), emittedTargets)
        collector.cancel()
        coordinator.close()
    }

    /** empty 一覧、null 選択、存在しない選択からは page animation を発行しない。 */
    @Test
    fun animateBoardPage_ignoresEmptyAndUnresolvedSelection() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        databaseFlow.emit(emptyList())

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        val emittedTargets = mutableListOf<Int>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.boardPageAnimation.collect { emittedTargets += it }
        }
        runCurrent()

        coordinator.animateBoardPage(1)
        runCurrent()
        assertTrue(emittedTargets.isEmpty())

        databaseFlow.emit(listOf(testBoardTab("only"), testBoardTab("second")))
        runCurrent()
        setControllerSelectedKey(coordinator, null)
        coordinator.animateBoardPage(1)
        runCurrent()
        setControllerSelectedKey(coordinator, "missing")
        coordinator.animateBoardPage(1)
        runCurrent()

        assertTrue(emittedTargets.isEmpty())
        collector.cancel()
        coordinator.close()
    }

    /** canonical 確認前の ensure tab も effective order の選択 index から animation できる。 */
    @Test
    fun animateBoardPage_usesPendingEnsureInEffectiveOrderBeforeCanonicalConfirmation() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val existing = testBoardTab("existing")
        val pending = testBoardTab("pending")
        val writeStarted = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.ensureOpenBoardTab(pending) } coAnswers {
            writeStarted.complete(Unit)
            releaseWrite.await()
            TabMutationResult.Success
        }
        databaseFlow.emit(listOf(existing))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        val emittedTargets = mutableListOf<Int>()
        val collector = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.boardPageAnimation.collect { emittedTargets += it }
        }
        runCurrent()

        val command = backgroundScope.async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.ensureBoardTabCommand(
                AppRoute.Board(
                    boardId = pending.boardId,
                    boardName = pending.boardName,
                    boardUrl = pending.boardUrl,
                )
            )
        }
        runCurrent()
        assertTrue(writeStarted.isCompleted)
        coordinator.selectBoardTab(pending.boardUrl)
        coordinator.animateBoardPage(-1)
        runCurrent()

        assertFalse(command.isCompleted)
        assertEquals(listOf(0), emittedTargets)

        releaseWrite.complete(Unit)
        runCurrent()
        databaseFlow.emit(listOf(existing, pending))
        runCurrent()
        assertTrue(command.await() is TabCommandResult.Success)
        collector.cancel()
        coordinator.close()
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

    /** 中間 canonical 通知がなくても、反復 scroll は最新位置だけを投影して最終値へ収束する。 */
    @Test
    fun repeatedScrollWrites_finalOnlyCanonicalEmissionBoundsPendingAndConverges() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val initial = testBoardTab("rapid-scroll")
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.updateBoardTabScrollPosition(any(), any(), any()) } returns TabMutationResult.Success
        databaseFlow.emit(listOf(initial))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()

        val positions = listOf(10 to 2, 20 to 4, 30 to 6)
        positions.forEach { (index, offset) ->
            coordinator.updateBoardScrollPosition(initial.boardUrl, index, offset)
            runCurrent()
            assertEquals(index, coordinator.openBoardTabs.value.single().firstVisibleItemIndex)
            assertEquals(offset, coordinator.openBoardTabs.value.single().firstVisibleItemScrollOffset)
        }

        assertEquals(initial, coordinator.openBoardTabs.value.single().copy(
            firstVisibleItemIndex = initial.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = initial.firstVisibleItemScrollOffset,
        ))
        val final = initial.copy(firstVisibleItemIndex = 30, firstVisibleItemScrollOffset = 6)
        databaseFlow.emit(listOf(final))
        runCurrent()

        assertEquals(listOf(final), coordinator.openBoardTabs.value)
        coVerify(exactly = 1) { tabsRepository.updateBoardTabScrollPosition(initial.boardUrl, 10, 2) }
        coVerify(exactly = 1) { tabsRepository.updateBoardTabScrollPosition(initial.boardUrl, 20, 4) }
        coVerify(exactly = 1) { tabsRepository.updateBoardTabScrollPosition(initial.boardUrl, 30, 6) }
        coVerify(exactly = 0) { tabsRepository.saveOpenBoardTabs(any()) }
        coordinator.close()
    }

    /** 初回 dispatch 前に連続した scroll を登録すると、obsolete write は repository に届かない。 */
    @Test
    fun queuedScrollWrites_supersededWritesAreSkipped() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val initial = testBoardTab("queued-scroll")
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.updateBoardTabScrollPosition(any(), any(), any()) } returns TabMutationResult.Success
        databaseFlow.emit(listOf(initial))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()

        coordinator.updateBoardScrollPosition(initial.boardUrl, 1, 10)
        coordinator.updateBoardScrollPosition(initial.boardUrl, 2, 20)
        coordinator.updateBoardScrollPosition(initial.boardUrl, 3, 30)
        runCurrent()

        coVerify(exactly = 0) { tabsRepository.updateBoardTabScrollPosition(initial.boardUrl, 1, 10) }
        coVerify(exactly = 0) { tabsRepository.updateBoardTabScrollPosition(initial.boardUrl, 2, 20) }
        coVerify(exactly = 1) { tabsRepository.updateBoardTabScrollPosition(initial.boardUrl, 3, 30) }
        assertEquals(3, coordinator.openBoardTabs.value.single().firstVisibleItemIndex)
        assertEquals(30, coordinator.openBoardTabs.value.single().firstVisibleItemScrollOffset)
        coordinator.close()
    }

    /** 反復 pin は effective projection の最新 intent を維持し、最終 canonical 値で確認される。 */
    @Test
    fun rapidPinWrites_finalOnlyCanonicalEmissionConverges() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val initial = testBoardTab("rapid-pin")
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.setBoardTabPinned(any(), any()) } returns TabMutationResult.Success
        databaseFlow.emit(listOf(initial))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()

        repeat(3) { coordinator.togglePinBoardTab(initial.boardUrl) }
        assertTrue(coordinator.openBoardTabs.value.single().isPinned)
        runCurrent()

        coVerify(exactly = 0) { tabsRepository.setBoardTabPinned(initial.boardUrl, false) }
        coVerify(exactly = 1) { tabsRepository.setBoardTabPinned(initial.boardUrl, true) }
        databaseFlow.emit(listOf(initial.copy(isPinned = true)))
        runCurrent()

        assertTrue(coordinator.openBoardTabs.value.single().isPinned)
        coVerify(exactly = 0) { tabsRepository.saveOpenBoardTabs(any()) }
        coordinator.close()
    }

    /** 反復 resolved info は古い board metadata を復活させず、最終 snapshot に収束する。 */
    @Test
    fun rapidResolvedInfoWrites_finalOnlyCanonicalEmissionConverges() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val initial = testBoardTab("rapid-info").copy(isPinned = true, firstVisibleItemIndex = 7)
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.updateBoardTabInfo(any()) } returns TabMutationResult.Success
        databaseFlow.emit(listOf(initial))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()

        coordinator.updateBoardResolvedInfo(initial.boardUrl, 2L, "old")
        coordinator.updateBoardResolvedInfo(initial.boardUrl, 3L, "latest")
        runCurrent()

        val projected = coordinator.openBoardTabs.value.single()
        assertEquals(3L, projected.boardId)
        assertEquals("latest", projected.boardName)
        assertTrue(projected.isPinned)
        assertEquals(7, projected.firstVisibleItemIndex)
        coVerify(exactly = 0) { tabsRepository.updateBoardTabInfo(match { it.boardId == 2L }) }
        coVerify(exactly = 1) { tabsRepository.updateBoardTabInfo(match { it.boardId == 3L && it.boardName == "latest" }) }

        databaseFlow.emit(listOf(projected))
        runCurrent()
        assertEquals(listOf(projected), coordinator.openBoardTabs.value)
        coordinator.close()
    }

    /** supersede 後に到着した先行 failure は、最新 projection と pending を変更しない。 */
    @Test
    fun supersededWriteFailure_doesNotRemoveLatestPending() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val initial = testBoardTab("superseded-failure")
        val firstRelease = CompletableDeferred<TabMutationResult>()
        val latestRelease = CompletableDeferred<TabMutationResult>()
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.updateBoardTabScrollPosition(any(), any(), 10) } coAnswers { firstRelease.await() }
        coEvery { tabsRepository.updateBoardTabScrollPosition(any(), any(), 20) } coAnswers { latestRelease.await() }
        databaseFlow.emit(listOf(initial))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        coordinator.updateBoardScrollPosition(initial.boardUrl, 1, 10)
        runCurrent()
        coordinator.updateBoardScrollPosition(initial.boardUrl, 2, 20)
        runCurrent()

        firstRelease.complete(TabMutationResult.Failure(IllegalStateException("obsolete failure")))
        runCurrent()
        assertEquals(2, coordinator.openBoardTabs.value.single().firstVisibleItemIndex)
        assertEquals(20, coordinator.openBoardTabs.value.single().firstVisibleItemScrollOffset)

        latestRelease.complete(TabMutationResult.Success)
        runCurrent()
        databaseFlow.emit(listOf(initial.copy(firstVisibleItemIndex = 2, firstVisibleItemScrollOffset = 20)))
        runCurrent()
        assertEquals(2, coordinator.openBoardTabs.value.single().firstVisibleItemIndex)
        coordinator.close()
    }

    /** 最新 write の failure は最新 projection だけを除去し、DB canonical state へ戻す。 */
    @Test
    fun latestWriteFailure_rollsBackWithoutRestoringSupersededProjection() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val initial = testBoardTab("latest-failure")
        val firstRelease = CompletableDeferred<TabMutationResult>()
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.updateBoardTabScrollPosition(any(), any(), 10) } coAnswers { firstRelease.await() }
        coEvery { tabsRepository.updateBoardTabScrollPosition(any(), any(), 20) } returns
            TabMutationResult.Failure(IllegalStateException("latest failure"))
        databaseFlow.emit(listOf(initial))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        coordinator.updateBoardScrollPosition(initial.boardUrl, 1, 10)
        runCurrent()
        coordinator.updateBoardScrollPosition(initial.boardUrl, 2, 20)
        runCurrent()

        assertEquals(initial, coordinator.openBoardTabs.value.single())
        firstRelease.complete(TabMutationResult.Success)
        runCurrent()
        assertEquals(initial, coordinator.openBoardTabs.value.single())
        coVerify(exactly = 1) { tabsRepository.updateBoardTabScrollPosition(initial.boardUrl, 2, 20) }
        coordinator.close()
    }

    /** 同じ更新種別でも Board が異なれば canonical confirmation は相互に独立する。 */
    @Test
    fun sameOperationOnDifferentBoards_confirmsIndependently() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val first = testBoardTab("independent-a")
        val second = testBoardTab("independent-b")
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.updateBoardTabScrollPosition(any(), any(), any()) } returns TabMutationResult.Success
        databaseFlow.emit(listOf(first, second))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        coordinator.updateBoardScrollPosition(first.boardUrl, 1, 10)
        coordinator.updateBoardScrollPosition(second.boardUrl, 2, 20)
        runCurrent()

        databaseFlow.emit(listOf(first.copy(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 10), second))
        runCurrent()
        assertEquals(1, coordinator.openBoardTabs.value.first().firstVisibleItemIndex)
        assertEquals(20, coordinator.openBoardTabs.value.last().firstVisibleItemScrollOffset)

        databaseFlow.emit(listOf(
            first.copy(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 10),
            second.copy(firstVisibleItemIndex = 2, firstVisibleItemScrollOffset = 20),
        ))
        runCurrent()
        assertEquals(1, coordinator.openBoardTabs.value.first().firstVisibleItemIndex)
        assertEquals(2, coordinator.openBoardTabs.value.last().firstVisibleItemIndex)
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

    /** bound bulk close が対象を一つのRepository commandで削除し、canonical確認後に完了することを確認する。 */
    @Test
    fun boundBulkClose_excludesTargetsImmediatelyAndCallsRepositoryOnce() = runTest {
        val databaseFlow = MutableSharedFlow<List<BoardTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val first = testBoardTab("first")
        val second = testBoardTab("second")
        val last = testBoardTab("last")
        every { tabsRepository.observeOpenBoardTabs() } returns databaseFlow
        every { bookmarkRepository.observeGroupsWithBoards() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenBoardTabs(any()) } returns TabMutationResult.Success
        databaseFlow.emit(listOf(first, second, last))

        val coordinator = createCoordinator(tabsRepository, bookmarkRepository)
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler)))
        runCurrent()
        coordinator.selectBoardTab(second.boardUrl)
        coordinator.closeBoardTabs(listOf(second, last))
        runCurrent()

        assertEquals(listOf(first), coordinator.openBoardTabs.value)
        assertEquals(first.boardUrl, coordinator.selectedBoardTabKey.value)
        databaseFlow.emit(listOf(first))
        runCurrent()

        coVerify(exactly = 1) { tabsRepository.deleteOpenBoardTabs(listOf(second.boardUrl, last.boardUrl)) }
        coVerify(exactly = 0) { tabsRepository.deleteOpenBoardTab(any()) }
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

    /** animation 専用の unresolved selection を作り、presentation の選択補正とは分離して検証する。 */
    @Suppress("UNCHECKED_CAST")
    private fun setControllerSelectedKey(coordinator: BoardTabsCoordinator, selectedKey: String?) {
        val stateField = BoardTabsCoordinator::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        val stateFlow = stateField.get(coordinator) as MutableStateFlow<Any>
        val state = stateFlow.value
        val copyMethod = state.javaClass.methods.first { method ->
            method.name == "copy" && method.parameterTypes.size == 5
        }
        val updatedState = copyMethod.invoke(
            state,
            state.javaClass.getMethod("getLoadPhase").invoke(state),
            state.javaClass.getMethod("getCanonicalTabs").invoke(state),
            state.javaClass.getMethod("getPendingCommands").invoke(state),
            selectedKey,
            state.javaClass.getMethod("getPresentation").invoke(state),
        )
        stateFlow.value = updatedState
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
