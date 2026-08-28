package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.core.log.AppLogger
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.ThreadHistoryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationStatus
import com.websarva.wings.android.slevo.data.datasource.local.entity.state.ThreadStateEntity
import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.notification.ReplyNotificationPublishResult
import com.websarva.wings.android.slevo.data.notification.ReplyNotificationPublisher
import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.PostHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ReplyNotificationRepository
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Test

/** [ThreadRefreshUseCase] の共通取得順序と通知境界を検証するテスト。 */
class ThreadRefreshUseCaseTest {
    @Test
    fun failedFetchDoesNotChangeStateOrNotifications() = runTest {
        val dependencies = dependencies()
        coEvery {
            dependencies.datRepository.getThread(
                boardUrl = any(),
                threadKey = any(),
                onProgress = any<(Float) -> Unit>(),
            )
        } returns null
        val useCase = dependencies.createUseCase()

        val result = useCase.refresh(request())

        assertEquals(null, result)
        coVerify(exactly = 0) { dependencies.threadStateRepository.saveThreadState(any()) }
        coVerify(exactly = 0) { dependencies.replyNotificationRepository.insertNew(any()) }
    }

    @Test
    fun detectsNewReplyAfterReconcilingOwnPosts() = runTest {
        val dependencies = dependencies()
        val state = ThreadStateEntity(
            threadId = THREAD_ID,
            boardId = 1L,
            boardUrl = BOARD_URL,
            boardName = "Board",
            threadKey = THREAD_KEY,
            title = "Old title",
            latestResCount = 2,
            updatedAt = 1L,
        )
        val history = ThreadHistoryEntity(
            id = 7L,
            threadId = THREAD_ID,
            boardUrl = BOARD_URL,
            boardId = 1L,
            boardName = "Board",
            title = "Old title",
            resCount = 2,
            readState = ThreadReadState(),
        )
        val notification = ReplyNotificationEntity(
            threadId = THREAD_ID,
            replyResNo = 3,
            targetOwnResNumbers = "2",
            boardUrl = BOARD_URL,
            threadKey = THREAD_KEY,
            threadTitle = "New title",
            messagePreview = ">>2 reply",
            detectedAt = 10L,
        )
        coEvery { dependencies.threadStateRepository.getThreadState(THREAD_ID) } returns state
        coEvery { dependencies.threadHistoryRepository.getHistory(THREAD_ID) } returns history
        coEvery { dependencies.postHistoryRepository.getMyPostNumbers(7L) } returns setOf(2)
        coEvery { dependencies.settingsRepository.getIsReplyNotificationEnabled() } returns true
        coEvery { dependencies.replyNotificationRepository.insertNew(any()) } returns listOf(notification)
        coEvery { dependencies.replyNotificationRepository.findDetected(THREAD_ID) } returns listOf(notification)
        every { dependencies.publisher.publish(notification) } returns ReplyNotificationPublishResult.DELIVERED
        coEvery { dependencies.replyNotificationRepository.updateStatus(any(), any(), any(), any()) } returns true
        coEvery {
            dependencies.datRepository.getThread(
                boardUrl = any(),
                threadKey = any(),
                onProgress = any<(Float) -> Unit>(),
            )
        } returns (
            listOf(
                post("root"),
                post("mine"),
                post(">>2 reply"),
            ) to "New title"
        )
        val useCase = dependencies.createUseCase()

        val result = useCase.refresh(request())

        assertEquals(3, result?.posts?.size)
        coVerify(exactly = 1) { dependencies.ownPostReconciliationUseCase.reconcile(any(), any(), 7L, 1L, any()) }
        coVerify(exactly = 1) { dependencies.replyNotificationRepository.insertNew(any()) }
        coVerify(exactly = 1) {
            dependencies.replyNotificationRepository.updateStatus(
                THREAD_ID,
                3,
                ReplyNotificationStatus.DETECTED,
                ReplyNotificationStatus.DELIVERED,
            )
        }
        coVerifyOrder {
            dependencies.threadStateRepository.getThreadState(THREAD_ID)
            dependencies.datRepository.getThread(
                boardUrl = any(),
                threadKey = any(),
                onProgress = any<(Float) -> Unit>(),
            )
            dependencies.ownPostReconciliationUseCase.reconcile(any(), any(), any(), any(), any())
            dependencies.postHistoryRepository.getMyPostNumbers(7L)
            dependencies.replyNotificationRepository.insertNew(any())
            dependencies.threadStateRepository.saveThreadState(any())
            dependencies.replyNotificationRepository.findDetected(THREAD_ID)
            dependencies.publisher.publish(notification)
            dependencies.replyNotificationRepository.updateStatus(any(), any(), any(), any())
        }
    }

    @Test
    fun initialFetchUpdatesStateWithoutCreatingNotifications() = runTest {
        val dependencies = dependencies()
        coEvery { dependencies.threadStateRepository.getThreadState(THREAD_ID) } returns null
        coEvery { dependencies.threadHistoryRepository.getHistory(THREAD_ID) } returns null
        coEvery { dependencies.settingsRepository.getIsReplyNotificationEnabled() } returns true
        coEvery {
            dependencies.datRepository.getThread(
                boardUrl = any(),
                threadKey = any(),
                onProgress = any<(Float) -> Unit>(),
            )
        } returns (
            listOf(
                post("root"),
                post(">>1 old reply"),
            ) to "Title"
        )
        val useCase = dependencies.createUseCase()

        useCase.refresh(request())

        coVerify(exactly = 0) { dependencies.replyNotificationRepository.insertNew(any()) }
        coVerify(exactly = 1) { dependencies.threadStateRepository.saveThreadState(any()) }
    }

    @Test
    fun disabledNotifications_updateStateWithoutPersistingOrPublishing() = runTest {
        val dependencies = dependencies()
        coEvery { dependencies.threadStateRepository.getThreadState(THREAD_ID) } returns state(latestResCount = 2)
        coEvery { dependencies.settingsRepository.getIsReplyNotificationEnabled() } returns false
        coEvery {
            dependencies.datRepository.getThread(
                boardUrl = any(),
                threadKey = any(),
                onProgress = any<(Float) -> Unit>(),
            )
        } returns (listOf(post("root"), post("mine"), post(">>2 reply")) to "Title")

        dependencies.createUseCase().refresh(request())

        coVerify(exactly = 0) { dependencies.replyNotificationRepository.insertNew(any()) }
        coVerify(exactly = 0) { dependencies.publisher.publish(any()) }
        coVerify(exactly = 1) { dependencies.threadStateRepository.saveThreadState(any()) }
    }

    @Test
    fun reducedFetch_doesNotCreateNotificationCandidates() = runTest {
        val dependencies = dependencies()
        coEvery { dependencies.threadStateRepository.getThreadState(THREAD_ID) } returns state(latestResCount = 5)
        coEvery { dependencies.settingsRepository.getIsReplyNotificationEnabled() } returns true
        coEvery {
            dependencies.datRepository.getThread(
                boardUrl = any(),
                threadKey = any(),
                onProgress = any<(Float) -> Unit>(),
            )
        } returns (listOf(post("root"), post(">>1 old reply")) to "Title")

        dependencies.createUseCase().refresh(request())

        coVerify(exactly = 0) { dependencies.replyNotificationRepository.insertNew(any()) }
        coVerify(exactly = 1) { dependencies.threadStateRepository.saveThreadState(any()) }
    }

    @Test
    fun retryPublisher_keepsDetectedStatusForNextRefresh() = runTest {
        val dependencies = dependencies()
        val notification = notification(replyResNo = 3)
        coEvery { dependencies.threadStateRepository.getThreadState(THREAD_ID) } returns state(latestResCount = 2)
        coEvery { dependencies.threadHistoryRepository.getHistory(THREAD_ID) } returns history()
        coEvery { dependencies.postHistoryRepository.getMyPostNumbers(7L) } returns setOf(2)
        coEvery { dependencies.settingsRepository.getIsReplyNotificationEnabled() } returns true
        coEvery {
            dependencies.datRepository.getThread(
                boardUrl = any(),
                threadKey = any(),
                onProgress = any<(Float) -> Unit>(),
            )
        } returns (listOf(post("root"), post("mine"), post(">>2 reply")) to "Title")
        coEvery { dependencies.replyNotificationRepository.insertNew(any()) } returns listOf(notification)
        coEvery { dependencies.replyNotificationRepository.findDetected(THREAD_ID) } returns listOf(notification)
        every { dependencies.publisher.publish(notification) } returns ReplyNotificationPublishResult.RETRY

        dependencies.createUseCase().refresh(request())

        coVerify(exactly = 0) {
            dependencies.replyNotificationRepository.updateStatus(any(), any(), any(), any())
        }
    }

    @Test
    fun suppressedPublisher_marksNotificationAsSuppressed() = runTest {
        val dependencies = dependencies()
        val notification = notification(replyResNo = 3)
        coEvery { dependencies.threadStateRepository.getThreadState(THREAD_ID) } returns state(latestResCount = 2)
        coEvery { dependencies.threadHistoryRepository.getHistory(THREAD_ID) } returns history()
        coEvery { dependencies.postHistoryRepository.getMyPostNumbers(7L) } returns setOf(2)
        coEvery { dependencies.settingsRepository.getIsReplyNotificationEnabled() } returns true
        coEvery {
            dependencies.datRepository.getThread(
                boardUrl = any(),
                threadKey = any(),
                onProgress = any<(Float) -> Unit>(),
            )
        } returns (listOf(post("root"), post("mine"), post(">>2 reply")) to "Title")
        coEvery { dependencies.replyNotificationRepository.insertNew(any()) } returns listOf(notification)
        coEvery { dependencies.replyNotificationRepository.findDetected(THREAD_ID) } returns listOf(notification)
        every { dependencies.publisher.publish(notification) } returns ReplyNotificationPublishResult.SUPPRESSED

        dependencies.createUseCase().refresh(request())

        coVerify(exactly = 1) {
            dependencies.replyNotificationRepository.updateStatus(
                THREAD_ID,
                3,
                ReplyNotificationStatus.DETECTED,
                ReplyNotificationStatus.SUPPRESSED,
            )
        }
    }

    /** スレッド画面とタブ画面をどちらから先に更新しても同じ返信を一件だけ登録する。 */
    @Test
    fun threadAndTabRefreshes_registerSameReplyOnceInEitherOrder() = runTest {
        assertCrossPathRefreshOrder(threadFirst = true)
        assertCrossPathRefreshOrder(threadFirst = false)
    }

    /** 共通UseCaseを二つの画面経路から呼び、Repositoryの一意登録境界を検証する。 */
    private suspend fun TestScope.assertCrossPathRefreshOrder(threadFirst: Boolean) {
        // --- Dependencies ---
        val dependencies = dependencies()
        val insertedReplies = mutableSetOf<Pair<ThreadId, Int>>()
        var insertAttempts = 0
        coEvery { dependencies.threadStateRepository.getThreadState(THREAD_ID) } returns state(latestResCount = 2)
        coEvery { dependencies.threadHistoryRepository.getHistory(THREAD_ID) } returns history()
        coEvery { dependencies.postHistoryRepository.getMyPostNumbers(7L) } returns setOf(2)
        coEvery { dependencies.settingsRepository.getIsReplyNotificationEnabled() } returns true
        coEvery {
            dependencies.datRepository.getThread(
                boardUrl = any(),
                threadKey = any(),
                onProgress = any<(Float) -> Unit>(),
            )
        } returns (listOf(post("root"), post("mine"), post(">>2 reply")) to "Title")
        coEvery { dependencies.replyNotificationRepository.insertNew(any()) } coAnswers {
            insertAttempts++
            @Suppress("UNCHECKED_CAST")
            val candidates = invocation.args[0] as List<ReplyNotificationEntity>
            candidates.filter { insertedReplies.add(it.threadId to it.replyResNo) }
        }
        coEvery { dependencies.replyNotificationRepository.findDetected(THREAD_ID) } returns emptyList()

        // --- Shared refresh entry points ---
        val refreshUseCase = dependencies.createUseCase()
        val contentLoadUseCase = ThreadContentLoadUseCase(refreshUseCase)
        val tabs = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        every { tabsRepository.observeOpenThreadTabs() } returns tabs
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        val coordinator = ThreadTabsCoordinator(
            tabsRepository = tabsRepository,
            threadBookmarkRepository = bookmarkRepository,
            threadRefreshUseCase = refreshUseCase,
        )
        tabs.emit(listOf(testTab()))
        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        runCurrent()

        // --- Refresh order ---
        if (threadFirst) {
            contentLoadUseCase.load(BOARD_URL, THREAD_KEY, onProgress = {})
            coordinator.refreshOpenThreads()
        } else {
            coordinator.refreshOpenThreads()
            advanceUntilIdle()
            contentLoadUseCase.load(BOARD_URL, THREAD_KEY, onProgress = {})
        }
        advanceUntilIdle()

        // stale stateを二経路へ返しても、複合主キー相当の登録境界では一件だけ残る。
        assertEquals(2, insertAttempts)
        assertEquals(setOf(THREAD_ID to 3), insertedReplies)
        coordinator.close()
    }

    private fun testTab() = ThreadTabInfo(
        id = THREAD_ID,
        title = "Title",
        boardName = "Board",
        boardUrl = BOARD_URL,
        boardId = 1L,
        firstVisibleItemIndex = 0,
        isPinned = false,
    )

    private fun dependencies() = Dependencies(
        datRepository = mockk(relaxed = true),
        threadStateRepository = mockk(relaxed = true),
        threadHistoryRepository = mockk(relaxed = true),
        postHistoryRepository = mockk(relaxed = true),
        ownPostReconciliationUseCase = mockk(relaxed = true),
        settingsRepository = mockk(relaxed = true),
        replyNotificationRepository = mockk(relaxed = true),
        publisher = mockk(relaxed = true),
        logger = mockk(relaxed = true),
    )

    private data class Dependencies(
        val datRepository: DatRepository,
        val threadStateRepository: ThreadStateRepository,
        val threadHistoryRepository: ThreadHistoryRepository,
        val postHistoryRepository: PostHistoryRepository,
        val ownPostReconciliationUseCase: OwnPostReconciliationUseCase,
        val settingsRepository: SettingsRepository,
        val replyNotificationRepository: ReplyNotificationRepository,
        val publisher: ReplyNotificationPublisher,
        val logger: AppLogger,
    ) {
        fun createUseCase() = ThreadRefreshUseCase(
            datRepository = datRepository,
            threadStateRepository = threadStateRepository,
            threadHistoryRepository = threadHistoryRepository,
            postHistoryRepository = postHistoryRepository,
            ownPostReconciliationUseCase = ownPostReconciliationUseCase,
            settingsRepository = settingsRepository,
            replyNotificationRepository = replyNotificationRepository,
            replyNotificationPublisher = publisher,
            logger = logger,
        )
    }

    private fun request() = ThreadRefreshRequest(
        threadId = THREAD_ID,
        boardUrl = BOARD_URL,
        boardId = 1L,
        boardName = "Board",
        threadKey = THREAD_KEY,
        threadTitle = "Title",
    )

    private fun state(latestResCount: Int) = ThreadStateEntity(
        threadId = THREAD_ID,
        boardId = 1L,
        boardUrl = BOARD_URL,
        boardName = "Board",
        threadKey = THREAD_KEY,
        title = "Title",
        latestResCount = latestResCount,
        updatedAt = 1L,
    )

    private fun history() = ThreadHistoryEntity(
        id = 7L,
        threadId = THREAD_ID,
        boardUrl = BOARD_URL,
        boardId = 1L,
        boardName = "Board",
        title = "Title",
        resCount = 2,
        readState = ThreadReadState(),
    )

    private fun notification(replyResNo: Int) = ReplyNotificationEntity(
        threadId = THREAD_ID,
        replyResNo = replyResNo,
        targetOwnResNumbers = "2",
        boardUrl = BOARD_URL,
        threadKey = THREAD_KEY,
        threadTitle = "Title",
        messagePreview = ">>2 reply",
        detectedAt = 10L,
    )

    private fun post(content: String) = ReplyInfo(
        name = "name",
        email = "",
        date = "2024/01/01 00:00:00",
        id = "id",
        content = content,
    )

    private companion object {
        const val BOARD_URL = "https://example.com/test/"
        const val THREAD_KEY = "123"
        val THREAD_ID = ThreadId.of("example.com", "test", THREAD_KEY)
    }
}
