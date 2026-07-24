package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.session.BoardSessionState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState
import com.websarva.wings.android.slevo.ui.tabs.coordinator.BoardTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsLoadState
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private fun createStore(): TabSessionStore {
        return TabSessionStore(
            boardTabsCoordinator = boardCoordinator,
            threadTabsCoordinator = threadCoordinator,
            threadTabSessionHolderFactory = threadHolderFactory,
            boardTabSessionHolderFactory = boardHolderFactory,
            tabsRepository = mockk(relaxed = true),
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
