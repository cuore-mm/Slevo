package com.websarva.wings.android.slevo.data.backup.pending

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * pending restore migration recorder と wrapper の durable boundary を検証する。
 */
class PendingRestoreMigrationAttemptRecorderTest {
    @Test
    fun record_matchingMarker_atomicallySetsAttemptEvidence() {
        val store = FakeStore(marker = marker(RestoreStatus.MIGRATION_PENDING, version = 8))
        val recorder = PendingRestoreMigrationAttemptRecorder(store)

        assertEquals(
            MigrationAttemptRecordingResult.Recorded,
            recorder.record(startVersion = 8),
        )
        assertTrue(requireNotNull(store.marker).migrationAttemptStarted)
    }

    @Test
    fun record_nonMatchingMarker_doesNotChangeMarker() {
        val original = marker(RestoreStatus.COMPLETED, version = 8)
        val store = FakeStore(marker = original)
        val recorder = PendingRestoreMigrationAttemptRecorder(store)

        assertEquals(
            MigrationAttemptRecordingResult.NotApplicable,
            recorder.record(startVersion = 8),
        )
        assertEquals(original, store.marker)
    }

    @Test
    fun record_alreadyStarted_returnsAlreadyStartedWithoutWriting() {
        val original = marker(RestoreStatus.MIGRATION_PENDING, version = 8, attemptStarted = true)
        val store = FakeStore(marker = original)
        val recorder = PendingRestoreMigrationAttemptRecorder(store)

        assertEquals(
            MigrationAttemptRecordingResult.AlreadyStarted,
            recorder.record(startVersion = 8),
        )
        assertEquals(0, store.writeCount)
        assertEquals(original, store.marker)
    }

    @Test
    fun wrapper_matchingMarker_callsDelegateOnce() {
        val store = FakeStore(marker = marker(RestoreStatus.MIGRATION_PENDING, version = 8))
        val recorder = PendingRestoreMigrationAttemptRecorder(store)
        var delegateCalls = 0
        val delegate = countingMigration(8, 9) { delegateCalls++ }

        PendingRestoreMigrationWrapper(delegate, recorder).migrate(nullDatabase())

        assertEquals(1, delegateCalls)
        assertTrue(requireNotNull(store.marker).migrationAttemptStarted)
    }

    @Test
    fun wrapper_alreadyStarted_stopsBeforeDelegate() {
        val store = FakeStore(
            marker = marker(RestoreStatus.MIGRATION_PENDING, version = 8, attemptStarted = true),
        )
        val recorder = PendingRestoreMigrationAttemptRecorder(store)
        var delegateCalls = 0
        val delegate = countingMigration(8, 9) { delegateCalls++ }

        assertThrows(IllegalStateException::class.java) {
            PendingRestoreMigrationWrapper(delegate, recorder).migrate(nullDatabase())
        }

        assertEquals(0, delegateCalls)
    }

    @Test
    fun wrapper_withoutPendingMarker_preservesNormalMigration() {
        val store = FakeStore(marker = null)
        val recorder = PendingRestoreMigrationAttemptRecorder(store)
        var delegateCalls = 0
        val delegate = countingMigration(8, 9) { delegateCalls++ }

        PendingRestoreMigrationWrapper(delegate, recorder).migrate(nullDatabase())

        assertEquals(1, delegateCalls)
        assertNull(store.marker)
    }

    @Test
    fun wrapper_markerWriteFailure_stopsBeforeDelegate() {
        val store = FakeStore(
            marker = marker(RestoreStatus.MIGRATION_PENDING, version = 8),
            writeFailure = IOException("marker unavailable"),
        )
        val recorder = PendingRestoreMigrationAttemptRecorder(store)
        var delegateCalls = 0
        val delegate = countingMigration(8, 9) { delegateCalls++ }

        assertThrows(IOException::class.java) {
            PendingRestoreMigrationWrapper(delegate, recorder).migrate(nullDatabase())
        }

        assertEquals(0, delegateCalls)
        assertFalse(requireNotNull(store.marker).migrationAttemptStarted)
    }

    @Test
    fun wrapper_markerReadFailure_stopsBeforeDelegate() {
        val store = FakeStore(
            marker = marker(RestoreStatus.MIGRATION_PENDING, version = 8),
            readFailure = IOException("marker unreadable"),
        )
        val recorder = PendingRestoreMigrationAttemptRecorder(store)
        var delegateCalls = 0
        val delegate = countingMigration(8, 9) { delegateCalls++ }

        assertThrows(IOException::class.java) {
            PendingRestoreMigrationWrapper(delegate, recorder).migrate(nullDatabase())
        }

        assertEquals(0, delegateCalls)
    }

    private fun marker(
        status: RestoreStatus,
        version: Int,
        attemptStarted: Boolean = false,
    ) = PendingRestoreMarker(
        status = status,
        createdAt = "2026-07-03T00:00:00Z",
        includeCookies = false,
        databaseVersion = version,
        migrationAttemptStarted = attemptStarted,
    )

    private fun countingMigration(
        startVersion: Int,
        endVersion: Int,
        onMigrate: () -> Unit,
    ) = object : Migration(startVersion, endVersion) {
        override fun migrate(db: SupportSQLiteDatabase) {
            onMigrate()
        }
    }

    private fun nullDatabase(): SupportSQLiteDatabase = mockk(relaxed = true)

    /** recorder test 用の marker store。conditional mutation は interface default を利用する。 */
    private class FakeStore(
        var marker: PendingRestoreMarker?,
        private val readFailure: IOException? = null,
        private val writeFailure: IOException? = null,
    ) : PendingRestoreFileStore {
        override val pendingDir: File = File("pending")
        override val rollbackDir: File = File(pendingDir, "rollback")
        override val quarantineRootDir: File = File("quarantine")
        var writeCount = 0

        override fun createQuarantineIncidentDir(): File = quarantineRootDir

        override fun readMarker(): PendingRestoreMarker? {
            readFailure?.let { throw it }
            return marker
        }

        override fun writeMarker(marker: PendingRestoreMarker) {
            writeCount++
            writeFailure?.let { throw it }
            this.marker = marker
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
        ) = Unit

        override fun cleanupPending(): Boolean = true

        override fun cleanupResult() = Unit
    }
}
