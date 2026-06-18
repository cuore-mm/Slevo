package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadDate
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadReadStateRepository
import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * [ThreadRouteViewModel] の route-level `UiState` 合成と更新 API を検証するテスト。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadRouteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiStateFor_sameKeyReusesCachedFlow() {
        val threadId = ThreadId.of("example.com", "test", "111")
        val tab = ThreadTabInfo(threadId, "title", "board", "https://example.com/test/", 1L)
        val dependencies = mockDependencies(listOf(tab), threadId.value)
        val viewModel = dependencies.createViewModel()

        val first = viewModel.uiStateFor(threadId.value)
        val second = viewModel.uiStateFor(threadId.value)

        assertSame(first, second)
    }

    @Test
    fun selectedUiState_switchesTabsAndUsesDirectSynthesis() = runTest {
        val firstId = ThreadId.of("example.com", "test", "111")
        val secondId = ThreadId.of("example.com", "test", "222")
        val tabs = listOf(
            ThreadTabInfo(firstId, "first", "board", "https://example.com/test/", 1L),
            ThreadTabInfo(secondId, "second", "board", "https://example.com/test/", 1L),
        )
        val dependencies = mockDependencies(tabs, firstId.value)
        val viewModel = dependencies.createViewModel()
        val titles = mutableListOf<String>()

        val job = launch {
            viewModel.selectedUiState.collect { state ->
                titles += state.threadInfo.title
            }
        }
        advanceUntilIdle()

        dependencies.selectedKey.value = secondId.value
        advanceUntilIdle()
        dependencies.selectedKey.value = firstId.value
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("first", "second", "first"), titles.filter { it.isNotBlank() }.takeLast(3))
        verify(atLeast = 1) { dependencies.threadContentLoadUseCase.load(any(), firstId.threadKey, any()) }
        verify(atLeast = 1) { dependencies.threadContentLoadUseCase.load(any(), secondId.threadKey, any()) }
    }

    @Test
    fun onAutoScrollReachedBottom_updatesOnlySelectedTab() = runTest {
        val firstId = ThreadId.of("example.com", "test", "111")
        val secondId = ThreadId.of("example.com", "test", "222")
        val tabs = listOf(
            ThreadTabInfo(firstId, "first", "board", "https://example.com/test/", 1L),
            ThreadTabInfo(secondId, "second", "board", "https://example.com/test/", 1L),
        )
        val dependencies = mockDependencies(tabs, firstId.value, autoScrollEnabled = true)
        val viewModel = dependencies.createViewModel()
        advanceUntilIdle()

        viewModel.onAutoScrollReachedBottom(secondId.value)
        viewModel.onAutoScrollReachedBottom(firstId.value)
        advanceUntilIdle()

        verify(exactly = 1) { dependencies.threadContentLoadUseCase.load(any(), firstId.threadKey, any()) }
        verify(exactly = 1) { dependencies.threadContentLoadUseCase.load(any(), secondId.threadKey, any()) }
    }

    @Test
    fun refreshOpenThreads_delegatesToStore() {
        val dependencies = mockDependencies(emptyList(), null)
        val viewModel = dependencies.createViewModel()

        viewModel.refreshOpenThreads()
        viewModel.cancelRefreshOpenThreads()

        verify(exactly = 1) { dependencies.store.refreshOpenThreads() }
        verify(exactly = 1) { dependencies.store.cancelRefreshOpenThreads() }
    }

    /** テスト用依存一式を作る。 */
    private fun mockDependencies(
        tabs: List<ThreadTabInfo>,
        selectedTabKey: String?,
        autoScrollEnabled: Boolean = false,
    ): RouteDependencies {
        val openTabs = MutableStateFlow(tabs)
        val selectedKey = MutableStateFlow(selectedTabKey)
        val sessionStates = MutableStateFlow(
            tabs.associate { tab ->
                tab.id.value to com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState(
                    isAutoScroll = autoScrollEnabled,
                )
            }
        )
        val store = mockk<TabSessionStore>(relaxed = true)
        every { store.openThreadTabs } returns openTabs
        every { store.selectedThreadTabKey } returns selectedKey
        every { store.threadSessionStates } returns sessionStates
        every { store.threadBookmarkSheetHolder(any()).uiState } returns MutableStateFlow(BookmarkSheetUiState())
        every { store.threadPostDialogController(any()) } returns mockk(relaxed = true)
        every { store.threadPostDialogSuccessEvents(any()) } returns MutableSharedFlow()
        every { store.getThreadSessionState(any()) } answers {
            sessionStates.value[firstArg<ThreadId>().value]
                ?: com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState()
        }
        every { store.getThreadRuntimeState(any()) } returns com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionRuntimeState()

        val boardRepository = mockk<BoardRepository>()
        coEvery { boardRepository.ensureBoard(any()) } answers { firstArg<BoardInfo>().boardId.takeIf { it != 0L } ?: 1L }
        coEvery { boardRepository.fetchBoardNoname(any()) } returns null

        val historyRepository = mockk<ThreadHistoryRepository>()
        coEvery { historyRepository.recordHistory(any(), any(), any()) } returns 1L

        val postHistoryRepository = mockk<PostHistoryRepository>(relaxed = true)
        every { postHistoryRepository.observeMyPostNumbers(any()) } returns flowOf(emptySet())

        val bookmarkRepository = mockk<ThreadBookmarkRepository>()
        every { bookmarkRepository.getBookmarkWithGroup(any(), any()) } returns flowOf(null)

        val ngRepository = mockk<NgRepository>()
        every { ngRepository.observeNgs() } returns flowOf(emptyList())

        val settingsRepository = mockk<SettingsRepository>(relaxed = true)
        every { settingsRepository.observeTextScale() } returns flowOf(1f)
        every { settingsRepository.observeIsIndividualTextScale() } returns flowOf(false)
        every { settingsRepository.observeHeaderTextScale() } returns flowOf(0.85f)
        every { settingsRepository.observeBodyTextScale() } returns flowOf(1f)
        every { settingsRepository.observeLineHeight() } returns flowOf(1f)
        every { settingsRepository.observeIsThreadMinimapScrollbarEnabled() } returns flowOf(true)
        every { settingsRepository.observeGestureSettings() } returns flowOf(com.websarva.wings.android.slevo.data.model.GestureSettings.DEFAULT)
        every { settingsRepository.observeIsTreeSort() } returns flowOf(false)

        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        every { tabsRepository.observeOpenThreadTabs() } returns openTabs

        val readStateRepository = mockk<ThreadReadStateRepository>(relaxed = true)

        val threadContentLoadUseCase = mockk<ThreadContentLoadUseCase>()
        tabs.forEach { tab ->
            coEvery { threadContentLoadUseCase.load(tab.boardUrl, tab.threadKey, any()) } returns ThreadContentLoadResult(
                uiPosts = emptyList(),
                threadTitle = tab.title,
                resCount = 0,
                threadDate = ThreadDate(2024, 1, 1, 0, 0, "月"),
                momentum = 0.0,
                idCountMap = emptyMap(),
                idIndexList = emptyList(),
                replySourceMap = emptyMap(),
                treeOrder = emptyList(),
                treeDepthMap = emptyMap(),
                treeRootMap = emptyMap(),
            )
        }

        val threadVisiblePostsUseCase = ThreadVisiblePostsUseCase()
        val logger = mockk<AppLogger>(relaxed = true)
        return RouteDependencies(
            store = store,
            boardRepository = boardRepository,
            historyRepository = historyRepository,
            postHistoryRepository = postHistoryRepository,
            bookmarkRepository = bookmarkRepository,
            ngRepository = ngRepository,
            settingsRepository = settingsRepository,
            tabsRepository = tabsRepository,
            readStateRepository = readStateRepository,
            threadContentLoadUseCase = threadContentLoadUseCase,
            threadVisiblePostsUseCase = threadVisiblePostsUseCase,
            logger = logger,
            selectedKey = selectedKey,
        )
    }

    /** テスト用依存 bundle。 */
    private data class RouteDependencies(
        val store: TabSessionStore,
        val boardRepository: BoardRepository,
        val historyRepository: ThreadHistoryRepository,
        val postHistoryRepository: PostHistoryRepository,
        val bookmarkRepository: ThreadBookmarkRepository,
        val ngRepository: NgRepository,
        val settingsRepository: SettingsRepository,
        val tabsRepository: TabsRepository,
        val readStateRepository: ThreadReadStateRepository,
        val threadContentLoadUseCase: ThreadContentLoadUseCase,
        val threadVisiblePostsUseCase: ThreadVisiblePostsUseCase,
        val logger: AppLogger,
        val selectedKey: MutableStateFlow<String?>,
    ) {
        /** 依存 bundle から ViewModel を生成する。 */
        fun createViewModel(): ThreadRouteViewModel {
            return ThreadRouteViewModel(
                tabSessionStore = store,
                boardRepository = boardRepository,
                historyRepository = historyRepository,
                postHistoryRepository = postHistoryRepository,
                threadBookmarkRepository = bookmarkRepository,
                ngRepository = ngRepository,
                settingsRepository = settingsRepository,
                tabsRepository = tabsRepository,
                threadReadStateRepository = readStateRepository,
                threadContentLoadUseCase = threadContentLoadUseCase,
                threadVisiblePostsUseCase = threadVisiblePostsUseCase,
                logger = logger,
            )
        }
    }
}
