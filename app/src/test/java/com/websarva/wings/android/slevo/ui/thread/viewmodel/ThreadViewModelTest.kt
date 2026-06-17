package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadReadStateRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolderFactory
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogImageUploader
import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.ui.common.postdialog.ThreadReplyPostDialogExecutor
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.registry.TabViewModelRegistry
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * [ThreadViewModel] の読み込み失敗時の Toast イベント発行を検証するテスト。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        datRepository: DatRepository = mockk(relaxed = true),
        boardRepository: BoardRepository = mockk(relaxed = true),
        historyRepository: ThreadHistoryRepository = mockk(relaxed = true),
        postHistoryRepository: PostHistoryRepository = mockk(relaxed = true),
        threadBookmarkRepository: ThreadBookmarkRepository = mockk(relaxed = true),
        bookmarkSheetStateHolderFactory: BookmarkBottomSheetStateHolderFactory = mockk(relaxed = true),
        ngRepository: NgRepository = mockk(relaxed = true),
        settingsRepository: SettingsRepository = mockk(relaxed = true),
        tabsRepository: TabsRepository = mockk(relaxed = true),
        threadReadStateRepository: ThreadReadStateRepository = mockk(relaxed = true),
        threadStateRepository: ThreadStateRepository = mockk(relaxed = true),
        postDialogImageUploaderFactory: PostDialogImageUploader.Factory = mockk(relaxed = true),
        postDialogControllerFactory: PostDialogController.Factory = mockk(relaxed = true),
        replyPostDialogExecutor: ThreadReplyPostDialogExecutor = mockk(relaxed = true),
        logger: AppLogger = mockk(relaxed = true),
        threadContentLoadUseCase: ThreadContentLoadUseCase = ThreadContentLoadUseCase(datRepository),
        threadVisiblePostsUseCase: ThreadVisiblePostsUseCase = ThreadVisiblePostsUseCase(),
        threadTabsCoordinator: ThreadTabsCoordinator = ThreadTabsCoordinator(
            tabsRepository = tabsRepository,
            threadBookmarkRepository = threadBookmarkRepository,
            datRepository = datRepository,
            threadStateRepository = threadStateRepository,
            tabViewModelRegistry = mockk<TabViewModelRegistry>(relaxed = true),
        ),
    ): ThreadViewModel {
        every { settingsRepository.observeTextScale() } returns flowOf(1.0f)
        every { settingsRepository.observeIsIndividualTextScale() } returns flowOf(false)
        every { settingsRepository.observeHeaderTextScale() } returns flowOf(1.0f)
        every { settingsRepository.observeBodyTextScale() } returns flowOf(1.0f)
        every { settingsRepository.observeLineHeight() } returns flowOf(1.5f)
        every { settingsRepository.observeIsThreadMinimapScrollbarEnabled() } returns flowOf(false)
        every { settingsRepository.observeGestureSettings() } returns flowOf(GestureSettings.DEFAULT)
        every { settingsRepository.observeIsTreeSort() } returns flowOf(false)

        val bookmarkSheetHolder = mockk<BookmarkBottomSheetStateHolder>(relaxed = true)
        every { bookmarkSheetHolder.uiState } returns MutableStateFlow(BookmarkSheetUiState())
        every { bookmarkSheetStateHolderFactory.create(any()) } returns bookmarkSheetHolder

        every { threadBookmarkRepository.getBookmarkWithGroup(any(), any()) } returns flowOf(null)
        every { ngRepository.observeNgs() } returns flowOf(emptyList())

        every { tabsRepository.observeOpenThreadTabs() } returns flowOf(emptyList())

        val postDialogController = mockk<PostDialogController>(relaxed = true)
        every {
            postDialogControllerFactory.create(any(), any(), any(), any(), any(), any())
        } returns postDialogController

        every { postDialogImageUploaderFactory.create(any(), any()) } returns mockk(relaxed = true)

        return ThreadViewModel(
            boardRepository = boardRepository,
            historyRepository = historyRepository,
            postHistoryRepository = postHistoryRepository,
            threadBookmarkRepository = threadBookmarkRepository,
            bookmarkSheetStateHolderFactory = bookmarkSheetStateHolderFactory,
            ngRepository = ngRepository,
            settingsRepository = settingsRepository,
            tabsRepository = tabsRepository,
            threadTabsCoordinator = threadTabsCoordinator,
            threadContentLoadUseCase = threadContentLoadUseCase,
            threadVisiblePostsUseCase = threadVisiblePostsUseCase,
            threadReadStateRepository = threadReadStateRepository,
            postDialogImageUploaderFactory = postDialogImageUploaderFactory,
            postDialogControllerFactory = postDialogControllerFactory,
            replyPostDialogExecutor = replyPostDialogExecutor,
            logger = logger,
            viewModelKey = "test",
        )
    }

    @Test
    fun loadData_datRepositoryReturnsNull_setsPendingToast() = runTest {
        val datRepository = mockk<DatRepository>(relaxed = true)
        coEvery { datRepository.getThread(any(), any(), any()) } returns null

        val boardRepository = mockk<BoardRepository>(relaxed = true)
        coEvery { boardRepository.ensureBoard(any()) } returns 1L
        coEvery { boardRepository.fetchBoardNoname(any()) } returns null

        val viewModel = createViewModel(
            datRepository = datRepository,
            boardRepository = boardRepository,
        )
        viewModel.initializeFlow(
            ThreadInitArgs(
                threadKey = "1234567890",
                boardInfo = BoardInfo(0, "test", "https://example.com/test/"),
                threadTitle = null,
            )
        )
        advanceUntilIdle()

        assertEquals(R.string.thread_load_failed, viewModel.uiState.value.pendingToastResId)
    }

    @Test
    fun loadData_datRepositoryThrows_setsPendingToast() = runTest {
        val datRepository = mockk<DatRepository>(relaxed = true)
        coEvery { datRepository.getThread(any(), any(), any()) } throws IOException("network error")

        val boardRepository = mockk<BoardRepository>(relaxed = true)
        coEvery { boardRepository.ensureBoard(any()) } returns 1L
        coEvery { boardRepository.fetchBoardNoname(any()) } returns null

        val viewModel = createViewModel(
            datRepository = datRepository,
            boardRepository = boardRepository,
        )
        viewModel.initializeFlow(
            ThreadInitArgs(
                threadKey = "1234567890",
                boardInfo = BoardInfo(0, "test", "https://example.com/test/"),
                threadTitle = null,
            )
        )
        advanceUntilIdle()

        assertEquals(R.string.thread_load_failed, viewModel.uiState.value.pendingToastResId)
    }

    @Test
    fun consumeToast_clearsPendingToast() = runTest {
        val datRepository = mockk<DatRepository>(relaxed = true)
        coEvery { datRepository.getThread(any(), any(), any()) } returns null

        val boardRepository = mockk<BoardRepository>(relaxed = true)
        coEvery { boardRepository.ensureBoard(any()) } returns 1L
        coEvery { boardRepository.fetchBoardNoname(any()) } returns null

        val viewModel = createViewModel(
            datRepository = datRepository,
            boardRepository = boardRepository,
        )
        viewModel.initializeFlow(
            ThreadInitArgs(
                threadKey = "1234567890",
                boardInfo = BoardInfo(0, "test", "https://example.com/test/"),
                threadTitle = null,
            )
        )
        advanceUntilIdle()
        viewModel.consumeToast()

        assertNull(viewModel.uiState.value.pendingToastResId)
    }

    @Test
    fun updateSearchInput_preservesComposition() = runTest {
        val viewModel = createViewModel()
        val inputValue = TextFieldValue(
            text = "かな",
            selection = TextRange(2),
            composition = TextRange(0, 2),
        )

        viewModel.updateSearchInput(inputValue)

        assertEquals(inputValue, viewModel.uiState.value.searchInputValue)
        assertEquals("かな", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun initializeFlow_restoresSessionStateFromCoordinator() = runTest {
        val datRepository = mockk<DatRepository>(relaxed = true)
        coEvery { datRepository.getThread(any(), any(), any()) } returns null
        val boardRepository = mockk<BoardRepository>(relaxed = true)
        coEvery { boardRepository.ensureBoard(any()) } returns 1L
        coEvery { boardRepository.fetchBoardNoname(any()) } returns null
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        every { tabsRepository.observeOpenThreadTabs() } returns flowOf(emptyList())
        val threadBookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val coordinator = ThreadTabsCoordinator(
            tabsRepository = tabsRepository,
            threadBookmarkRepository = threadBookmarkRepository,
            datRepository = datRepository,
            threadStateRepository = mockk(relaxed = true),
            tabViewModelRegistry = mockk(relaxed = true),
        )
        val threadId = ThreadId.of("example.com", "test", "1234567890")
        coordinator.updateThreadSessionState(threadId) {
            it.copy(
                searchInputValue = TextFieldValue("かな", selection = TextRange(2), composition = TextRange(0, 2)),
                isSearchMode = true,
                popupStack = listOf(PopupInfo(popupId = 9L, postNumbers = listOf(1), offset = IntOffset.Zero)),
                postDialogState = PostDialogState(namePlaceholder = "名無しさん", formState = PostDialogState().formState.copy(message = "draft")),
                isAutoScroll = true,
                showImageMenuSheet = true,
                imageMenuTargetUrl = "https://example.com/image.jpg",
                imageMenuTargetUrls = listOf("https://example.com/image.jpg"),
                isTabSwipeEnabled = false,
            )
        }

        val viewModel = createViewModel(
            datRepository = datRepository,
            boardRepository = boardRepository,
            tabsRepository = tabsRepository,
            threadBookmarkRepository = threadBookmarkRepository,
            threadTabsCoordinator = coordinator,
        )

        viewModel.initializeFlow(
            ThreadInitArgs(
                threadKey = "1234567890",
                boardInfo = BoardInfo(0, "test", "https://example.com/test/"),
                threadTitle = null,
            )
        )
        advanceUntilIdle()

        assertEquals("かな", viewModel.uiState.value.searchQuery)
        assertEquals(true, viewModel.uiState.value.isSearchMode)
        assertEquals(1, viewModel.uiState.value.popupStack.size)
        assertEquals("draft", viewModel.uiState.value.postDialogState.formState.message)
        assertEquals(true, viewModel.uiState.value.isAutoScroll)
        assertEquals(true, viewModel.uiState.value.showImageMenuSheet)
        assertEquals("https://example.com/image.jpg", viewModel.uiState.value.imageMenuTargetUrl)
        assertEquals(false, viewModel.uiState.value.isTabSwipeEnabled)
    }
}
