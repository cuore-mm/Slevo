package com.websarva.wings.android.slevo.ui.board.viewmodel

import androidx.compose.ui.text.input.TextFieldValue
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolderFactory
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogImageUploader
import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.ui.common.postdialog.ThreadCreatePostDialogExecutor
import com.websarva.wings.android.slevo.ui.tabs.coordinator.BoardTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.registry.TabViewModelRegistry
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
 * [BoardViewModel] の読み込み失敗時の Toast イベント発行を検証するテスト。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        repository: BoardRepository = mockk(relaxed = true),
        bookmarkBoardRepository: BookmarkBoardRepository = mockk(relaxed = true),
        ngRepository: NgRepository = mockk(relaxed = true),
        settingsRepository: SettingsRepository = mockk(relaxed = true),
        bookmarkSheetStateHolderFactory: BookmarkBottomSheetStateHolderFactory = mockk(relaxed = true),
        boardTabsCoordinator: BoardTabsCoordinator = BoardTabsCoordinator(
            tabsRepository = mockk(relaxed = true),
            bookmarkBoardRepository = bookmarkBoardRepository,
            tabViewModelRegistry = mockk<TabViewModelRegistry>(relaxed = true),
        ),
        threadListCoordinatorFactory: ThreadListCoordinator.Factory = mockk(relaxed = true),
        postDialogControllerFactory: PostDialogController.Factory = mockk(relaxed = true),
        threadCreatePostDialogExecutor: ThreadCreatePostDialogExecutor = mockk(relaxed = true),
        postDialogImageUploaderFactory: PostDialogImageUploader.Factory = mockk(relaxed = true),
        logger: AppLogger = mockk(relaxed = true),
    ): BoardViewModel {
        every { settingsRepository.observeGestureSettings() } returns flowOf(GestureSettings.DEFAULT)
        every { ngRepository.observeNgs() } returns flowOf(emptyList())
        every { bookmarkBoardRepository.getBoardWithBookmarkAndGroupByUrlFlow(any()) } returns flowOf(null)

        val bookmarkSheetHolder = mockk<BookmarkBottomSheetStateHolder>(relaxed = true)
        every { bookmarkSheetHolder.uiState } returns MutableStateFlow(BookmarkSheetUiState())
        every { bookmarkSheetStateHolderFactory.create(any()) } returns bookmarkSheetHolder

        val threadListCoordinator = mockk<ThreadListCoordinator>(relaxed = true)
        every { threadListCoordinatorFactory.create(any(), any()) } returns threadListCoordinator

        val postDialogController = mockk<PostDialogController>(relaxed = true)
        every {
            postDialogControllerFactory.create(any(), any(), any(), any(), any(), any())
        } returns postDialogController

        every { postDialogImageUploaderFactory.create(any(), any()) } returns mockk(relaxed = true)

        return BoardViewModel(
            repository = repository,
            bookmarkBoardRepository = bookmarkBoardRepository,
            ngRepository = ngRepository,
            settingsRepository = settingsRepository,
            boardTabsCoordinator = boardTabsCoordinator,
            bookmarkSheetStateHolderFactory = bookmarkSheetStateHolderFactory,
            threadListCoordinatorFactory = threadListCoordinatorFactory,
            postDialogControllerFactory = postDialogControllerFactory,
            threadCreatePostDialogExecutor = threadCreatePostDialogExecutor,
            postDialogImageUploaderFactory = postDialogImageUploaderFactory,
            logger = logger,
            viewModelKey = "test",
        )
    }

    @Test
    fun loadData_refreshThreadListReturnsFalse_setsPendingToast() = runTest {
        val repository = mockk<BoardRepository>(relaxed = true)
        coEvery { repository.ensureBoard(any()) } returns 1L
        coEvery { repository.fetchBoardNoname(any()) } returns null
        coEvery {
            repository.refreshThreadList(any(), any(), any(), any(), any())
        } returns false

        val viewModel = createViewModel(repository = repository)
        viewModel.initializeFlow(BoardInitArgs(BoardInfo(0, "test", "https://example.com/test/")))
        advanceUntilIdle()

        assertEquals(R.string.board_load_failed, viewModel.uiState.value.pendingToastResId)
    }

    @Test
    fun loadData_refreshThreadListThrows_setsPendingToast() = runTest {
        val repository = mockk<BoardRepository>(relaxed = true)
        coEvery { repository.ensureBoard(any()) } returns 1L
        coEvery { repository.fetchBoardNoname(any()) } returns null
        coEvery {
            repository.refreshThreadList(any(), any(), any(), any(), any())
        } throws IOException("network error")

        val viewModel = createViewModel(repository = repository)
        viewModel.initializeFlow(BoardInitArgs(BoardInfo(0, "test", "https://example.com/test/")))
        advanceUntilIdle()

        assertEquals(R.string.board_load_failed, viewModel.uiState.value.pendingToastResId)
    }

    @Test
    fun consumeToast_clearsPendingToast() = runTest {
        val repository = mockk<BoardRepository>(relaxed = true)
        coEvery { repository.ensureBoard(any()) } returns 1L
        coEvery { repository.fetchBoardNoname(any()) } returns null
        coEvery {
            repository.refreshThreadList(any(), any(), any(), any(), any())
        } returns false

        val viewModel = createViewModel(repository = repository)
        viewModel.initializeFlow(BoardInitArgs(BoardInfo(0, "test", "https://example.com/test/")))
        advanceUntilIdle()
        viewModel.consumeToast()

        assertNull(viewModel.uiState.value.pendingToastResId)
    }

    @Test
    fun openBoardInfoSheet_setsShowBoardInfoSheetTrue() = runTest {
        val viewModel = createViewModel()
        viewModel.initializeFlow(BoardInitArgs(BoardInfo(0, "test", "https://example.com/test/")))
        advanceUntilIdle()

        viewModel.openBoardInfoSheet()
        assertEquals(true, viewModel.uiState.value.showBoardInfoSheet)
    }

    @Test
    fun closeBoardInfoSheet_setsShowBoardInfoSheetFalse() = runTest {
        val viewModel = createViewModel()
        viewModel.initializeFlow(BoardInitArgs(BoardInfo(0, "test", "https://example.com/test/")))
        advanceUntilIdle()

        viewModel.openBoardInfoSheet()
        viewModel.closeBoardInfoSheet()
        assertEquals(false, viewModel.uiState.value.showBoardInfoSheet)
    }

    @Test
    fun initializeFlow_restoresSessionStateFromCoordinator() = runTest {
        val repository = mockk<BoardRepository>(relaxed = true)
        coEvery { repository.ensureBoard(any()) } returns 1L
        coEvery { repository.fetchBoardNoname(any()) } returns null
        coEvery { repository.refreshThreadList(any(), any(), any(), any(), any()) } returns true
        val bookmarkBoardRepository = mockk<BookmarkBoardRepository>(relaxed = true)
        val coordinator = BoardTabsCoordinator(
            tabsRepository = mockk(relaxed = true),
            bookmarkBoardRepository = bookmarkBoardRepository,
            tabViewModelRegistry = mockk(relaxed = true),
        )
        coordinator.updateBoardSessionState("https://example.com/test/") {
            it.copy(
                searchInputValue = TextFieldValue("query"),
                isSearchActive = true,
                currentSortKey = ThreadSortKey.RES_COUNT,
                isSortAscending = true,
                showSortSheet = true,
                showBoardInfoSheet = true,
                postDialogState = PostDialogState(namePlaceholder = "名無しさん", formState = PostDialogState().formState.copy(message = "draft")),
                isTabSwipeEnabled = false,
            )
        }
        val viewModel = createViewModel(
            repository = repository,
            bookmarkBoardRepository = bookmarkBoardRepository,
            boardTabsCoordinator = coordinator,
        )

        viewModel.initializeFlow(BoardInitArgs(BoardInfo(0, "test", "https://example.com/test/")))
        advanceUntilIdle()

        assertEquals("query", viewModel.uiState.value.searchQuery)
        assertEquals(true, viewModel.uiState.value.isSearchActive)
        assertEquals(ThreadSortKey.RES_COUNT, viewModel.uiState.value.currentSortKey)
        assertEquals(true, viewModel.uiState.value.isSortAscending)
        assertEquals(true, viewModel.uiState.value.showSortSheet)
        assertEquals(true, viewModel.uiState.value.showBoardInfoSheet)
        assertEquals("draft", viewModel.uiState.value.postDialogState.formState.message)
        assertEquals(false, viewModel.uiState.value.isTabSwipeEnabled)
    }
}
