package com.websarva.wings.android.slevo.ui

import androidx.lifecycle.ViewModelStore
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreNotification
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreNotificationType
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreResultConsumer
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreResultRead
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [PendingRestoreResultViewModel]のSTARTED観察、指数backoff、generation ownershipを検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingRestoreResultViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** START前はconsumerを読み取らず、START直後のAbsentで通常起動stateを保つ。 */
    @Test
    fun absentResult_isReadOnlyAfterObservationStarts() = runTest {
        val consumer = mockk<PendingRestoreResultConsumer>()
        every { consumer.read() } returns PendingRestoreResultRead.Absent
        val viewModel = createViewModel(consumer, testScheduler)

        runCurrent()
        verify(exactly = 0) { consumer.read() }

        viewModel.startObservation()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.notification)
        assertFalse(viewModel.uiState.value.waitingForCompletion)
        verify(exactly = 1) { consumer.read() }
    }

    /** terminal Ready successを公開し、同一観察中に追加readしない。 */
    @Test
    fun readySuccess_isPublishedAndObservationStops() = runTest {
        val consumer = mockk<PendingRestoreResultConsumer>()
        val success = notification("success", PendingRestoreNotificationType.SUCCESS)
        every { consumer.read() } returns PendingRestoreResultRead.Ready(success)
        val viewModel = createViewModel(consumer, testScheduler)

        viewModel.startObservation()
        advanceUntilIdle()

        assertEquals("success", viewModel.uiState.value.notification?.token)
        assertEquals(PendingRestoreNotificationType.SUCCESS, viewModel.uiState.value.notification?.type)
        verify(exactly = 1) { consumer.read() }
    }

    /** terminal Ready failureもsuccessと同じく一件だけ公開する。 */
    @Test
    fun readyFailure_isPublishedAndObservationStops() = runTest {
        val consumer = mockk<PendingRestoreResultConsumer>()
        val failure = notification("failure", PendingRestoreNotificationType.FAILURE)
        every { consumer.read() } returns PendingRestoreResultRead.Ready(failure)
        val viewModel = createViewModel(consumer, testScheduler)

        viewModel.startObservation()
        advanceUntilIdle()

        assertEquals("failure", viewModel.uiState.value.notification?.token)
        verify(exactly = 1) { consumer.read() }
    }

    /** pending resultが最初の200ms後にReadyへ遷移する。 */
    @Test
    fun pendingResult_isRetriedAfterInitialBackoffAndPublished() = runTest {
        val consumer = mockk<PendingRestoreResultConsumer>()
        val success = notification("final", PendingRestoreNotificationType.SUCCESS)
        every { consumer.read() } returnsMany listOf(
            PendingRestoreResultRead.Pending(null),
            PendingRestoreResultRead.Ready(success),
        )
        val viewModel = createViewModel(consumer, testScheduler)

        viewModel.startObservation()
        runCurrent()
        assertTrue(viewModel.uiState.value.waitingForCompletion)
        verify(exactly = 1) { consumer.read() }

        advanceTimeBy(199)
        runCurrent()
        verify(exactly = 1) { consumer.read() }

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals("final", viewModel.uiState.value.notification?.token)
        assertFalse(viewModel.uiState.value.waitingForCompletion)
        verify(exactly = 2) { consumer.read() }
    }

    /** Pendingが継続しても2秒上限のbackoffでSTARTED中の観察を継続する。 */
    @Test
    fun pendingResult_usesContinuousExponentialBackoffCappedAtTwoSeconds() = runTest {
        val consumer = mockk<PendingRestoreResultConsumer>()
        val readTimes = mutableListOf<Long>()
        every { consumer.read() } answers {
            readTimes += testScheduler.currentTime
            if (readTimes.size < 7) {
                PendingRestoreResultRead.Pending(null)
            } else {
                PendingRestoreResultRead.Ready(notification("final", PendingRestoreNotificationType.SUCCESS))
            }
        }
        val viewModel = createViewModel(consumer, testScheduler)

        viewModel.startObservation()
        advanceUntilIdle()

        assertEquals(listOf(0L, 200L, 600L, 1_400L, 3_000L, 5_000L, 7_000L), readTimes)
        verify(exactly = 7) { consumer.read() }
        assertEquals("final", viewModel.uiState.value.notification?.token)
    }

    /** STOPは待機中のreadを止め、停止中に追加readを行わない。 */
    @Test
    fun stopObservation_cancelsPendingBackoff() = runTest {
        val consumer = mockk<PendingRestoreResultConsumer>()
        every { consumer.read() } returns PendingRestoreResultRead.Pending(null)
        val viewModel = createViewModel(consumer, testScheduler)

        viewModel.startObservation()
        runCurrent()
        viewModel.stopObservation()
        advanceUntilIdle()

        verify(exactly = 1) { consumer.read() }
        assertTrue(viewModel.uiState.value.waitingForCompletion)
    }

    /** STOP後の再STARTは旧backoffを引き継がず即時readし、重複開始はjobを増やさない。 */
    @Test
    fun restartObservation_readsImmediatelyAndDuplicateStartIsIgnored() = runTest {
        val consumer = mockk<PendingRestoreResultConsumer>()
        val success = notification("success", PendingRestoreNotificationType.SUCCESS)
        every { consumer.read() } returnsMany listOf(
            PendingRestoreResultRead.Pending(null),
            PendingRestoreResultRead.Ready(success),
        )
        val viewModel = createViewModel(consumer, testScheduler)

        viewModel.startObservation()
        viewModel.startObservation()
        runCurrent()
        verify(exactly = 1) { consumer.read() }

        viewModel.stopObservation()
        viewModel.startObservation()
        viewModel.startObservation()
        advanceUntilIdle()

        assertEquals("success", viewModel.uiState.value.notification?.token)
        verify(exactly = 2) { consumer.read() }
    }

    /** generationを跨ぐ遅延readは新しい観察の結果とstateを上書きしない。 */
    @Test
    fun staleNonCooperativeRead_cannotPublishAfterRestart() = runTest {
        val consumer = mockk<PendingRestoreResultConsumer>()
        val readStarted = CountDownLatch(1)
        val currentReadCompleted = CountDownLatch(1)
        val staleReadCompleted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val stale = notification("stale", PendingRestoreNotificationType.FAILURE)
        val current = notification("current", PendingRestoreNotificationType.SUCCESS)
        every { consumer.read() } answers {
            if (readStarted.count == 1L) {
                readStarted.countDown()
                releaseRead.await(1, TimeUnit.SECONDS)
                staleReadCompleted.countDown()
                PendingRestoreResultRead.Ready(stale)
            } else {
                currentReadCompleted.countDown()
                PendingRestoreResultRead.Ready(current)
            }
        }
        val viewModel = createViewModel(consumer, testScheduler, Dispatchers.IO)

        try {
            viewModel.startObservation()
            assertTrue(readStarted.await(1, TimeUnit.SECONDS))
            viewModel.stopObservation()
            viewModel.startObservation()
            assertTrue(currentReadCompleted.await(1, TimeUnit.SECONDS))
            advanceUntilIdle()
            assertEquals("current", viewModel.uiState.value.notification?.token)

            releaseRead.countDown()
            assertTrue(staleReadCompleted.await(1, TimeUnit.SECONDS))
            advanceUntilIdle()

            assertEquals("current", viewModel.uiState.value.notification?.token)
            verify(exactly = 2) { consumer.read() }
        } finally {
            releaseRead.countDown()
            viewModel.stopObservation()
        }
    }

    /** ViewModel clear後に完了した遅延readはstateや後続readへ作用しない。 */
    @Test
    fun viewModelClear_invalidatesObservationGeneration() = runTest {
        val consumer = mockk<PendingRestoreResultConsumer>()
        val readStarted = CountDownLatch(1)
        val readCompleted = CountDownLatch(1)
        val releaseRead = CountDownLatch(1)
        val stale = notification("stale", PendingRestoreNotificationType.FAILURE)
        every { consumer.read() } answers {
            readStarted.countDown()
            releaseRead.await(1, TimeUnit.SECONDS)
            readCompleted.countDown()
            PendingRestoreResultRead.Ready(stale)
        }
        val viewModel = createViewModel(consumer, testScheduler, Dispatchers.IO)

        try {
            viewModel.startObservation()
            assertTrue(readStarted.await(1, TimeUnit.SECONDS))
            ViewModelStore().apply {
                put("pendingRestore", viewModel)
                clear()
            }
            releaseRead.countDown()
            assertTrue(readCompleted.await(1, TimeUnit.SECONDS))
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.notification)
            verify(exactly = 1) { consumer.read() }
        } finally {
            releaseRead.countDown()
        }
    }

    /** 一致するtokenのacknowledge成功時だけstateをclearする。 */
    @Test
    fun acknowledgeSuccess_clearsOnlyMatchingNotification() = runTest {
        val consumer = mockk<PendingRestoreResultConsumer>()
        val success = notification("success", PendingRestoreNotificationType.SUCCESS)
        every { consumer.read() } returns PendingRestoreResultRead.Ready(success)
        every { consumer.acknowledge("success") } returns true
        val viewModel = createViewModel(consumer, testScheduler)
        viewModel.startObservation()
        advanceUntilIdle()

        viewModel.acknowledgeResult("success")
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.notification)
    }

    /** ack競合時は現行lifecycleで新しいnotificationを再読する。 */
    @Test
    fun acknowledgeRace_reloadsNewNotification() = runTest {
        val consumer = mockk<PendingRestoreResultConsumer>()
        val first = notification("first", PendingRestoreNotificationType.FAILURE)
        val second = notification("second", PendingRestoreNotificationType.SUCCESS)
        every { consumer.read() } returnsMany listOf(
            PendingRestoreResultRead.Ready(first),
            PendingRestoreResultRead.Ready(second),
        )
        every { consumer.acknowledge("first") } returns false
        val viewModel = createViewModel(consumer, testScheduler)
        viewModel.startObservation()
        advanceUntilIdle()

        viewModel.acknowledgeResult("first")
        advanceUntilIdle()

        assertEquals("second", viewModel.uiState.value.notification?.token)
    }

    /** virtual dispatcherを使うtest用ViewModelを生成する。 */
    private fun createViewModel(
        consumer: PendingRestoreResultConsumer,
        scheduler: TestCoroutineScheduler,
        ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = StandardTestDispatcher(scheduler),
    ): PendingRestoreResultViewModel = PendingRestoreResultViewModel(
        resultConsumer = consumer,
        ioDispatcher = ioDispatcher,
    )

    /** test用notificationを生成する。 */
    private fun notification(
        token: String,
        type: PendingRestoreNotificationType,
    ): PendingRestoreNotification = PendingRestoreNotification(token, type)
}
