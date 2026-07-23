package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import com.squareup.moshi.Moshi
import com.websarva.wings.android.slevo.data.backup.restore.BackupDatabaseValidator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
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

    // --- 5.9: COMPLETED marker write failure は後続処理を停止する ---

    @Test
    fun migrationPending_completedMarkerWriteFailure_stopsBeforeResultAndCleanup() {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.nextValidateResult = null
        fileStore.markerWriteFailsAfter = 0 // first marker write fails

        checker.runIfNeeded()

        assertTrue(fileStore.events.isEmpty())
        assertEquals(RestoreStatus.MIGRATION_PENDING, fileStore.marker?.status)
    }

    @Test
    fun migrationPending_successResultWriteFailure_stopsBeforeCleanup() {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.nextValidateResult = null
        fileStore.resultWriteFailure = IllegalStateException("secret result detail")

        checker.runIfNeeded()

        assertEquals(listOf("writeMarker:COMPLETED"), fileStore.events)
        assertEquals(RestoreStatus.COMPLETED, fileStore.marker?.status)
    }

    @Test
    fun migrationPending_successResultWriteFailure_recoveryRetriesBeforeCleanup() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.nextValidateResult = null
        fileStore.resultWriteFailure = IllegalStateException("secret result detail")

        checker.runIfNeeded()

        fileStore.resultWriteFailure = null
        val dbSwapper = mockk<PendingRestoreDbSwapper>(relaxed = true)
        every { dbSwapper.getLiveDbFile() } returns liveDbFile
        val reflector = mockk<PendingRestoreDataStoreReflector>(relaxed = true)
        createApplierForRecovery(dbSwapper, reflector).runIfNeeded()

        assertEquals(
            "writeResult:true:restore completed successfully",
            fileStore.events[fileStore.events.lastIndex - 1],
        )
        assertEquals("cleanupPending", fileStore.events.last())
        assertEquals(null, fileStore.marker)
    }

    @Test
    fun migrationPending_validationResultWriteFailure_keepsMigrationPending() {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.nextValidateResult = "identity hash mismatch"
        fileStore.resultWriteFailure = IllegalStateException("secret result detail")

        checker.runIfNeeded()

        assertTrue(fileStore.events.isEmpty())
        assertEquals(RestoreStatus.MIGRATION_PENDING, fileStore.marker?.status)
    }

    @Test
    fun migrationPending_validationResultWriteFailure_recoveryUsesMigrationPendingMarker() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.nextValidateResult = "identity hash mismatch"
        fileStore.resultWriteFailure = IllegalStateException("secret result detail")

        checker.runIfNeeded()

        fileStore.resultWriteFailure = null
        validator.nextValidateResult = null
        validator.userVersion = 9
        val dbSwapper = mockk<PendingRestoreDbSwapper>(relaxed = true)
        every { dbSwapper.getLiveDbFile() } returns liveDbFile
        every { dbSwapper.hasRollbackBackup(any(), any()) } returns true
        val reflector = mockk<PendingRestoreDataStoreReflector>(relaxed = true)
        createApplierForRecovery(dbSwapper, reflector).runIfNeeded()
        createApplierForRecovery(dbSwapper, reflector).runIfNeeded()

        verify(exactly = 0) { dbSwapper.restoreRollbackBackup(any(), any()) }
        assertTrue(fileStore.events.contains("cleanupPending"))
        assertEquals(null, fileStore.marker)
    }

    @Test
    fun migrationPending_operationalValidationException_isSwallowedAndKeepsMarker() {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.validationFailure = IllegalStateException("secret validation detail")

        checker.runIfNeeded()

        assertTrue(fileStore.events.isEmpty())
        assertEquals(RestoreStatus.MIGRATION_PENDING, fileStore.marker?.status)
    }

    @Test
    fun migrationPending_operationalMarkerReadException_isSwallowedWithoutWrites() {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        fileStore.markerReadFailure = IllegalStateException("secret marker detail")

        checker.runIfNeeded()

        assertTrue(fileStore.events.isEmpty())
        assertEquals(RestoreStatus.MIGRATION_PENDING, fileStore.marker?.status)
    }

    @Test
    fun migrationPending_cancellationException_isRethrown() {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        val cancellation = CancellationException("cancelled")
        validator.validationFailure = cancellation

        val thrown = try {
            checker.runIfNeeded()
            null
        } catch (error: CancellationException) {
            error
        }

        assertSame(cancellation, thrown)
        assertTrue(fileStore.events.isEmpty())
    }

    @Test
    fun migrationPending_fatalThrowable_isNotCaught() {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        val fatal = AssertionError("fatal")
        validator.validationFailure = fatal

        val thrown = try {
            checker.runIfNeeded()
            null
        } catch (error: AssertionError) {
            error
        }

        assertSame(fatal, thrown)
        assertTrue(fileStore.events.isEmpty())
    }

    @Test
    fun migrationPending_markerWriteFailure_recoveryUsesMarkerAndCleansUpWithoutRollback() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.nextValidateResult = null
        fileStore.markerWriteFailsAfter = 0

        checker.runIfNeeded()

        fileStore.markerWriteFailsAfter = null
        validator.userVersion = 9
        val dbSwapper = mockk<PendingRestoreDbSwapper>(relaxed = true)
        every { dbSwapper.getLiveDbFile() } returns liveDbFile
        every { dbSwapper.hasRollbackBackup(any(), any()) } returns true
        val reflector = mockk<PendingRestoreDataStoreReflector>(relaxed = true)
        createApplierForRecovery(dbSwapper, reflector).runIfNeeded()
        createApplierForRecovery(dbSwapper, reflector).runIfNeeded()

        verify(exactly = 0) { dbSwapper.restoreRollbackBackup(any(), any()) }
        assertTrue(fileStore.events.contains("cleanupPending"))
        assertEquals(null, fileStore.marker)
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
        override val quarantineRootDir: File = File(
            System.getProperty("java.io.tmpdir"),
            "pending-restore-quarantine-${System.nanoTime()}",
        )
        var marker: PendingRestoreMarker? = null
        val events = mutableListOf<String>()
        var markerWriteFailsAfter: Int? = null
        var markerReadFailure: Exception? = null
        var resultWriteFailure: Exception? = null
        private var writeCount = 0

        override fun createQuarantineIncidentDir(): File {
            val incidentDir = File(quarantineRootDir, "incident-${System.nanoTime()}")
            incidentDir.mkdirs()
            return incidentDir
        }

        override fun readMarker(): PendingRestoreMarker? {
            markerReadFailure?.let { throw it }
            return marker
        }

        override fun writeMarker(marker: PendingRestoreMarker) {
            if (markerWriteFailsAfter != null && writeCount >= markerWriteFailsAfter!!) {
                throw IllegalStateException("marker write failed")
            }
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
            resultWriteFailure?.let { throw it }
            events += "writeResult:$success:$message"
        }

        override fun cleanupPending(): Boolean {
            events += "cleanupPending"
            marker = null
            return true
        }

        override fun cleanupResult() {
            events += "cleanupResult"
        }
    }

    /** [BackupDatabaseValidator] の fake。 */
    private class FakeBackupDatabaseValidator : BackupDatabaseValidator {
        var nextValidateResult: String? = null
        var validationFailure: Throwable? = null
        var userVersion: Int? = null

        override fun preValidate(dbFile: File, manifestDatabaseVersion: Int): String? = null
        override fun getUserVersion(dbFile: File): Int? = userVersion

        override fun validate(dbFile: File): String? {
            validationFailure?.let { throw it }
            return nextValidateResult
        }
    }

    private fun createApplierForRecovery(
        dbSwapper: PendingRestoreDbSwapper,
        reflector: PendingRestoreDataStoreReflector,
    ): PendingRestoreApplier {
        return PendingRestoreApplier.createForTest(
            context = context,
            dbValidator = validator,
            dataStoreReflector = reflector,
            fileStore = fileStore,
            dbSwapper = dbSwapper,
            nowProvider = { "2026-07-06T00:00:00Z" },
        )
    }
}
