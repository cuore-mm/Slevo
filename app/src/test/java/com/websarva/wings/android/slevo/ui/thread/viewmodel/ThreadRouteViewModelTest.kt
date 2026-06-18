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
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionRuntimeState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * [ThreadRouteViewModel] の軽量回帰テスト。
 *
 * 直接合成化後の lazy load、reload、自動スクロールの委譲条件だけを最小依存で検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadRouteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiStateFor_sameKeyReusesCachedFlow() {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(listOf(threadTab(threadId, "title")), selectedTabKey = threadId.value)
        val viewModel = dependencies.createViewModel()

        val first = viewModel.uiStateFor(threadId.value)
        val second = viewModel.uiStateFor(threadId.value)

        assertSame(first, second)
    }

    @Test
    fun uiStateFor_doesNotLoadUntilCollected() = runTest {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(listOf(threadTab(threadId, "title")), selectedTabKey = threadId.value)
        val viewModel = dependencies.createViewModel()

        viewModel.uiStateFor(threadId.value)
        advanceUntilIdle()

        coVerify(exactly = 0) { dependencies.threadContentLoadUseCase.load(any(), any(), any()) }

        val job = launch { viewModel.uiStateFor(threadId.value).collect { } }
        advanceUntilIdle()
        job.cancel()

        coVerify(atLeast = 1) { dependencies.threadContentLoadUseCase.load(any(), "111", any()) }
    }

    @Test
    fun reloadThread_callsThreadContentLoadUseCase() = runTest {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(listOf(threadTab(threadId, "title")), selectedTabKey = threadId.value)
        val viewModel = dependencies.createViewModel()

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()

        coVerify(atLeast = 1) { dependencies.threadContentLoadUseCase.load(any(), "111", any()) }
    }

    @Test
    fun onAutoScrollReachedBottom_doesNotUpdateUnselectedTab() = runTest {
        val firstId = ThreadId.of("example.com", "test", "111")
        val secondId = ThreadId.of("example.com", "test", "222")
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(firstId, "first"), threadTab(secondId, "second")),
            selectedTabKey = firstId.value,
            initialSessionStates = mapOf(
                firstId.value to ThreadSessionState(isAutoScroll = true),
                secondId.value to ThreadSessionState(isAutoScroll = true),
            ),
        )
        val viewModel = dependencies.createViewModel()

        viewModel.onAutoScrollReachedBottom(secondId.value)
        advanceUntilIdle()

        coVerify(exactly = 0) { dependencies.threadContentLoadUseCase.load(any(), "222", any()) }
    }

    /** テスト用依存一式を構築する。 */
    private fun mockDependencies(
        tabs: List<ThreadTabInfo>,
        selectedTabKey: String?,
        initialSessionStates: Map<String, ThreadSessionState> = tabs.associate { it.id.value to ThreadSessionState() },
    ): RouteDependencies {
        val openTabs = MutableStateFlow(tabs)
        val selectedKey = MutableStateFlow(selectedTabKey)
        val sessionStates = MutableStateFlow(initialSessionStates)
        val runtimeStates = MutableStateFlow(tabs.associate { it.id.value to ThreadSessionRuntimeState() })
        val store = mockk<TabSessionStore>(relaxed = true)
        every { store.openThreadTabs } returns openTabs
        every { store.selectedThreadTabKey } returns selectedKey
        every { store.threadSessionStates } returns sessionStates
        every { store.threadBookmarkSheetHolder(any()).uiState } returns MutableStateFlow(BookmarkSheetUiState())
        every { store.threadPostDialogController(any()) } returns mockk(relaxed = true)
        every { store.threadPostDialogSuccessEvents(any()) } returns MutableSharedFlow()
        every { store.getThreadSessionState(any()) } answers {
            sessionStates.value[firstArg<ThreadId>().value] ?: ThreadSessionState()
        }
        every { store.getThreadRuntimeState(any()) } answers {
            runtimeStates.value[firstArg<ThreadId>().value] ?: ThreadSessionRuntimeState()
        }

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
            threadVisiblePostsUseCase = ThreadVisiblePostsUseCase(),
            logger = mockk(relaxed = true),
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

    /** テスト用のタブ情報を作る。 */
    private fun threadTab(threadId: ThreadId, title: String): ThreadTabInfo {
        return ThreadTabInfo(
            id = threadId,
            title = title,
            boardName = "board",
            boardUrl = "https://example.com/test/",
            boardId = 1L,
        )
    }
}
