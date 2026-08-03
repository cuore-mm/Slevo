package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
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
import com.websarva.wings.android.slevo.data.util.ThreadNewResCalculator
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionRuntimeState
import com.websarva.wings.android.slevo.ui.tabs.session.ThreadSessionState
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostGroup
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
 * 直接合成化後の lazy load、reload、自動スクロールの委譲条件とレスグループ境界を検証する。
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
    fun initialLoad_restoresUnreadGroupFromLastReadPosition() {
        val state = updateThreadPostGroups(
            previousGroups = emptyList(),
            previousResCount = 0,
            posts = posts(110),
            initialUnreadStartResNo = 101,
            isInitialLoad = true,
        )

        assertEquals(
            listOf(
                ThreadPostGroup(startResNo = 1, endResNo = 100, prevResCount = 0),
                ThreadPostGroup(startResNo = 101, endResNo = 110, prevResCount = 100),
            ),
            state.groups,
        )
        assertEquals(1, state.latestArrivalGroupIndex)

        val visible = ThreadVisiblePostsUseCase().buildVisibleRows(
            posts = posts(110),
            groups = state.groups,
            sortType = com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType.NUMBER,
            treeOrder = emptyList(),
            treeDepthMap = emptyMap(),
            treeRootMap = emptyMap(),
            latestArrivalGroupIndex = state.latestArrivalGroupIndex,
            searchQuery = "",
            ngPostNumbers = emptySet(),
            replySourceMap = emptyMap(),
        )
        assertEquals(100, visible.firstAfterIndex)
    }

    @Test
    fun initialLoad_restoresUnreadGroupWhenFirstNewResNoIsMissing() {
        val readState = ThreadReadState(lastReadResNo = 100, firstNewResNo = null)
        val initialUnreadStartResNo = (ThreadNewResCalculator.calculate(110, readState) > 0)
            .let { hasUnread -> if (hasUnread) readState.lastReadResNo + 1 else null }

        val state = updateThreadPostGroups(
            previousGroups = emptyList(),
            previousResCount = 0,
            posts = posts(110),
            initialUnreadStartResNo = initialUnreadStartResNo,
            isInitialLoad = true,
        )

        assertEquals(1, state.latestArrivalGroupIndex)
        assertEquals(101, state.groups[1].startResNo)
    }

    @Test
    fun initialLoad_doesNotShowArrivalBarWithoutValidUnreadBoundary() {
        val states = listOf(
            updateThreadPostGroups(emptyList(), 0, posts(110), null, true),
            updateThreadPostGroups(emptyList(), 0, posts(110), null, true),
            updateThreadPostGroups(emptyList(), 0, posts(90), 101, true),
        )

        states.forEach { state ->
            assertEquals(null, state.latestArrivalGroupIndex)
            val visible = ThreadVisiblePostsUseCase().buildVisibleRows(
                posts = posts(state.lastLoadedResCount),
                groups = state.groups,
                sortType = com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType.NUMBER,
                treeOrder = emptyList(),
                treeDepthMap = emptyMap(),
                treeRootMap = emptyMap(),
                latestArrivalGroupIndex = state.latestArrivalGroupIndex,
                searchQuery = "",
                ngPostNumbers = emptySet(),
                replySourceMap = emptyMap(),
            )
            assertEquals(-1, visible.firstAfterIndex)
        }
    }

    @Test
    fun initialLoad_treatsAllResponsesAsUnreadWhenBoundaryIsOne() {
        val state = updateThreadPostGroups(emptyList(), 0, posts(10), 1, true)

        assertEquals(
            listOf(ThreadPostGroup(startResNo = 1, endResNo = 10, prevResCount = 0)),
            state.groups,
        )
        assertEquals(0, state.latestArrivalGroupIndex)
    }

    @Test
    fun initialLoad_keepsBoundaryAfterReadStateChanges() {
        val initial = updateThreadPostGroups(emptyList(), 0, posts(110), 101, true)
        val afterRead = initial.copy()

        assertEquals(initial.groups, afterRead.groups)
        assertEquals(initial.latestArrivalGroupIndex, afterRead.latestArrivalGroupIndex)
    }

    @Test
    fun reload_movesArrivalBarToNewestAppendedGroupAndClearsOnNoChange() {
        val initial = updateThreadPostGroups(emptyList(), 0, posts(110), 101, true)
        val appended = updateThreadPostGroups(initial.groups, 110, posts(115), 101, false)
        val unchanged = updateThreadPostGroups(appended.groups, 115, posts(115), 101, false)

        assertEquals(2, appended.latestArrivalGroupIndex)
        assertEquals(110, appended.groups.last().startResNo - 1)
        assertEquals(null, unchanged.latestArrivalGroupIndex)
    }

    @Test
    fun recoveryAfterEmptyReload_doesNotReuseInitialBoundary() {
        val initial = updateThreadPostGroups(emptyList(), 0, posts(110), 101, true)
        val empty = updateThreadPostGroups(initial.groups, 110, emptyList(), 101, false)
        val recovered = updateThreadPostGroups(empty.groups, 0, posts(110), 101, false)

        assertEquals(
            listOf(ThreadPostGroup(startResNo = 1, endResNo = 110, prevResCount = 0)),
            recovered.groups,
        )
        assertEquals(null, recovered.latestArrivalGroupIndex)
        assertEquals(-1, firstAfterIndex(posts(110), recovered))
    }

    @Test
    fun recoveryAfterInitialEmptyLoad_doesNotReuseInitialBoundary() {
        val empty = updateThreadPostGroups(emptyList(), 0, emptyList(), 101, true)
        val recovered = updateThreadPostGroups(empty.groups, 0, posts(110), 101, false)

        assertEquals(
            listOf(ThreadPostGroup(startResNo = 1, endResNo = 110, prevResCount = 0)),
            recovered.groups,
        )
        assertEquals(null, recovered.latestArrivalGroupIndex)
        assertEquals(-1, firstAfterIndex(posts(110), recovered))
    }

    @Test
    fun recoveryAfterNonZeroCountDecrease_doesNotReuseInitialBoundary() {
        val initial = updateThreadPostGroups(emptyList(), 0, posts(110), 101, true)
        val decreased = updateThreadPostGroups(initial.groups, 110, posts(90), 101, false)

        assertEquals(
            listOf(ThreadPostGroup(startResNo = 1, endResNo = 90, prevResCount = 0)),
            decreased.groups,
        )
        assertEquals(null, decreased.latestArrivalGroupIndex)
        assertEquals(-1, firstAfterIndex(posts(90), decreased))
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
                    ThreadContentLoadResult(
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

    /** グループ結果を表示変換へ渡し、新着バー挿入位置を返す。 */
    private fun firstAfterIndex(
        posts: List<ThreadPostUiModel>,
        state: ThreadRoutePostGroupState,
    ): Int {
        return ThreadVisiblePostsUseCase().buildVisibleRows(
            posts = posts,
            groups = state.groups,
            sortType = com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType.NUMBER,
            treeOrder = emptyList(),
            treeDepthMap = emptyMap(),
            treeRootMap = emptyMap(),
            latestArrivalGroupIndex = state.latestArrivalGroupIndex,
            searchQuery = "",
            ngPostNumbers = emptySet(),
            replySourceMap = emptyMap(),
        ).firstAfterIndex
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
