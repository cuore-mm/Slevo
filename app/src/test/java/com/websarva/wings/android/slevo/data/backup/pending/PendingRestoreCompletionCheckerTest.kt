package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import com.squareup.moshi.Moshi
import com.websarva.wings.android.slevo.data.backup.restore.BackupDatabaseValidator
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * [PendingRestoreCompletionChecker] の unit tests。
 */
class PendingRestoreCompletionCheckerTest {

    private lateinit var context: Context
    private lateinit var fileStore: FakePendingRestoreFileStore
    private lateinit var validator: FakeBackupDatabaseValidator
    private lateinit var checker: PendingRestoreCompletionChecker
    private val liveDbFile = File("test-live-db")
    private val moshi = Moshi.Builder().build()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        fileStore = FakePendingRestoreFileStore()
        validator = FakeBackupDatabaseValidator()
        checker = PendingRestoreCompletionChecker(
            context = context,
            moshi = moshi,
            dbValidator = validator,
        )
        checker.setFileStoreForTest(fileStore)
        checker.liveDbFileOverride = liveDbFile
    }

    // --- 5.9: marker なし → no-op ---

    @Test
    fun runIfNeeded_noMarker_doesNothing() {
        fileStore.marker = null
        checker.runIfNeeded()
        assertTrue(fileStore.events.isEmpty())
    }

    // --- 5.9: marker が MIGRATION_PENDING でない → no-op ---

    @Test
    fun runIfNeeded_notMigrationPending_doesNothing() {
        fileStore.marker = marker(RestoreStatus.PREPARED)
        checker.runIfNeeded()
        assertTrue(fileStore.events.isEmpty())
    }

    // --- 5.9: MIGRATION_PENDING + validation success → COMPLETED → cleanup ---

    @Test
    fun migrationPending_validationSuccess_transitionsToCompletedAndCleansUp() {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.nextValidateResult = null // success

        checker.runIfNeeded()

        assertEquals(
            listOf(
                "writeMarker:COMPLETED",
                "writeResult:true:restore completed successfully (migration confirmed)",
                "cleanupPending",
            ),
            fileStore.events,
        )
    }

    // --- 5.9: MIGRATION_PENDING + validation failure → ROLLBACK_REQUIRED ---

    @Test
    fun migrationPending_validationFailure_transitionsToRollbackRequired() {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.nextValidateResult = "identity hash mismatch"

        checker.runIfNeeded()

        assertEquals(
            listOf(
                "writeResult:false:post-migration validation failed: identity hash mismatch",
                "writeMarker:ROLLBACK_REQUIRED",
            ),
            fileStore.events,
        )
        assertFalse(fileStore.events.contains("cleanupPending"))
        assertFalse(fileStore.events.contains("writeResult:true"))
    }

    // --- 5.10: ROLLBACK_REQUIRED marker write failure → keeps MIGRATION_PENDING ---

    @Test
    fun migrationPending_rollbackRequiredMarkerWriteFailure_leavesMigrationPending() {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.nextValidateResult = "identity hash mismatch"
        fileStore.markerWriteFailsAfter = 0 // marker write fails, result already written

        checker.runIfNeeded()

        // result は書かれるが marker は MIGRATION_PENDING のまま
        assertEquals(
            listOf("writeResult:false:post-migration validation failed: identity hash mismatch"),
            fileStore.events,
        )
    }

    // --- 5.12: COMPLETED marker → success result + cleanup ---

    @Test
    fun migrationPending_successResultAndCleanupFollowCompletedMarker() {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.nextValidateResult = null

        checker.runIfNeeded()

        // COMPLETED marker → success result → cleanup の順で書かれる
        assertEquals(
            listOf(
                "writeMarker:COMPLETED",
                "writeResult:true:restore completed successfully (migration confirmed)",
                "cleanupPending",
            ),
            fileStore.events,
        )
    }

    // --- 5.9: COMPLETED marker write 失敗でも後続処理は実行される ---

    @Test
    fun migrationPending_completedMarkerWriteFailure_stillWritesResultAndCleansUp() {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.nextValidateResult = null
        fileStore.markerWriteFailsAfter = 0 // first marker write fails

        checker.runIfNeeded()

        // marker 書き込み失敗でも result と cleanup は続行
        assertEquals(
            listOf(
                "writeResult:true:restore completed successfully (migration confirmed)",
                "cleanupPending",
            ),
            fileStore.events,
        )
    }

    // --- Helper ---

    private fun marker(status: RestoreStatus) = PendingRestoreMarker(
        status = status,
        createdAt = "2026-07-06T00:00:00Z",
        includeCookies = false,
        databaseVersion = 9,
    )

    /** [PendingRestoreFileStore] の fake。writeMarker/writeResult の失敗を注入可能。 */
    private class FakePendingRestoreFileStore : PendingRestoreFileStore {
        override val pendingDir: File = File("pending-dir")
        override val rollbackDir: File = File(pendingDir, "rollback")
        var marker: PendingRestoreMarker? = null
        val events = mutableListOf<String>()
        var markerWriteFailsAfter: Int? = null
        private var writeCount = 0

        override fun readMarker(): PendingRestoreMarker? = marker

        override fun writeMarker(marker: PendingRestoreMarker) {
            if (markerWriteFailsAfter != null && writeCount >= markerWriteFailsAfter!!) return
            this.marker = marker
            events += "writeMarker:${marker.status}"
            writeCount++
        }

        override fun writeResult(
            success: Boolean,
            message: String,
            timestamp: String,
            backupDatabaseVersion: Int?,
            currentDatabaseVersion: Int?,
            migrationRequired: Boolean,
            migrationCompleted: Boolean,
            previousStatus: String?,
            rollbackRequiredAt: String?,
            finalFailureReason: String?,
        ) {
            events += "writeResult:$success:$message"
        }

        override fun cleanupPending() {
            events += "cleanupPending"
            marker = null
        }

        override fun cleanupResult() {
            events += "cleanupResult"
        }
    }

    /** [BackupDatabaseValidator] の fake。 */
    private class FakeBackupDatabaseValidator : BackupDatabaseValidator {
        var nextValidateResult: String? = null
        override fun validate(dbFile: File): String? = nextValidateResult
        override fun preValidate(dbFile: File, manifestDatabaseVersion: Int): String? = null
    }
}
