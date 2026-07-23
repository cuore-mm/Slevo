package com.websarva.wings.android.slevo.data.backup

import android.content.Context
import com.websarva.wings.android.slevo.data.backup.PendingRestoreApplierTest.FakeBackupDatabaseValidator
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PendingRestoreApplier] の orchestration を fake collaborator で検証する。
 */
class PendingRestoreApplierTest {
    private lateinit var context: Context
    private lateinit var validator: FakeBackupDatabaseValidator
    private lateinit var fileStore: FakePendingRestoreFileStore
    private lateinit var dbSwapper: FakePendingRestoreDbSwapper
    private lateinit var reflector: FakePendingRestoreDataStoreReflector

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        validator = FakeBackupDatabaseValidator()
        fileStore = FakePendingRestoreFileStore()
        dbSwapper = FakePendingRestoreDbSwapper()
        reflector = FakePendingRestoreDataStoreReflector()
    }

    @Test
    fun runIfNeeded_doesNothingWhenMarkerDoesNotExist() = runTest {
        createApplier().runIfNeeded()

        assertEquals(emptyList<String>(), fileStore.events)
        assertTrue(reflector.calls.isEmpty())
        assertTrue(validator.validatedFiles.isEmpty())
    }

    @Test
    fun prepared_happyPath_runsCollaboratorsInOrder() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED, includeCookies = true)
        dbSwapper.liveDbExists = true

        createApplier().runIfNeeded()

        assertEquals(
            listOf(
                "writeMarker:APPLYING",
                "writeMarker:DB_SWAPPED",
                "writeResult:true:restore completed successfully",
                "cleanupPending",
            ),
            fileStore.events,
        )
        assertEquals(listOf(fileStore.pendingDir to true), reflector.calls)
        assertEquals(listOf(dbSwapper.liveDbPath), validator.validatedFiles)
        assertTrue(dbSwapper.rollbackRequested)
        assertTrue(dbSwapper.replaceRequested)
    }

    @Test
    fun failedMarker_isNotRetried() = runTest {
        fileStore.marker = marker(RestoreStatus.FAILED)

        createApplier().runIfNeeded()

        assertEquals(listOf("cleanupPending"), fileStore.events)
        assertTrue(reflector.calls.isEmpty())
        assertTrue(validator.validatedFiles.isEmpty())
    }

    @Test
    fun staleApplying_rollsBackAndWritesFailureResult() = runTest {
        fileStore.marker = marker(RestoreStatus.APPLYING)
        dbSwapper.hasRollbackBackup = true

        createApplier().runIfNeeded()

        assertTrue(dbSwapper.restoreRollbackRequested)
        assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        assertEquals("writeResult:false:stale marker: APPLYING", fileStore.events[1])
        assertEquals("cleanupPending", fileStore.events.last())
    }

    @Test
    fun rollbackBackupCreationFailure_keepsLiveDbUnchanged() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED)
        dbSwapper.liveDbExists = true
        dbSwapper.createRollbackBackupResult = "backup failed"

        createApplier().runIfNeeded()

        assertFalse(dbSwapper.replaceRequested)
        assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        assertTrue(fileStore.events.contains("writeResult:false:backup failed"))
    }

    @Test
    fun rollbackCopyFailure_preservesPendingForManualRecovery() = runTest {
        fileStore.marker = marker(RestoreStatus.APPLYING)
        dbSwapper.hasRollbackBackup = true
        dbSwapper.restoreRollbackBackupResult = false

        createApplier().runIfNeeded()

        assertTrue(dbSwapper.restoreRollbackRequested)
        assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        assertFalse(fileStore.events.contains("cleanupPending"))
    }

    @Test
    fun freshInstallValidationFailure_deletesCorruptLiveDb() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED)
        dbSwapper.liveDbExists = false
        validator.nextResult = "invalid db"

        createApplier().runIfNeeded()

        assertTrue(dbSwapper.cleanupCorruptRequested)
        assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        assertTrue(
            fileStore.events.contains("writeResult:false:post-replace validation failed: invalid db"),
        )
    }

    @Test
    fun unexpectedException_doesNotEscapeAndWritesFailureResult() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED)
        reflector.throwOnReflect = IllegalStateException("boom")

        createApplier().runIfNeeded()

        assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        assertEquals(
            "unexpected error: boom",
            fileStore.lastWrittenMarker?.failureReason,
        )
        assertEquals("writeResult:false:unexpected error: boom", fileStore.events.last())
    }

    private fun createApplier(): PendingRestoreApplier {
        return PendingRestoreApplier.createForTest(
            context = context,
            dbValidator = validator,
            dataStoreReflector = reflector,
            fileStore = fileStore,
            dbSwapper = dbSwapper,
            nowProvider = { "2026-07-03T00:00:00Z" },
        )
    }

    private fun marker(status: RestoreStatus, includeCookies: Boolean = false): PendingRestoreMarker {
        return PendingRestoreMarker(
            status = status,
            createdAt = "2026-07-03T00:00:00Z",
            includeCookies = includeCookies,
            databaseVersion = RealBackupDatabaseValidator.EXPECTED_USER_VERSION,
        )
    }

    /** [BackupDatabaseValidator] の fake 実装。 */
    internal class FakeBackupDatabaseValidator : BackupDatabaseValidator {
        val validatedFiles = mutableListOf<File>()
        var nextResult: String? = null

        override fun validate(dbFile: File): String? {
            validatedFiles += dbFile
            return nextResult
        }
    }

    /** [PendingRestoreFileStore] の fake 実装。 */
    private class FakePendingRestoreFileStore : PendingRestoreFileStore {
        override val pendingDir: File = File("pending-dir")
        override val rollbackDir: File = File(pendingDir, "rollback")
        var marker: PendingRestoreMarker? = null
        var lastWrittenMarker: PendingRestoreMarker? = null
        val events = mutableListOf<String>()

        override fun readMarker(): PendingRestoreMarker? = marker

        override fun writeMarker(marker: PendingRestoreMarker) {
            lastWrittenMarker = marker
            this.marker = marker
            events += "writeMarker:${marker.status}"
        }

        override fun writeResult(success: Boolean, message: String, timestamp: String) {
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

    /** [PendingRestoreDbSwapper] の fake 実装。 */
    private class FakePendingRestoreDbSwapper : PendingRestoreDbSwapper {
        val liveDbPath = File(System.getProperty("java.io.tmpdir"), "live-db-${System.nanoTime()}")
        var liveDbExists = false
        var hasRollbackBackup = false
        var createRollbackBackupResult: String? = null
        var replaceDbFileResult: String? = null
        var restoreRollbackBackupResult = true
        var rollbackRequested = false
        var replaceRequested = false
        var restoreRollbackRequested = false
        var cleanupCorruptRequested = false

        override fun getLiveDbFile(): File {
            if (liveDbExists && !liveDbPath.exists()) {
                liveDbPath.writeText("live-db")
            }
            return liveDbPath
        }

        override fun createRollbackBackup(liveDbFile: File, rollbackDir: File): String? {
            rollbackRequested = true
            return createRollbackBackupResult
        }

        override fun replaceDbFile(stagedDbFile: File, liveDbFile: File): String? {
            replaceRequested = true
            return replaceDbFileResult
        }

        override fun hasRollbackBackup(rollbackDir: File, liveDbFile: File): Boolean = hasRollbackBackup

        override fun restoreRollbackBackup(liveDbFile: File, rollbackDir: File): Boolean {
            restoreRollbackRequested = true
            return restoreRollbackBackupResult
        }

        override fun cleanupCorruptFreshInstallDb(liveDbFile: File) {
            cleanupCorruptRequested = true
        }
    }

    /** [PendingRestoreDataStoreReflector] の fake 実装。 */
    private class FakePendingRestoreDataStoreReflector : PendingRestoreDataStoreReflector {
        val calls = mutableListOf<Pair<File, Boolean>>()
        var throwOnReflect: Exception? = null
        var result: String? = null

        override suspend fun reflect(pendingDir: File, includeCookies: Boolean): String? {
            calls += pendingDir to includeCookies
            throwOnReflect?.let { throw it }
            return result
        }
    }
}
