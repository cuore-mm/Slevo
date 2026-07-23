package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import com.squareup.moshi.Moshi
import com.websarva.wings.android.slevo.core.log.AppLogger
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [PendingRestoreResultConsumer]のmarker gateとconditional acknowledgeを検証する。
 */
@OptIn(ExperimentalStdlibApi::class)
@RunWith(RobolectricTestRunner::class)
class PendingRestoreResultConsumerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val moshi = Moshi.Builder().build()

    /** COMPLETED markerの成功resultを通知可能状態へ変換する。 */
    @Test
    fun completedSuccess_isReadyWithoutExposingDiagnosticMessage() {
        val fixture = createFixture()
        fixture.store.writeMarker(marker(RestoreStatus.COMPLETED))
        fixture.store.writeResult(
            success = true,
            message = "internal path /secret/restore",
            timestamp = "2026-07-15T00:00:00Z",
            migrationCompleted = true,
        )

        val result = fixture.consumer.read()

        assertTrue(result is PendingRestoreResultRead.Ready)
        val notification = (result as PendingRestoreResultRead.Ready).notification
        assertTrue(notification.type == PendingRestoreNotificationType.SUCCESS)
        assertTrue(notification.token.isNotEmpty())
    }

    /** FAILEDとROLLBACK_REQUIREDはfailure通知として扱う。 */
    @Test
    fun failedAndRollbackRequired_areReadyAsFailure() {
        val fixture = createFixture()

        fixture.store.writeMarker(marker(RestoreStatus.FAILED))
        fixture.store.writeResult(false, "failure details", "2026-07-15T00:00:00Z")
        assertFailure(fixture.consumer.read())

        fixture.store.writeMarker(marker(RestoreStatus.ROLLBACK_REQUIRED))
        fixture.store.writeResult(false, "rollback details", "2026-07-15T00:00:01Z")
        assertFailure(fixture.consumer.read())
    }

    /** 適用途中の全marker statusは通知せずPendingとして保持する。 */
    @Test
    fun intermediateStatuses_arePending() {
        val fixture = createFixture()
        val statuses = listOf(
            RestoreStatus.PREPARED,
            RestoreStatus.APPLYING,
            RestoreStatus.ROLLBACK_READY,
            RestoreStatus.DB_SWAPPED,
            RestoreStatus.MIGRATION_PENDING,
        )

        statuses.forEachIndexed { index, status ->
            fixture.store.writeMarker(marker(status))
            fixture.store.writeResult(
                success = true,
                message = "intermediate",
                timestamp = "2026-07-15T00:00:0$index",
                migrationCompleted = false,
            )

            assertTrue(fixture.consumer.read() is PendingRestoreResultRead.Pending)
        }
    }

    /** marker cleanup後のfailureと中間successを別々に分類する。 */
    @Test
    fun markerlessFinalResult_isReadyButMarkerlessIntermediateIsPending() {
        val fixture = createFixture()
        fixture.store.writeResult(false, "final failure", "2026-07-15T00:00:00Z")
        assertFailure(fixture.consumer.read())

        fixture.store.writeResult(
            success = true,
            message = "intermediate",
            timestamp = "2026-07-15T00:00:01Z",
            migrationCompleted = false,
        )
        assertTrue(fixture.consumer.read() is PendingRestoreResultRead.Pending)
    }

    /** cleanup 後の markerless migration completion result は既存 success Snackbar へ渡す。 */
    @Test
    fun markerlessMigrationCompletedSuccess_isReadyAsSuccess() {
        val fixture = createFixture()
        fixture.store.writeResult(
            success = true,
            message = "internal completion detail",
            timestamp = "2026-07-15T00:00:00Z",
            migrationCompleted = true,
        )

        val result = fixture.consumer.read()

        assertTrue(result is PendingRestoreResultRead.Ready)
        assertTrue(
            (result as PendingRestoreResultRead.Ready).notification.type ==
                PendingRestoreNotificationType.SUCCESS,
        )
    }

    /** terminal markerとresultの不整合を通知せず破棄する。 */
    @Test
    fun markerAndResultMismatch_isUnreadableAndRemoved() {
        val fixture = createFixture()
        fixture.store.writeMarker(marker(RestoreStatus.COMPLETED))
        fixture.store.writeResult(false, "unexpected failure", "2026-07-15T00:00:00Z")

        val result = fixture.consumer.read()

        assertTrue(result === PendingRestoreResultRead.Unreadable)
        assertFalse(resultFile(fixture.filesDir).exists())
    }

    /** malformed payloadは機密内容を表示せず非fatal扱いにする。 */
    @Test
    fun malformedResult_isUnreadableAndRemovedWithoutLoggingPayload() {
        val fixture = createFixture()
        val resultFile = resultFile(fixture.filesDir)
        resultFile.parentFile?.mkdirs()
        resultFile.writeText("{\"message\":\"secret-path\"")

        val result = fixture.consumer.read()

        assertTrue(result === PendingRestoreResultRead.Unreadable)
        assertFalse(resultFile.exists())
        assertTrue(fixture.logger.messages.none { it.contains("secret-path") })
    }

    /** result pathをfileとして読めない場合も通常起動を妨げない。 */
    @Test
    fun resultReadFailure_isNonFatal() {
        val fixture = createFixture()
        val resultFile = resultFile(fixture.filesDir)
        resultFile.mkdirs()

        val result = fixture.consumer.read()

        assertTrue(result === PendingRestoreResultRead.Unreadable)
    }

    /** 新resultが上書きされた場合、旧tokenのacknowledgeで削除しない。 */
    @Test
    fun acknowledge_deletesOnlyTheMatchingResult() {
        val fixture = createFixture()
        fixture.store.writeResult(false, "first", "2026-07-15T00:00:00Z")
        val first = fixture.consumer.read() as PendingRestoreResultRead.Ready

        fixture.store.writeResult(false, "second", "2026-07-15T00:00:01Z")

        assertFalse(fixture.consumer.acknowledge(first.notification.token))
        val second = fixture.consumer.read() as PendingRestoreResultRead.Ready
        assertNotEquals(first.notification.token, second.notification.token)
        assertTrue(resultFile(fixture.filesDir).exists())
        assertTrue(fixture.consumer.acknowledge(second.notification.token))
        assertFalse(resultFile(fixture.filesDir).exists())
    }

    /** resultが先に消費済みならacknowledgeを成功扱いにする。 */
    @Test
    fun acknowledge_missingResult_isAlreadyAcknowledged() {
        val fixture = createFixture()

        assertTrue(fixture.consumer.acknowledge("missing"))
    }

    /** acknowledge時のread failureを非fatal扱いにする。 */
    @Test
    fun acknowledgeReadFailure_isNonFatal() {
        val fixture = createFixture()
        val resultFile = resultFile(fixture.filesDir)
        resultFile.mkdirs()

        assertFalse(fixture.consumer.acknowledge("any"))
    }

    /** read outcomeがfailure notificationであることを検証する。 */
    private fun assertFailure(result: PendingRestoreResultRead) {
        assertTrue(result is PendingRestoreResultRead.Ready)
        assertTrue(
            (result as PendingRestoreResultRead.Ready).notification.type ==
                PendingRestoreNotificationType.FAILURE,
        )
    }

    /** 一時filesDir、real store、consumerを組み立てる。 */
    private fun createFixture(): Fixture {
        val filesDir = temporaryFolder.newFolder("files")
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
        val logger = TestAppLogger()
        val store = RealPendingRestoreFileStore(context, moshi)
        return Fixture(
            filesDir = filesDir,
            store = store,
            logger = logger,
            consumer = PendingRestoreResultConsumer(context, moshi, logger),
        )
    }

    /** fixtureのresult file pathを返す。 */
    private fun resultFile(filesDir: File): File = File(
        File(filesDir, PendingRestoreManager.RESULT_DIR_NAME),
        PendingRestoreManager.RESULT_FILENAME,
    )

    /** test用markerを作成する。 */
    private fun marker(status: RestoreStatus): PendingRestoreMarker = PendingRestoreMarker(
        status = status,
        createdAt = "2026-07-15T00:00:00Z",
        includeCookies = false,
        databaseVersion = 9,
    )

    /** consumer testで共有する一時filesystemとfake logger。 */
    private data class Fixture(
        val filesDir: File,
        val store: RealPendingRestoreFileStore,
        val logger: TestAppLogger,
        val consumer: PendingRestoreResultConsumer,
    )

    /** consumer testで診断messageだけを収集するlogger。 */
    private class TestAppLogger : AppLogger {
        val messages = mutableListOf<String>()

        /** debug logはtestでは無視する。 */
        override fun d(message: String, tag: String?, throwable: Throwable?) = Unit

        /** info logはtestでは無視する。 */
        override fun i(message: String, tag: String?, throwable: Throwable?) = Unit

        /** error messageだけをpayload漏洩検証用に保持する。 */
        override fun e(message: String, tag: String?, throwable: Throwable?) {
            messages += message
        }
    }
}
