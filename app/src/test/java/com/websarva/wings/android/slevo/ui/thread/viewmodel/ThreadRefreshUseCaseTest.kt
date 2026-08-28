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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** [ThreadRefreshUseCase] の共通取得順序と通知境界を検証するテスト。 */
class ThreadRefreshUseCaseTest {
    @Test
    fun failedFetchDoesNotChangeStateOrNotifications() = runTest {
        val dependencies = dependencies()
        coEvery { dependencies.datRepository.getThread(any(), any(), any()) } returns null
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
        coEvery { dependencies.datRepository.getThread(any(), any(), any()) } returns listOf(
            post("root"),
            post("mine"),
            post(">>2 reply"),
        ) to "New title"
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
    }

    @Test
    fun initialFetchUpdatesStateWithoutCreatingNotifications() = runTest {
        val dependencies = dependencies()
        coEvery { dependencies.threadStateRepository.getThreadState(THREAD_ID) } returns null
        coEvery { dependencies.threadHistoryRepository.getHistory(THREAD_ID) } returns null
        coEvery { dependencies.settingsRepository.getIsReplyNotificationEnabled() } returns true
        coEvery { dependencies.datRepository.getThread(any(), any(), any()) } returns listOf(
            post("root"),
            post(">>1 old reply"),
        ) to "Title"
        val useCase = dependencies.createUseCase()

        useCase.refresh(request())

        coVerify(exactly = 0) { dependencies.replyNotificationRepository.insertNew(any()) }
        coVerify(exactly = 1) { dependencies.threadStateRepository.saveThreadState(any()) }
    }

    private fun dependencies() = Dependencies(
        datRepository = mockk(),
        threadStateRepository = mockk(),
        threadHistoryRepository = mockk(),
        postHistoryRepository = mockk(),
        ownPostReconciliationUseCase = mockk(relaxed = true),
        settingsRepository = mockk(),
        replyNotificationRepository = mockk(),
        publisher = mockk(),
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
