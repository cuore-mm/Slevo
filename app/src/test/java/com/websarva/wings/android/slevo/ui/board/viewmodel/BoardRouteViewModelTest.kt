package com.websarva.wings.android.slevo.ui.board.viewmodel

import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.session.BoardSessionState
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * [BoardRouteViewModel] の軽量回帰テスト。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardRouteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiStateFor_sameKeyReusesCachedFlow() {
        val tab = boardTab("https://example.com/test/", "board")
        val dependencies = mockDependencies(listOf(tab), tab.boardUrl)
        val viewModel = dependencies.createViewModel()

        val first = viewModel.uiStateFor(tab.boardUrl)
        val second = viewModel.uiStateFor(tab.boardUrl)

        assertSame(first, second)
    }

    @Test
    fun refreshBoard_callsRepositoryRefresh() = runTest {
        val tab = boardTab("https://example.com/test/", "board")
        val dependencies = mockDependencies(listOf(tab), tab.boardUrl)
        val viewModel = dependencies.createViewModel()

        viewModel.refreshBoard(tab.boardUrl)
        advanceUntilIdle()

        coVerify(atLeast = 1) {
            dependencies.boardRepository.refreshThreadList(
                boardId = tab.boardId,
                subjectUrl = "https://example.com/test/subject.txt",
                refreshStartAt = any(),
                isManual = true,
                onProgress = any(),
            )
        }
    }

    @Test
    fun refreshOpenBoardUiState_isDirectlySynthesized() = runTest {
        val tab = boardTab("https://example.com/test/", "board")
        val dependencies = mockDependencies(listOf(tab), tab.boardUrl)
        val viewModel = dependencies.createViewModel()

        viewModel.uiStateFor(tab.boardUrl)
        advanceUntilIdle()

        verify(exactly = 1) { dependencies.boardRepository.observeThreads(tab.boardId) }
    }

    private fun mockDependencies(
        tabs: List<BoardTabInfo>,
        selectedTabKey: String?,
    ): RouteDependencies {
        val openTabs = MutableStateFlow(tabs)
        val selectedKey = MutableStateFlow<String?>(selectedTabKey)
        val sessionStates = MutableStateFlow(tabs.associate { it.boardUrl to BoardSessionState() })
        val store = mockk<TabSessionStore>(relaxed = true)
        every { store.openBoardTabs } returns openTabs
        every { store.selectedBoardTabKey } returns selectedKey
        every { store.boardSessionStates } returns sessionStates
        every { store.boardBookmarkSheetHolder(any()).uiState } returns MutableStateFlow(com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState())
        every { store.boardPostDialogController(any()) } returns mockk(relaxed = true)
        every { store.boardPostDialogSuccessEvents(any()) } returns MutableSharedFlow()
        every { store.getBoardSessionState(any()) } answers { sessionStates.value[firstArg<String>()] ?: BoardSessionState() }
        every { store.updateBoardSessionState(any(), any()) } answers {
            val key = firstArg<String>()
            val transform = secondArg<(BoardSessionState) -> BoardSessionState>()
            sessionStates.value = sessionStates.value + (key to transform(sessionStates.value[key] ?: BoardSessionState()))
        }

        val boardRepository = mockk<BoardRepository>()
        tabs.forEach { tab ->
            every { boardRepository.observeThreads(tab.boardId) } returns flowOf(listOf(ThreadInfo(title = tab.boardName, key = "1")))
            coEvery {
                boardRepository.refreshThreadList(
                    boardId = tab.boardId,
                    subjectUrl = any(),
                    refreshStartAt = any(),
                    isManual = any(),
                    onProgress = any(),
                )
            } returns true
        }
        coEvery { boardRepository.ensureBoard(any()) } answers { firstArg<BoardInfo>().boardId.takeIf { it != 0L } ?: 1L }
        coEvery { boardRepository.fetchBoardNoname(any()) } returns null

        val bookmarkBoardRepository = mockk<BookmarkBoardRepository>()
        every { bookmarkBoardRepository.getBoardWithBookmarkAndGroupByUrlFlow(any()) } returns flowOf(null)

        val ngRepository = mockk<NgRepository>()
        every { ngRepository.observeNgs() } returns flowOf(emptyList())

        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.observeGestureSettings() } returns flowOf(com.websarva.wings.android.slevo.data.model.GestureSettings.DEFAULT)

        val historyRepository = mockk<ThreadHistoryRepository>()
        every { historyRepository.observeHistoryReadStateMap(any()) } returns flowOf(emptyMap())

        val threadStateRepository = mockk<ThreadStateRepository>()
        every { threadStateRepository.observeThreadStateMapByBoard(any()) } returns flowOf(emptyMap())

        return RouteDependencies(
            store = store,
            boardRepository = boardRepository,
            bookmarkBoardRepository = bookmarkBoardRepository,
            ngRepository = ngRepository,
            settingsRepository = settingsRepository,
            historyRepository = historyRepository,
            threadStateRepository = threadStateRepository,
            boardThreadListTransformUseCase = BoardThreadListTransformUseCase(),
            logger = mockk(relaxed = true),
        )
    }

    private data class RouteDependencies(
        val store: TabSessionStore,
        val boardRepository: BoardRepository,
        val bookmarkBoardRepository: BookmarkBoardRepository,
        val ngRepository: NgRepository,
        val settingsRepository: SettingsRepository,
        val historyRepository: ThreadHistoryRepository,
        val threadStateRepository: ThreadStateRepository,
        val boardThreadListTransformUseCase: BoardThreadListTransformUseCase,
        val logger: AppLogger,
    ) {
        fun createViewModel(): BoardRouteViewModel {
            return BoardRouteViewModel(
                tabSessionStore = store,
                boardRepository = boardRepository,
                bookmarkBoardRepository = bookmarkBoardRepository,
                ngRepository = ngRepository,
                settingsRepository = settingsRepository,
                historyRepository = historyRepository,
                threadStateRepository = threadStateRepository,
                boardThreadListTransformUseCase = boardThreadListTransformUseCase,
                logger = logger,
            )
        }
    }

    private fun boardTab(url: String, name: String): BoardTabInfo {
        return BoardTabInfo(
            boardId = 1L,
            boardName = name,
            boardUrl = url,
            serviceName = "5ch",
        )
    }
}
