package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.NgRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadReadStateRepository
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolder
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkBottomSheetStateHolderFactory
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkSheetUiState
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogController
import com.websarva.wings.android.slevo.ui.common.postdialog.PostDialogImageUploader
import com.websarva.wings.android.slevo.ui.common.postdialog.ThreadReplyPostDialogExecutor
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        postDialogImageUploaderFactory: PostDialogImageUploader.Factory = mockk(relaxed = true),
        postDialogControllerFactory: PostDialogController.Factory = mockk(relaxed = true),
        replyPostDialogExecutor: ThreadReplyPostDialogExecutor = mockk(relaxed = true),
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
            datRepository = datRepository,
            boardRepository = boardRepository,
            historyRepository = historyRepository,
            postHistoryRepository = postHistoryRepository,
            threadBookmarkRepository = threadBookmarkRepository,
            bookmarkSheetStateHolderFactory = bookmarkSheetStateHolderFactory,
            ngRepository = ngRepository,
            settingsRepository = settingsRepository,
            tabsRepository = tabsRepository,
            threadReadStateRepository = threadReadStateRepository,
            postDialogImageUploaderFactory = postDialogImageUploaderFactory,
            postDialogControllerFactory = postDialogControllerFactory,
            replyPostDialogExecutor = replyPostDialogExecutor,
            viewModelKey = "test",
        )
    }

    @Test
    fun loadData_datRepositoryReturnsNull_emitsShowToast() = runTest {
        val datRepository = mockk<DatRepository>(relaxed = true)
        coEvery { datRepository.getThread(any(), any(), any()) } returns null

        val boardRepository = mockk<BoardRepository>(relaxed = true)
        coEvery { boardRepository.ensureBoard(any()) } returns 1L
        coEvery { boardRepository.fetchBoardNoname(any()) } returns null

        val viewModel = createViewModel(
            datRepository = datRepository,
            boardRepository = boardRepository,
        )
        val events = mutableListOf<ThreadUiEvent>()
        val job = backgroundScope.launch { viewModel.uiEvents.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.initializeFlow(
            ThreadInitArgs(
                threadKey = "1234567890",
                boardInfo = BoardInfo(0, "test", "https://example.com/test/"),
                threadTitle = null,
            )
        )
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(ThreadUiEvent.ShowToast(R.string.thread_load_failed), events[0])
        job.cancel()
    }

    @Test
    fun loadData_datRepositoryThrows_emitsShowToast() = runTest {
        val datRepository = mockk<DatRepository>(relaxed = true)
        coEvery { datRepository.getThread(any(), any(), any()) } throws IOException("network error")

        val boardRepository = mockk<BoardRepository>(relaxed = true)
        coEvery { boardRepository.ensureBoard(any()) } returns 1L
        coEvery { boardRepository.fetchBoardNoname(any()) } returns null

        val viewModel = createViewModel(
            datRepository = datRepository,
            boardRepository = boardRepository,
        )
        val events = mutableListOf<ThreadUiEvent>()
        val job = backgroundScope.launch { viewModel.uiEvents.collect { events.add(it) } }
        advanceUntilIdle()

        viewModel.initializeFlow(
            ThreadInitArgs(
                threadKey = "1234567890",
                boardInfo = BoardInfo(0, "test", "https://example.com/test/"),
                threadTitle = null,
            )
        )
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(ThreadUiEvent.ShowToast(R.string.thread_load_failed), events[0])
        job.cancel()
    }
}
