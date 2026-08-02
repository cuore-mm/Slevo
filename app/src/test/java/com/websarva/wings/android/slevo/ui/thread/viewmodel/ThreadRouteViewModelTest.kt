package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
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
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionRuntimeState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostGroup
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Test
    fun closingThreadTab_cancelsInFlightLoad() = runTest {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            suspendLoad = true,
        )
        val viewModel = dependencies.createViewModel()

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()
        dependencies.openTabs.value = emptyList()
        advanceUntilIdle()

        assertTrue(dependencies.threadLoadCancelled.value)
    }

    @Test
    fun onCleared_keepsSessionStateButCancelsRouteJobs() = runTest {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            suspendLoad = true,
        )
        val viewModel = dependencies.createViewModel()

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()
        invokeOnCleared(viewModel)
        advanceUntilIdle()

        assertTrue(dependencies.threadLoadCancelled.value)
        assertEquals(1, dependencies.sessionStates.value.size)
    }

    @Test
    fun reloadThread_loadFailure_setsPendingToast() = runTest {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(
            tabs = listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            loadReturnsNull = true,
        )
        val viewModel = dependencies.createViewModel()

        viewModel.reloadThread(threadId.value)
        advanceUntilIdle()

        assertEquals(com.websarva.wings.android.slevo.R.string.thread_load_failed, dependencies.sessionStates.value[threadId.value]?.pendingToastResId)
    }

    @Test
    fun initialLoad_restoresUnreadGroupFromLastReadPosition() = runTest {
        val threadId = ThreadId.of("example.com", "test", "111")
        val tab = threadTab(threadId, "title").copy(
            resCount = 100,
            newResCount = 10,
            lastReadResNo = 100,
            firstNewResNo = 101,
        )
        val dependencies = mockDependencies(
            tabs = listOf(tab),
            selectedTabKey = threadId.value,
            loadedPosts = mutableMapOf(threadId.value to posts(110)),
        )
        val viewModel = dependencies.createViewModel()

        val state = try {
            viewModel.uiStateFor(threadId.value).first { it.posts?.size == 110 }
        } finally {
            invokeOnCleared(viewModel)
        }

        assertEquals(
            listOf(
                ThreadPostGroup(startResNo = 1, endResNo = 100, prevResCount = 0),
                ThreadPostGroup(startResNo = 101, endResNo = 110, prevResCount = 100),
            ),
            state.postGroups,
        )
        assertEquals(1, state.latestArrivalGroupIndex)
        assertEquals(100, state.firstAfterIndex)
    }

    @Test
    fun initialLoad_restoresUnreadGroupWhenFirstNewResNoIsMissing() = runTest {
        val threadId = ThreadId.of("example.com", "test", "112")
        val tab = threadTab(threadId, "title").copy(
            resCount = 100,
            newResCount = 10,
            lastReadResNo = 100,
            firstNewResNo = null,
        )

        val state = loadInitialState(tab, posts(110))

        assertEquals(1, state.latestArrivalGroupIndex)
        assertEquals(100, state.firstAfterIndex)
    }

    @Test
    fun initialLoad_doesNotShowArrivalBarWithoutValidUnreadBoundary() = runTest {
        val unvisitedId = ThreadId.of("example.com", "test", "113")
        val allReadId = ThreadId.of("example.com", "test", "114")
        val insufficientId = ThreadId.of("example.com", "test", "115")

        val unvisited = loadInitialState(threadTab(unvisitedId, "unvisited"), posts(110))
        val allRead = loadInitialState(
            threadTab(allReadId, "all read").copy(
                resCount = 110,
                lastReadResNo = 110,
            ),
            posts(110),
        )
        val insufficient = loadInitialState(
            threadTab(insufficientId, "insufficient").copy(
                resCount = 100,
                newResCount = 10,
                lastReadResNo = 100,
            ),
            posts(90),
        )

        listOf(unvisited, allRead, insufficient).forEach { state ->
            assertEquals(null, state.latestArrivalGroupIndex)
            assertEquals(-1, state.firstAfterIndex)
        }
    }

    @Test
    fun initialLoad_treatsAllResponsesAsUnreadWhenBoundaryIsOne() = runTest {
        val threadId = ThreadId.of("example.com", "test", "116")
        val tab = threadTab(threadId, "title").copy(
            newResCount = 10,
            lastReadResNo = 0,
        )

        val state = loadInitialState(tab, posts(10))

        assertEquals(
            listOf(ThreadPostGroup(startResNo = 1, endResNo = 10, prevResCount = 0)),
            state.postGroups,
        )
        assertEquals(0, state.latestArrivalGroupIndex)
        assertEquals(0, state.firstAfterIndex)
    }

    @Test
    fun initialLoad_keepsBoundaryAfterReadStateFlowChanges() = runTest {
        val threadId = ThreadId.of("example.com", "test", "117")
        val tab = threadTab(threadId, "title").copy(
            resCount = 100,
            newResCount = 10,
            lastReadResNo = 100,
        )
        val dependencies = mockDependencies(
            tabs = listOf(tab),
            selectedTabKey = threadId.value,
            loadedPosts = mutableMapOf(threadId.value to posts(110)),
        )
        val viewModel = dependencies.createViewModel()
        val stateFlow = viewModel.uiStateFor(threadId.value)
        try {
            val initial = stateFlow.first { it.posts?.size == 110 }

            dependencies.openTabs.value = listOf(tab.copy(lastReadResNo = 110, newResCount = 0))
            advanceUntilIdle()

            val updated = stateFlow.value
            assertEquals(initial.postGroups, updated.postGroups)
            assertEquals(initial.firstAfterIndex, updated.firstAfterIndex)
            assertEquals(100, updated.firstAfterIndex)
        } finally {
            invokeOnCleared(viewModel)
        }
    }

    @Test
    fun reload_movesArrivalBarToNewestAppendedGroupAndClearsOnNoChange() = runTest {
        val threadId = ThreadId.of("example.com", "test", "118")
        val tab = threadTab(threadId, "title").copy(
            resCount = 100,
            newResCount = 10,
            lastReadResNo = 100,
        )
        val loadedPosts = mutableMapOf(threadId.value to posts(110))
        val dependencies = mockDependencies(
            tabs = listOf(tab),
            selectedTabKey = threadId.value,
            loadedPosts = loadedPosts,
        )
        val viewModel = dependencies.createViewModel()
        val stateFlow = viewModel.uiStateFor(threadId.value)
        try {
            stateFlow.first { it.posts?.size == 110 }

            loadedPosts[threadId.value] = posts(115)
            viewModel.reloadThread(threadId.value)
            val appended = stateFlow.first { it.posts?.size == 115 && it.latestArrivalGroupIndex == 2 }

            assertEquals(
                listOf(
                    ThreadPostGroup(startResNo = 1, endResNo = 100, prevResCount = 0),
                    ThreadPostGroup(startResNo = 101, endResNo = 110, prevResCount = 100),
                    ThreadPostGroup(startResNo = 111, endResNo = 115, prevResCount = 110),
                ),
                appended.postGroups,
            )
            assertEquals(110, appended.firstAfterIndex)

            viewModel.reloadThread(threadId.value)
            advanceUntilIdle()

            assertEquals(null, stateFlow.value.latestArrivalGroupIndex)
            assertEquals(-1, stateFlow.value.firstAfterIndex)
        } finally {
            invokeOnCleared(viewModel)
        }
    }

    @Test
    fun updateSearchInput_preservesComposition() {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(listOf(threadTab(threadId, "title")), selectedTabKey = threadId.value)
        val viewModel = dependencies.createViewModel()
        val input = TextFieldValue(text = "かな", selection = TextRange(2), composition = TextRange(0, 2))

        viewModel.updateSearchInput(threadId.value, input)

        assertEquals(input, dependencies.sessionStates.value[threadId.value]?.searchInputValue)
        assertEquals("かな", dependencies.sessionStates.value[threadId.value]?.searchQuery)
    }

    @Test
    fun closeSearch_clearsSearchAndRestoresSwipe() {
        val threadId = ThreadId.of("example.com", "test", "111")
        val dependencies = mockDependencies(
            listOf(threadTab(threadId, "title")),
            selectedTabKey = threadId.value,
            initialSessionStates = mapOf(
                threadId.value to ThreadSessionState(
                    isSearchMode = true,
                    searchInputValue = TextFieldValue("query"),
                    isTabSwipeEnabled = false,
                )
            )
        )
        val viewModel = dependencies.createViewModel()

        viewModel.closeSearch(threadId.value)

        val state = dependencies.sessionStates.value[threadId.value]!!
        assertEquals(TextFieldValue(""), state.searchInputValue)
        assertEquals(false, state.isSearchMode)
        assertEquals(true, state.isTabSwipeEnabled)
    }

    /** テスト用依存一式を構築する。 */
    private fun mockDependencies(
        tabs: List<ThreadTabInfo>,
        selectedTabKey: String?,
        initialSessionStates: Map<String, ThreadSessionState> = tabs.associate { it.id.value to ThreadSessionState() },
        loadedPosts: MutableMap<String, List<ThreadPostUiModel>> = mutableMapOf(),
        suspendLoad: Boolean = false,
        loadReturnsNull: Boolean = false,
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
        every { store.threadPostDialogController(any()) } returns mockk<PostDialogController>(relaxed = true)
        every { store.threadPostDialogSuccessEvents(any()) } returns MutableSharedFlow()
        every { store.getThreadSessionState(any()) } answers {
            val threadKey = threadKey(firstArg())
            sessionStates.value[threadKey] ?: ThreadSessionState()
        }
        every { store.updateThreadSessionState(any(), any()) } answers {
            val threadId = threadKey(firstArg())
            val transform = secondArg<(ThreadSessionState) -> ThreadSessionState>()
            sessionStates.value = sessionStates.value + (threadId to transform(sessionStates.value[threadId] ?: ThreadSessionState()))
        }
        every { store.getThreadRuntimeState(any()) } answers {
            val threadKey = threadKey(firstArg())
            runtimeStates.value[threadKey] ?: ThreadSessionRuntimeState()
        }
        every { store.updateThreadRuntimeState(any(), any()) } answers {
            val threadId = threadKey(firstArg())
            val transform = secondArg<(ThreadSessionRuntimeState) -> ThreadSessionRuntimeState>()
            runtimeStates.value = runtimeStates.value + (threadId to transform(runtimeStates.value[threadId] ?: ThreadSessionRuntimeState()))
        }
        coEvery { store.updateThreadResolvedBoardInfo(any(), any(), any()) } answers {
            val threadId = firstArg<ThreadId>()
            val boardId = secondArg<Long>()
            val boardName = thirdArg<String?>()
            openTabs.value = openTabs.value.map { tab ->
                if (tab.id == threadId) {
                    tab.copy(
                        boardId = boardId,
                        boardName = boardName?.takeIf(String::isNotBlank) ?: tab.boardName,
                    )
                } else {
                    tab
                }
            }
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
        val threadLoadCancelled = MutableStateFlow(false)
        tabs.forEach { tab ->
            coEvery { threadContentLoadUseCase.load(tab.boardUrl, tab.threadKey, any()) } coAnswers {
                if (suspendLoad) {
                    suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation { threadLoadCancelled.value = true }
                    }
                } else if (loadReturnsNull) {
                    null
                } else {
                    val posts = loadedPosts[tab.threadKey].orEmpty()
                    ThreadContentLoadResult(
                        uiPosts = posts,
                        threadTitle = tab.title,
                        resCount = posts.size,
                        threadDate = ThreadDate(2024, 1, 1, 0, 0, "月"),
                        momentum = 0.0,
                        idCountMap = emptyMap(),
                        idIndexList = posts.indices.map { it + 1 },
                        replySourceMap = emptyMap(),
                        treeOrder = posts.indices.map { it + 1 },
                        treeDepthMap = emptyMap(),
                        treeRootMap = emptyMap(),
                    )
                }
            }
        }

        return RouteDependencies(
            store = store,
            openTabs = openTabs,
            sessionStates = sessionStates,
            threadLoadCancelled = threadLoadCancelled,
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
        val openTabs: MutableStateFlow<List<ThreadTabInfo>>,
        val sessionStates: MutableStateFlow<Map<String, ThreadSessionState>>,
        val threadLoadCancelled: MutableStateFlow<Boolean>,
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
    private fun threadTab(threadId: ThreadId, title: String, boardId: Long = 1L): ThreadTabInfo {
        return ThreadTabInfo(
            id = threadId,
            title = title,
            boardName = "board",
            boardUrl = "https://example.com/test/",
            boardId = boardId,
        )
    }

    /** 指定件数の投稿を作り、初回・追加ロードのレス範囲を再現する。 */
    private fun posts(count: Int): List<ThreadPostUiModel> {
        return (1..count).map { number ->
            ThreadPostUiModel(
                header = ThreadPostUiModel.Header(
                    name = "name",
                    email = "",
                    date = "2024/01/01 00:00:00",
                    id = "id$number",
                ),
                body = ThreadPostUiModel.Body(content = "post $number"),
            )
        }
    }

    /** テスト用タブをロードし、初回ロード完了後のUI状態を返す。 */
    private suspend fun loadInitialState(
        tab: ThreadTabInfo,
        posts: List<ThreadPostUiModel>,
    ): ThreadUiState {
        val dependencies = mockDependencies(
            tabs = listOf(tab),
            selectedTabKey = tab.id.value,
            loadedPosts = mutableMapOf(tab.id.value to posts),
        )
        val viewModel = dependencies.createViewModel()
        return try {
            viewModel.uiStateFor(tab.id.value).first { it.posts != null }
        } finally {
            invokeOnCleared(viewModel)
        }
    }

    /** ViewModelの内部scopeを含めてテスト用ViewModelを解放する。 */
    private fun invokeOnCleared(viewModel: ThreadRouteViewModel) {
        viewModel.viewModelScope.cancel()
        val method = ThreadRouteViewModel::class.java.getDeclaredMethod("onCleared")
        method.isAccessible = true
        method.invoke(viewModel)
    }

    /** MockK 引数から thread key を取り出す。 */
    private fun threadKey(arg: Any?): String {
        return when (arg) {
            is ThreadId -> arg.value
            is String -> arg
            else -> error("Unexpected thread key argument: $arg")
        }
    }
}
