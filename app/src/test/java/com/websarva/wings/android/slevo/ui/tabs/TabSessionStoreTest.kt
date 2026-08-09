package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.session.BoardSessionState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState
import com.websarva.wings.android.slevo.ui.tabs.coordinator.BoardTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsLoadState
import com.websarva.wings.android.slevo.ui.tabs.controller.TabCommandResult
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.tabs.session.holder.BoardTabSessionHolder
import com.websarva.wings.android.slevo.ui.tabs.session.holder.BoardTabSessionHolderFactory
import com.websarva.wings.android.slevo.ui.tabs.session.holder.ThreadTabSessionHolder
import com.websarva.wings.android.slevo.ui.tabs.session.holder.ThreadTabSessionHolderFactory
import io.mockk.mockk
import io.mockk.every
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.verify
import io.mockk.slot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [TabSessionStore] のライフサイクルと操作委譲を検証するテスト。
 */
class TabSessionStoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val boardCoordinator = mockk<BoardTabsCoordinator>(relaxed = true)
    private val threadCoordinator = mockk<ThreadTabsCoordinator>(relaxed = true)
    private val threadHolderFactory = mockk<ThreadTabSessionHolderFactory>(relaxed = true)
    private val boardHolderFactory = mockk<BoardTabSessionHolderFactory>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)

    init {
        every { threadCoordinator.threadTabState } returns MutableStateFlow(ThreadTabsLoadState.Loading)
        every { threadCoordinator.isCanonicalThreadTab(any()) } returns true
        every { threadCoordinator.selectThreadTab(any()) } returns true
    }

    private fun createStore(
        threadCoordinatorOverride: ThreadTabsCoordinator = threadCoordinator,
        tabsRepositoryOverride: TabsRepository = mockk(relaxed = true),
        boardTabs: List<BoardTabInfo> = emptyList(),
        threadTabs: List<ThreadTabInfo> = emptyList(),
    ): TabSessionStore {
        every { boardCoordinator.openBoardTabs } returns MutableStateFlow(boardTabs)
        if (threadCoordinatorOverride === threadCoordinator) {
            every { threadCoordinatorOverride.openThreadTabs } returns MutableStateFlow(threadTabs)
        }
        return TabSessionStore(
            boardTabsCoordinator = boardCoordinator,
            threadTabsCoordinator = threadCoordinatorOverride,
            threadTabSessionHolderFactory = threadHolderFactory,
            boardTabSessionHolderFactory = boardHolderFactory,
            tabsRepository = tabsRepositoryOverride,
            boardRepository = mockk(relaxed = true),
            bbsServiceRepository = mockk(relaxed = true),
            settingsRepository = settingsRepository,
        )
    }

    private val store by lazy { createStore() }

    /**
     * [close] 呼び出し時に内部 CoroutineScope と全 holder が解放されることを確認する。
     */
    @Test
    fun close_disposesAllHoldersAndCancelsScope() {
        val threadHolder = mockThreadHolder()
        val boardHolder = mockBoardHolder()
        every { threadHolderFactory.create(any(), any()) } returns threadHolder
        every { boardHolderFactory.create(any(), any()) } returns boardHolder

        val testStore = createStore()
        testStore.threadBookmarkSheetHolder("example.com/test/123")
        testStore.boardBookmarkSheetHolder("https://example.com/test/")

        testStore.close()

        verify { threadHolder.dispose() }
        verify { boardHolder.dispose() }
    }

    /**
     * スレッドタブ削除時に対象 holder だけが破棄されることを確認する。
     */
    @Test
    fun closeThreadTab_disposesTargetHolderOnly() = runTest {
        val holder1 = mockThreadHolder()
        val holder2 = mockThreadHolder()
        every { threadHolderFactory.create("example.com/test/123", any()) } returns holder1
        every { threadHolderFactory.create("example.com/test/456", any()) } returns holder2

        val testStore = createStore()
        testStore.threadBookmarkSheetHolder("example.com/test/123")
        testStore.threadBookmarkSheetHolder("example.com/test/456")

        val tab = ThreadTabInfo(
            id = ThreadId.of("example.com", "test", "123"),
            title = "Thread 1",
            boardName = "Test Board",
            boardUrl = "https://example.com/test/",
            boardId = 1L,
        )
        testStore.closeThreadTab(tab)

        verify { holder1.dispose() }
        verify(exactly = 0) { holder2.dispose() }
    }

    /**
     * 板タブ削除時に対象 holder だけが破棄されることを確認する。
     */
    @Test
    fun closeBoardTab_disposesTargetHolderOnly() {
        val holder1 = mockBoardHolder()
        val holder2 = mockBoardHolder()
        every { boardHolderFactory.create("https://example.com/a/", any()) } returns holder1
        every { boardHolderFactory.create("https://example.com/b/", any()) } returns holder2

        val testStore = createStore()
        testStore.boardBookmarkSheetHolder("https://example.com/a/")
        testStore.boardBookmarkSheetHolder("https://example.com/b/")

        val tab = BoardTabInfo(
            boardId = 1,
            boardName = "A",
            boardUrl = "https://example.com/a/",
            serviceName = "example.com",
        )
        testStore.closeBoardTab(tab)

        verify { holder1.dispose() }
        verify(exactly = 0) { holder2.dispose() }
    }

    private fun mockThreadHolder(): ThreadTabSessionHolder {
        val holder = mockk<ThreadTabSessionHolder>(relaxed = true)
        every { holder.bookmarkSheetHolder } returns mockk(relaxed = true)
        every { holder.postDialogController } returns mockk(relaxed = true)
        every { holder.imageSaveEvents } returns MutableSharedFlow<com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent>()
        return holder
    }

    private fun mockBoardHolder(): BoardTabSessionHolder {
        val holder = mockk<BoardTabSessionHolder>(relaxed = true)
        every { holder.bookmarkSheetHolder } returns mockk(relaxed = true)
        every { holder.postDialogController } returns mockk(relaxed = true)
        return holder
    }

    /** 実 coordinator を retained close 回帰テストへ接続する。 */
    private fun realThreadCoordinator(
        tabsRepository: TabsRepository,
        bookmarkRepository: ThreadBookmarkRepository,
    ): ThreadTabsCoordinator = ThreadTabsCoordinator(
        tabsRepository = tabsRepository,
        threadBookmarkRepository = bookmarkRepository,
        datRepository = mockk<DatRepository>(relaxed = true),
        threadStateRepository = mockk<ThreadStateRepository>(relaxed = true),
    )

    /** 正規 snapshot と close 要求が同じ識別子を共有するテストタブを作る。 */
    private fun retainedCloseTestTab(): ThreadTabInfo = ThreadTabInfo(
        id = ThreadId.of("host", "board", "last-thread"),
        title = "Last thread",
        boardName = "Board",
        boardUrl = "https://host/board/",
        boardId = 1L,
    )

    /** 一括 close テスト用に固定状態だけを変えたスレッドタブを作る。 */
    private fun bulkThreadTab(threadKey: String, isPinned: Boolean = false): ThreadTabInfo = ThreadTabInfo(
        id = ThreadId.of("host", "board", threadKey),
        title = threadKey,
        boardName = "Board",
        boardUrl = "https://host/board/",
        boardId = 1L,
        isPinned = isPinned,
    )

    /**
     * 板タブ削除操作が [BoardTabsCoordinator] へ委譲されることを確認する。
     */
    @Test
    fun closeBoardTab_delegatesToBoardCoordinator() {
        val tab = BoardTabInfo(
            boardId = 1,
            boardName = "Test Board",
            boardUrl = "https://example.com/test/",
            serviceName = "example.com",
        )
        store.closeBoardTab(tab)
        verify { boardCoordinator.closeBoardTab(tab) }
    }

    /** 板ページの一括 close が未固定タブだけを表示順に委譲することを確認する。 */
    @Test
    fun closeAllUnpinnedTabs_forBoard_closesOnlyUnpinnedBoardTabs() {
        val pinnedTab = BoardTabInfo(
            boardId = 1,
            boardName = "Pinned",
            boardUrl = "https://example.com/pinned/",
            serviceName = "example.com",
            isPinned = true,
        )
        val firstTab = BoardTabInfo(
            boardId = 2,
            boardName = "First",
            boardUrl = "https://example.com/first/",
            serviceName = "example.com",
        )
        val secondTab = BoardTabInfo(
            boardId = 3,
            boardName = "Second",
            boardUrl = "https://example.com/second/",
            serviceName = "example.com",
        )
        val testStore = createStore(
            boardTabs = listOf(pinnedTab, firstTab, secondTab),
            threadTabs = listOf(retainedCloseTestTab()),
        )

        testStore.closeAllUnpinnedTabs(TabPage.BOARD)

        verify(exactly = 0) { boardCoordinator.closeBoardTab(pinnedTab) }
        verify { boardCoordinator.closeBoardTabs(listOf(firstTab, secondTab)) }
        coVerify(exactly = 0) { threadCoordinator.closeThreadTab(any<ThreadTabInfo>()) }
    }

    /** スレッドページの一括 close が未固定スレッドだけを retained scope で処理することを確認する。 */
    @Test
    fun closeAllUnpinnedTabs_forThread_closesOnlyUnpinnedThreadTabs() = runTest {
        val pinnedTab = bulkThreadTab("pinned", isPinned = true)
        val firstTab = bulkThreadTab("first")
        val secondTab = bulkThreadTab("second")
        val testStore = createStore(
            boardTabs = listOf(
                BoardTabInfo(
                    boardId = 1,
                    boardName = "Board",
                    boardUrl = "https://example.com/board/",
                    serviceName = "example.com",
                )
            ),
            threadTabs = listOf(pinnedTab, firstTab, secondTab),
        )

        testStore.closeAllUnpinnedTabs(TabPage.THREAD)
        runCurrent()

        coVerify { threadCoordinator.closeThreadTabs(listOf(firstTab, secondTab)) }
        verify(exactly = 0) { boardCoordinator.closeBoardTab(any()) }
    }

    /** 一括 close 対象がない場合に両 Coordinator へ削除要求を送らないことを確認する。 */
    @Test
    fun closeAllUnpinnedTabs_withNoTargets_isNoOp() {
        val testStore = createStore(
            boardTabs = listOf(
                BoardTabInfo(
                    boardId = 1,
                    boardName = "Pinned",
                    boardUrl = "https://example.com/pinned/",
                    serviceName = "example.com",
                    isPinned = true,
                )
            ),
            threadTabs = listOf(bulkThreadTab("pinned", isPinned = true)),
        )

        testStore.closeAllUnpinnedTabs(TabPage.BOARD)
        testStore.closeAllUnpinnedTabs(TabPage.THREAD)

        verify(exactly = 0) { boardCoordinator.closeBoardTab(any()) }
        coVerify(exactly = 0) { threadCoordinator.closeThreadTab(any<ThreadTabInfo>()) }
    }

    /** bulk close は未固定holderだけを一度破棄し、固定holderを維持することを確認する。 */
    @Test
    fun closeAllUnpinnedTabs_disposesOnlyTargetHolders() {
        val targetBoardHolder = mockBoardHolder()
        val pinnedBoardHolder = mockBoardHolder()
        every { boardHolderFactory.create("https://example.com/target/", any()) } returns targetBoardHolder
        every { boardHolderFactory.create("https://example.com/pinned/", any()) } returns pinnedBoardHolder
        val targetBoard = BoardTabInfo(1, "Target", "https://example.com/target/", "example.com")
        val pinnedBoard = targetBoard.copy(
            boardName = "Pinned",
            boardUrl = "https://example.com/pinned/",
            isPinned = true,
        )
        val testStore = createStore(boardTabs = listOf(targetBoard, pinnedBoard))
        testStore.boardBookmarkSheetHolder(targetBoard.boardUrl)
        testStore.boardBookmarkSheetHolder(pinnedBoard.boardUrl)

        testStore.closeAllUnpinnedTabs(TabPage.BOARD)

        verify { targetBoardHolder.dispose() }
        verify(exactly = 0) { pinnedBoardHolder.dispose() }
        verify { boardCoordinator.closeBoardTabs(listOf(targetBoard)) }
    }

    /**
     * スレッドタブ更新操作が [ThreadTabsCoordinator] へ委譲されることを確認する。
     */
    @Test
    fun refreshOpenThreads_delegatesToThreadCoordinator() {
        store.refreshOpenThreads()
        verify { threadCoordinator.refreshOpenThreads() }
    }

    /**
     * スレッドタブ更新キャンセル操作が [ThreadTabsCoordinator] へ委譲されることを確認する。
     */
    @Test
    fun cancelRefreshOpenThreads_delegatesToThreadCoordinator() {
        store.cancelRefreshOpenThreads()
        verify { threadCoordinator.cancelRefreshOpenThreads() }
    }

    /**
     * 最後のタブの close が Composition 相当の要求元 Job のキャンセル後も retained scope で完了することを確認する。
     */
    @Test
    fun requestCloseThreadTab_survivesCallerCancellationAndConfirmsCanonicalDeletion() = runTest {
        // --- 制御可能な正規 Flow と Repository 書き込み ---
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val writeStarted = CompletableDeferred<Unit>()
        val writeRelease = CompletableDeferred<Unit>()
        val tab = retainedCloseTestTab()
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenThreadTab(tab.id) } coAnswers {
            writeStarted.complete(Unit)
            writeRelease.await()
            true
        }
        val coordinator = realThreadCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(listOf(tab))
        val testStore = createStore(coordinator, tabsRepository)
        runCurrent()
        testStore.selectThreadTab(tab.id)

        // --- Composition 相当の要求元をキャンセルしても retained 処理を維持 ---
        val callerJob = launch(start = CoroutineStart.UNDISPATCHED) {
            testStore.requestCloseThreadTab("last-thread", tab.boardUrl)
            kotlinx.coroutines.awaitCancellation()
        }
        runCurrent()
        assertTrue(writeStarted.isCompleted)
        assertTrue(testStore.openThreadTabs.value.isEmpty())
        callerJob.cancel()
        runCurrent()
        assertFalse(callerJob.isActive)

        // --- 書き込みと Room 正本確認を完了 ---
        writeRelease.complete(Unit)
        runCurrent()
        databaseFlow.emit(emptyList())
        runCurrent()
        coVerify(exactly = 1) { tabsRepository.deleteOpenThreadTab(tab.id) }
        assertTrue(testStore.openThreadTabs.value.isEmpty())
        assertNull(testStore.selectedThreadTabKey.value)
        testStore.close()
    }

    /**
     * retained store 自体の破棄が未完了 close の正当な cancellation 境界になることを確認する。
     */
    @Test
    fun close_cancelsRetainedCloseAtStoreLifetimeBoundary() = runTest {
        // --- 制御可能な正規 Flow とキャンセル観測 ---
        val databaseFlow = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val writeStarted = CompletableDeferred<Unit>()
        val writeCancelled = CompletableDeferred<Unit>()
        val tab = retainedCloseTestTab()
        every { tabsRepository.observeOpenThreadTabs() } returns databaseFlow
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { tabsRepository.deleteOpenThreadTab(tab.id) } coAnswers {
            writeStarted.complete(Unit)
            try {
                CompletableDeferred<Unit>().await()
            } catch (cancellationException: CancellationException) {
                writeCancelled.complete(Unit)
                throw cancellationException
            }
            true
        }
        val coordinator = realThreadCoordinator(tabsRepository, bookmarkRepository)
        databaseFlow.emit(listOf(tab))
        val testStore = createStore(coordinator, tabsRepository)
        runCurrent()
        testStore.requestCloseThreadTab("last-thread", tab.boardUrl)
        runCurrent()
        assertTrue(writeStarted.isCompleted)

        // --- Activity-retained lifetime の終了 ---
        testStore.close()
        runCurrent()
        assertTrue(writeCancelled.isCompleted)
        coVerify(exactly = 1) { tabsRepository.deleteOpenThreadTab(tab.id) }
    }

    /**
     * 板タブ固定切替が [BoardTabsCoordinator] へ委譲されることを確認する。
     */
    @Test
    fun togglePinBoardTab_delegatesToBoardCoordinator() {
        store.togglePinBoardTab("https://example.com/test/")
        verify { boardCoordinator.togglePinBoardTab("https://example.com/test/") }
    }

    /**
     * 正規化済み板 route 登録 API が coordinator の ensure と select を順に呼ぶことを確認する。
     */
    @Test
    fun registerAndSelectBoardRoute_delegatesToCoordinator() {
        val route = AppRoute.Board(
            boardId = 1L,
            boardName = "board",
            boardUrl = "https://example.com/test/",
        )
        every { boardCoordinator.ensureBoardTab(route) } returns 0

        store.registerAndSelectBoardRoute(route)

        verify { boardCoordinator.ensureBoardTab(route) }
        verify { boardCoordinator.selectBoardTab(route.boardUrl) }
    }

    /** Board ensure failure は presentation 待ちへ進まず navigation 不可の terminal result になる。 */
    @Test
    fun registerAndConfirmBoardRoute_failureDoesNotSelectOrNavigate() = runTest {
        val route = AppRoute.Board(
            boardId = 1L,
            boardName = "board",
            boardUrl = "https://example.com/test/",
        )
        coEvery { boardCoordinator.ensureBoardTabCommand(route) } returns
            TabCommandResult.Failure(IllegalStateException("write failed"))

        assertFalse(createStore().registerAndConfirmBoardRoute(route))
        verify(exactly = 0) { boardCoordinator.selectBoardTabCommand(route.boardUrl) }
    }

    /**
     * 正規化済みスレ route 登録 API が coordinator の ensure と select を順に呼ぶことを確認する。
     */
    @Test
    fun registerAndSelectThreadRoute_delegatesToCoordinator() = runTest {
        val route = AppRoute.Thread(
            threadKey = "123",
            boardUrl = "https://example.com/test/",
            boardName = "board",
            threadTitle = "title",
        )
        coEvery { threadCoordinator.ensureThreadTab(route) } returns 0

        store.registerAndSelectThreadRoute(route)

        coVerify { threadCoordinator.ensureThreadTab(route) }
        verify { threadCoordinator.selectThreadTab(any()) }
    }

    /**
     * 正規化設定が有効な場合、板 route の boardUrl が 5ch.io に置き換わることを確認する。
     */
    @Test
    fun normalizeBoardRouteForNavigation_rewritesBoardUrlWhenEnabled() = runTest {
        coEvery { settingsRepository.getIsRedirect5chNetToIoEnabled() } returns true
        val route = AppRoute.Board(
            boardName = "board",
            boardUrl = "https://agree.5ch.net/operate/",
        )

        val normalized = store.normalizeBoardRouteForNavigation(route)

        assertEquals("https://agree.5ch.io/operate/", normalized.boardUrl)
        assertEquals(route.boardName, normalized.boardName)
    }

    /**
     * 正規化設定が有効な場合、スレ route の boardUrl が 5ch.io に置き換わることを確認する。
     */
    @Test
    fun normalizeThreadRouteForNavigation_rewritesBoardUrlWhenEnabled() = runTest {
        coEvery { settingsRepository.getIsRedirect5chNetToIoEnabled() } returns true
        val route = AppRoute.Thread(
            threadKey = "123",
            boardName = "board",
            boardUrl = "https://agree.5ch.net/operate/",
            threadTitle = "title",
        )

        val normalized = store.normalizeThreadRouteForNavigation(route)

        assertEquals("https://agree.5ch.io/operate/", normalized.boardUrl)
        assertEquals(route.threadKey, normalized.threadKey)
    }

    /**
     * 板セッション状態更新 API が coordinator へ委譲されることを確認する。
     */
    @Test
    fun updateBoardSessionState_delegatesToBoardCoordinator() {
        val transform = slot<(BoardSessionState) -> BoardSessionState>()

        store.updateBoardSessionState("https://example.com/test/") {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("query"))
        }

        verify { boardCoordinator.updateBoardSessionState("https://example.com/test/", capture(transform)) }
        assertEquals("query", transform.captured(BoardSessionState()).searchQuery)
    }

    /**
     * スレッドセッション状態更新 API が coordinator へ委譲されることを確認する。
     */
    @Test
    fun updateThreadSessionState_delegatesToThreadCoordinator() {
        val threadId = ThreadId.of("example.com", "test", "123")
        val transform = slot<(ThreadSessionState) -> ThreadSessionState>()

        store.updateThreadSessionState(threadId) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("query"))
        }

        verify { threadCoordinator.updateThreadSessionState(threadId, capture(transform)) }
        assertEquals("query", transform.captured(ThreadSessionState()).searchQuery)
    }
}
