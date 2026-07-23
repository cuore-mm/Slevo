package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import com.websarva.wings.android.slevo.data.backup.restore.BackupDatabaseValidator
import com.websarva.wings.android.slevo.data.backup.restore.RealBackupDatabaseValidator
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.File

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

        Assert.assertEquals(emptyList<String>(), fileStore.events)
        Assert.assertTrue(reflector.calls.isEmpty())
        Assert.assertTrue(validator.validatedFiles.isEmpty())
    }

    @Test
    fun prepared_happyPath_transitionsToMigrationPending() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED, includeCookies = true)
        dbSwapper.liveDbExists = true

        createApplier().runIfNeeded()

        Assert.assertEquals(
            listOf(
                "writeMarker:APPLYING",
                "writeMarker:DB_SWAPPED",
                "writeResult:true:restore completed successfully",
                "writeMarker:MIGRATION_PENDING",
            ),
            fileStore.events,
        )
        Assert.assertEquals(listOf(fileStore.pendingDir to true), reflector.calls)
        Assert.assertEquals(listOf(dbSwapper.liveDbPath), validator.validatedFiles)
        Assert.assertTrue(dbSwapper.rollbackRequested)
        Assert.assertTrue(dbSwapper.replaceRequested)
        Assert.assertEquals(RestoreStatus.MIGRATION_PENDING, fileStore.lastWrittenMarker?.status)
    }

    // --- 4.3: stale MIGRATION_PENDING (strict validation success) ---

    @Test
    fun migrationPending_strictValidationPasses_transitionsToCompleted() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        dbSwapper.hasRollbackBackup = true
        validator.nextValidateResult = null // strict validation OK

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.COMPLETED, fileStore.lastWrittenMarker?.status)
        Assert.assertFalse(fileStore.events.contains("writeResult:false"))
    }

    // --- 4.3: stale MIGRATION_PENDING (strict validation failed + rollback backup exists) ---

    @Test
    fun migrationPending_strictValidationFailsWithRollback_rollsBack() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        dbSwapper.hasRollbackBackup = true
        validator.nextValidateResult = "identity hash mismatch"

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(dbSwapper.restoreRollbackRequested)
        Assert.assertTrue(fileStore.events.contains("writeResult:false:stale MIGRATION_PENDING: identity hash mismatch"))
    }

    // --- 4.3: stale MIGRATION_PENDING (strict validation failed + no rollback backup) ---

    @Test
    fun migrationPending_strictValidationFailsNoRollback_quarantines() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        dbSwapper.hasRollbackBackup = false
        validator.nextValidateResult = "identity hash mismatch"

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(fileStore.lastWrittenMarker!!.failureReason!!.contains("quarantine"))
        Assert.assertFalse(dbSwapper.restoreRollbackRequested)
    }

    // --- 4.4: ROLLBACK_REQUIRED ---

    @Test
    fun rollbackRequired_withBackup_rollsBack() = runTest {
        fileStore.marker = marker(RestoreStatus.ROLLBACK_REQUIRED)
        dbSwapper.hasRollbackBackup = true

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(dbSwapper.restoreRollbackRequested)
    }

    @Test
    fun rollbackRequired_noBackup_quarantines() = runTest {
        fileStore.marker = marker(RestoreStatus.ROLLBACK_REQUIRED)
        dbSwapper.hasRollbackBackup = false

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(fileStore.lastWrittenMarker!!.failureReason!!.contains("quarantine"))
    }

    // --- 4.5: COMPLETED (retry success result + cleanup) ---

    @Test
    fun completed_retriesSuccessResultAndCleanup() = runTest {
        fileStore.marker = marker(RestoreStatus.COMPLETED)

        createApplier().runIfNeeded()

        Assert.assertEquals(
            listOf("writeResult:true:restore completed successfully", "cleanupPending"),
            fileStore.events,
        )
    }

    // --- 4.8: current version も MIGRATION_PENDING を通る ---

    @Test
    fun prepared_currentVersion_transitionsToMigrationPending() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED, includeCookies = false)
        dbSwapper.liveDbExists = true

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.MIGRATION_PENDING, fileStore.lastWrittenMarker?.status)
        Assert.assertFalse(fileStore.events.contains("cleanupPending"))
    }

    @Test
    fun failedMarker_isNotRetried() = runTest {
        fileStore.marker = marker(RestoreStatus.FAILED)

        createApplier().runIfNeeded()

        Assert.assertEquals(emptyList<String>(), fileStore.events)
        Assert.assertTrue(reflector.calls.isEmpty())
        Assert.assertTrue(validator.validatedFiles.isEmpty())
    }

    @Test
    fun staleApplying_rollsBackAndWritesFailureResult() = runTest {
        fileStore.marker = marker(RestoreStatus.APPLYING)
        dbSwapper.hasRollbackBackup = true

        createApplier().runIfNeeded()

        Assert.assertTrue(dbSwapper.restoreRollbackRequested)
        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertEquals("writeResult:false:stale marker: APPLYING", fileStore.events[1])
        Assert.assertEquals("cleanupPending", fileStore.events.last())
    }

    @Test
    fun rollbackBackupCreationFailure_keepsLiveDbUnchanged() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED)
        dbSwapper.liveDbExists = true
        dbSwapper.createRollbackBackupResult = "backup failed"

        createApplier().runIfNeeded()

        Assert.assertFalse(dbSwapper.replaceRequested)
        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(fileStore.events.contains("writeResult:false:backup failed"))
    }

    @Test
    fun rollbackCopyFailure_preservesPendingForManualRecovery() = runTest {
        fileStore.marker = marker(RestoreStatus.APPLYING)
        dbSwapper.hasRollbackBackup = true
        dbSwapper.restoreRollbackBackupResult = false

        createApplier().runIfNeeded()

        Assert.assertTrue(dbSwapper.restoreRollbackRequested)
        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertFalse(fileStore.events.contains("cleanupPending"))
    }

    @Test
    fun freshInstallValidationFailure_deletesCorruptLiveDb() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED)
        dbSwapper.liveDbExists = false
        validator.nextResult = "invalid db"

        createApplier().runIfNeeded()

        Assert.assertTrue(dbSwapper.cleanupCorruptRequested)
        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(
            fileStore.events.contains("writeResult:false:post-replace validation failed: invalid db"),
        )
    }

    @Test
    fun unexpectedException_doesNotEscapeAndWritesFailureResult() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED)
        reflector.throwOnReflect = IllegalStateException("boom")

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertEquals(
            "unexpected error: boom",
            fileStore.lastWrittenMarker?.failureReason,
        )
        Assert.assertEquals("writeResult:false:unexpected error: boom", fileStore.events.last())
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
            databaseVersion = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION,
        )
    }

    /** [BackupDatabaseValidator] の fake 実装。 */
    internal class FakeBackupDatabaseValidator : BackupDatabaseValidator {
        val validatedFiles = mutableListOf<File>()
        var nextResult: String? = null
        var nextValidateResult: String? = null

        override fun validate(dbFile: File): String? {
            validatedFiles += dbFile
            return nextValidateResult
        }

        override fun preValidate(dbFile: File, manifestDatabaseVersion: Int): String? {
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

    // --- Cookie failure / ordering tests ---

    @Test
    fun prepared_cookieParseFailure_rollbackAndFail() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED, includeCookies = true)
        dbSwapper.liveDbExists = true
        reflector.result = "failed to parse cookies JSON"

        createApplier().runIfNeeded()

        Assert.assertTrue("rollback should be requested", dbSwapper.rollbackRequested)
        Assert.assertTrue(
            "cookie parse error should appear in events",
            fileStore.events.any { it == "writeResult:false:failed to parse cookies JSON" },
        )
    }

    @Test
    fun prepared_cookieSerializeFailure_rollbackAndFail() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED, includeCookies = true)
        dbSwapper.liveDbExists = true
        reflector.result = "failed to serialize restored cookies: failed=1 total=1"

        createApplier().runIfNeeded()

        Assert.assertTrue("rollback should be requested", dbSwapper.rollbackRequested)
        Assert.assertTrue(
            fileStore.events.any { it.contains("failed to serialize restored cookies") },
        )
    }

    @Test
    fun prepared_includeCookiesFalse_doesNotRequestCookieReflection() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED, includeCookies = false)
        dbSwapper.liveDbExists = true

        createApplier().runIfNeeded()

        Assert.assertEquals(listOf(fileStore.pendingDir to false), reflector.calls)
        Assert.assertTrue(
            fileStore.events.any { it == "writeMarker:MIGRATION_PENDING" },
        )
    }
}
