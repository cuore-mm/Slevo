package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.data.backup.restore.BackupDatabaseValidator
import com.websarva.wings.android.slevo.data.backup.restore.RealBackupDatabaseValidator
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [PendingRestoreApplier] の orchestration を fake collaborator で検証する。
 */
@OptIn(ExperimentalStdlibApi::class)
@RunWith(RobolectricTestRunner::class)
class PendingRestoreApplierTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

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
                "writeMarker:ROLLBACK_READY",
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
        Assert.assertEquals(true, fileStore.lastWrittenMarker?.hadExistingLiveDb)
    }

    // --- 8.1: same-startup migration finalization ordering ---

    @Test
    fun migrationPending_strictValidationPasses_finalizesInDurableOrder() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        dbSwapper.hasRollbackBackup = true
        validator.nextValidateResult = null // strict validation OK
        validator.userVersion = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.COMPLETED, fileStore.lastWrittenMarker?.status)
        Assert.assertEquals(
            listOf(
                "writeMarker:COMPLETED",
                "writeResult:true:restore completed successfully",
                "cleanupPending",
            ),
            fileStore.events,
        )
        Assert.assertTrue(fileStore.results.single().migrationCompleted)
    }

    @Test
    fun migrationPending_completedMarkerWriteFailure_keepsRetryableState() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.userVersion = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION
        fileStore.markerWriteFailure = IOException("marker unavailable")

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.MIGRATION_PENDING, fileStore.marker?.status)
        Assert.assertTrue(fileStore.events.isEmpty())
        Assert.assertTrue(fileStore.results.isEmpty())
    }

    @Test
    fun migrationPending_successResultWriteFailure_keepsCompletedMarkerAndSkipsCleanup() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        validator.userVersion = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION
        fileStore.resultWriteFailure = IOException("result unavailable")

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.COMPLETED, fileStore.marker?.status)
        Assert.assertEquals(listOf("writeMarker:COMPLETED"), fileStore.events)
        Assert.assertFalse(fileStore.events.contains("cleanupPending"))
    }

    @Test
    fun completed_cleanupFailure_keepsMarkerForRetry() = runTest {
        fileStore.marker = marker(RestoreStatus.COMPLETED)
        fileStore.cleanupReturnsFalse = true

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.COMPLETED, fileStore.marker?.status)
        Assert.assertEquals(
            listOf(
                "writeResult:true:restore completed successfully",
                "cleanupPending",
            ),
            fileStore.events,
        )
    }

    // --- 4.3: stale MIGRATION_PENDING (strict validation failed + rollback backup exists) ---

    @Test
    fun migrationPending_strictValidationFailsWithRollback_rollsBack() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        dbSwapper.hasRollbackBackup = true
        validator.nextValidateResult = "identity hash mismatch"
        validator.userVersion = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(dbSwapper.restoreRollbackRequested)
        Assert.assertTrue(fileStore.events.contains("writeResult:false:stale MIGRATION_PENDING: identity hash mismatch (post-migration)"))
    }

    // --- 4.3: stale MIGRATION_PENDING (strict validation failed + no rollback backup) ---

    @Test
    fun migrationPending_strictValidationFailsNoRollback_quarantines() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        dbSwapper.hasRollbackBackup = false
        validator.nextValidateResult = "identity hash mismatch"
        validator.userVersion = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(fileStore.lastWrittenMarker!!.failureReason!!.contains("quarantine"))
        Assert.assertFalse(dbSwapper.restoreRollbackRequested)
    }

    @Test
    fun migrationPending_strictValidationFailsNoRollback_preservesRealQuarantineAfterCleanup() = runTest {
        val filesDir = tempFolder.newFolder("files")
        every { context.filesDir } returns filesDir
        val moshi = Moshi.Builder().build()
        val realStore = RealPendingRestoreFileStore(context, moshi)
        realStore.writeMarker(marker(RestoreStatus.MIGRATION_PENDING))
        dbSwapper.liveDbExists = true
        dbSwapper.hasRollbackBackup = false
        validator.userVersion = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION
        validator.nextValidateResult = "identity hash mismatch"
        File(dbSwapper.liveDbPath.absolutePath + "-wal").writeText("wal")
        File(dbSwapper.liveDbPath.absolutePath + "-shm").writeText("shm")

        PendingRestoreApplier.createForTest(
            context = context,
            dbValidator = validator,
            dataStoreReflector = reflector,
            fileStore = realStore,
            dbSwapper = dbSwapper,
            nowProvider = { "2026-07-03T00:00:00Z" },
        ).runIfNeeded()

        val incidents = realStore.quarantineRootDir.listFiles().orEmpty()
        Assert.assertEquals(1, incidents.size)
        Assert.assertEquals("live-db", File(incidents.single(), dbSwapper.liveDbPath.name).readText())
        Assert.assertEquals("wal", File(incidents.single(), "${dbSwapper.liveDbPath.name}-wal").readText())
        Assert.assertEquals("shm", File(incidents.single(), "${dbSwapper.liveDbPath.name}-shm").readText())
        Assert.assertFalse(realStore.pendingDir.exists())

        val resultFile = File(
            filesDir,
            "${PendingRestoreManager.RESULT_DIR_NAME}/${PendingRestoreManager.RESULT_FILENAME}",
        )
        val result = moshi.adapter<PendingRestoreResultFile>().fromJson(resultFile.readText())
        Assert.assertNotNull(result)
        Assert.assertTrue(result!!.message.contains(incidents.single().canonicalPath))

        // A cold-start retry must not remove the independent recovery artifact.
        PendingRestoreApplier.createForTest(
            context = context,
            dbValidator = validator,
            dataStoreReflector = reflector,
            fileStore = realStore,
            dbSwapper = dbSwapper,
            nowProvider = { "2026-07-03T00:00:00Z" },
        ).runIfNeeded()
        Assert.assertTrue(File(incidents.single(), dbSwapper.liveDbPath.name).exists())
    }

    @Test
    fun migrationPending_strictValidationFailsNoRollback_withoutSidecars_preservesMainDb() = runTest {
        val filesDir = tempFolder.newFolder("files")
        every { context.filesDir } returns filesDir
        val realStore = RealPendingRestoreFileStore(context, Moshi.Builder().build())
        realStore.writeMarker(marker(RestoreStatus.MIGRATION_PENDING))
        dbSwapper.liveDbExists = true
        dbSwapper.hasRollbackBackup = false
        validator.userVersion = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION
        validator.nextValidateResult = "identity hash mismatch"

        PendingRestoreApplier.createForTest(
            context = context,
            dbValidator = validator,
            dataStoreReflector = reflector,
            fileStore = realStore,
            dbSwapper = dbSwapper,
            nowProvider = { "2026-07-03T00:00:00Z" },
        ).runIfNeeded()

        val incident = realStore.quarantineRootDir.listFiles().orEmpty().single()
        Assert.assertTrue(File(incident, dbSwapper.liveDbPath.name).exists())
        Assert.assertFalse(File(incident, "${dbSwapper.liveDbPath.name}-wal").exists())
        Assert.assertFalse(File(incident, "${dbSwapper.liveDbPath.name}-shm").exists())
    }

    @Test
    fun migrationPending_quarantineCreationFails_doesNotReportMissingSuccessPath() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        dbSwapper.hasRollbackBackup = false
        validator.userVersion = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION
        validator.nextValidateResult = "identity hash mismatch"
        fileStore.quarantineCreationFailure = IOException("root unavailable")

        createApplier().runIfNeeded()

        val reason = fileStore.lastWrittenMarker!!.failureReason!!
        Assert.assertTrue(reason.contains("quarantine failed"))
        Assert.assertFalse(reason.contains("quarantined to"))
    }

    @Test
    fun migrationPending_mainDbMoveAndCopyFail_doesNotReportMissingSuccessPath() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        dbSwapper.liveDbExists = true
        dbSwapper.hasRollbackBackup = false
        validator.userVersion = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION
        validator.nextValidateResult = "identity hash mismatch"
        fileStore.quarantineIncidentIsFile = true

        try {
            createApplier().runIfNeeded()

            val reason = fileStore.lastWrittenMarker!!.failureReason!!
            Assert.assertTrue(reason.contains("quarantine failed"))
            Assert.assertFalse(reason.contains("quarantined to"))
        } finally {
            dbSwapper.liveDbPath.deleteRecursively()
        }
    }

    @Test
    fun migrationPending_cleanupFailure_preservesSavedIncident() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        dbSwapper.liveDbExists = true
        dbSwapper.hasRollbackBackup = false
        validator.userVersion = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION
        validator.nextValidateResult = "identity hash mismatch"
        fileStore.cleanupFailure = IOException("cleanup unavailable")

        createApplier().runIfNeeded()

        val incident = fileStore.quarantineRootDir.listFiles().orEmpty().single()
        Assert.assertTrue(File(incident, dbSwapper.liveDbPath.name).exists())
    }

    @Test
    fun migrationPending_resultWriteFailure_preservesSavedIncident() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        dbSwapper.liveDbExists = true
        dbSwapper.hasRollbackBackup = false
        validator.userVersion = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION
        validator.nextValidateResult = "identity hash mismatch"
        fileStore.resultWriteFailure = IOException("result unavailable")

        createApplier().runIfNeeded()

        val incident = fileStore.quarantineRootDir.listFiles().orEmpty().single()
        Assert.assertTrue(File(incident, dbSwapper.liveDbPath.name).exists())
    }

    // --- 4.3a: stale MIGRATION_PENDING (pre-migration: validation passes, waits) ---

    @Test
    fun migrationPending_roomNotMigratedYet_preValidatePasses_keepsMigrationPending() = runTest {
        val oldVersion = 7
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING, databaseVersion = oldVersion)
        dbSwapper.hasRollbackBackup = true
        validator.userVersion = oldVersion // Room migration 前
        validator.nextResult = null // preValidate success

        createApplier().runIfNeeded()

        // marker は再書き込みせず、MIGRATION_PENDING のまま保持する。
        Assert.assertEquals(RestoreStatus.MIGRATION_PENDING, fileStore.marker?.status)
        Assert.assertNull("migration 前の待機では marker を書き直さない", fileStore.lastWrittenMarker)
        // rollback は呼ばれない
        Assert.assertFalse(dbSwapper.restoreRollbackRequested)
        // writeResult:false は記録されない (failure path に入っていない)
        Assert.assertFalse(fileStore.events.any { it.contains("writeResult:false") })
        // cleanup も呼ばれない
        Assert.assertFalse(fileStore.events.contains("cleanupPending"))
    }

    // --- 4.3a: stale MIGRATION_PENDING (pre-migration: validation fails + rollback exists) ---

    @Test
    fun migrationPending_roomNotMigratedYet_preValidateFails_rollsBackWhenBackupExists() = runTest {
        val oldVersion = 7
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING, databaseVersion = oldVersion)
        dbSwapper.hasRollbackBackup = true
        validator.userVersion = oldVersion // Room migration 前
        validator.nextResult = "integrity check failed" // preValidate failure

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(dbSwapper.restoreRollbackRequested)
        Assert.assertTrue(
            fileStore.events.any {
                it.contains("writeResult:false:stale MIGRATION_PENDING (pre-migration failure): integrity check failed")
            },
        )
    }

    // --- 4.3a: stale MIGRATION_PENDING (pre-migration: validation fails + no rollback) ---

    @Test
    fun migrationPending_roomNotMigratedYet_preValidateFails_quarantinesWithoutRollback() = runTest {
        val oldVersion = 7
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING, databaseVersion = oldVersion)
        dbSwapper.hasRollbackBackup = false
        validator.userVersion = oldVersion // Room migration 前
        validator.nextResult = "integrity check failed" // preValidate failure

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(fileStore.lastWrittenMarker!!.failureReason!!.contains("quarantine"))
        Assert.assertFalse(dbSwapper.restoreRollbackRequested)
    }

    // --- 4.3b: stale MIGRATION_PENDING (unreadable DB) ---

    @Test
    fun migrationPending_unreadableDb_rollsBackOrQuarantines() = runTest {
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING)
        dbSwapper.hasRollbackBackup = true
        validator.userVersion = null // 読み取り不可

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(
            fileStore.lastWrittenMarker!!.failureReason!!.contains("userVersion=null"),
        )
        Assert.assertTrue(dbSwapper.restoreRollbackRequested)
    }

    // --- 4.3c: stale MIGRATION_PENDING (unexpected intermediate version) ---

    @Test
    fun migrationPending_unexpectedIntermediateVersion_rollsBackOrQuarantines() = runTest {
        val oldVersion = 7
        fileStore.marker = marker(RestoreStatus.MIGRATION_PENDING, databaseVersion = oldVersion)
        dbSwapper.hasRollbackBackup = true
        validator.userVersion = 8 // 中間 version (marker=7, current=EXPECTED, user=8)

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(
            fileStore.lastWrittenMarker!!.failureReason!!.contains("unexpected intermediate version"),
        )
        Assert.assertTrue(
            fileStore.lastWrittenMarker!!.failureReason!!.contains("userVersion=8"),
        )
        Assert.assertTrue(dbSwapper.restoreRollbackRequested)
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
        Assert.assertEquals(true, fileStore.lastWrittenMarker?.hadExistingLiveDb)
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
    fun staleApplying_withCompleteSnapshot_andExistingLiveDb_rollsBack() = runTest {
        fileStore.marker = marker(RestoreStatus.APPLYING, hadExistingLiveDb = true)
        dbSwapper.hasRollbackBackup = true

        createApplier().runIfNeeded()

        Assert.assertTrue(dbSwapper.restoreRollbackRequested)
        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(
            fileStore.events.any { it == "writeResult:false:stale marker: APPLYING" },
        )
        Assert.assertEquals("cleanupPending", fileStore.events.last())
    }

    @Test
    fun staleApplying_withIncompleteSnapshot_preservesLiveDb() = runTest {
        fileStore.marker = marker(RestoreStatus.APPLYING, hadExistingLiveDb = true)
        dbSwapper.hasRollbackBackup = false

        createApplier().runIfNeeded()

        Assert.assertFalse(dbSwapper.restoreRollbackRequested)
        Assert.assertFalse(dbSwapper.cleanupCorruptRequested)
        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(
            fileStore.lastWrittenMarker?.failureReason?.contains("incomplete rollback snapshot") == true,
        )
        Assert.assertFalse(fileStore.events.contains("cleanupPending"))
    }

    @Test
    fun staleApplying_freshInstall_cleansUpCorruptLiveDb() = runTest {
        fileStore.marker = marker(RestoreStatus.APPLYING, hadExistingLiveDb = false)
        dbSwapper.hasRollbackBackup = false

        createApplier().runIfNeeded()

        Assert.assertTrue(dbSwapper.cleanupCorruptRequested)
        Assert.assertFalse(dbSwapper.restoreRollbackRequested)
        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
    }

    @Test
    fun prepared_writesHadExistingLiveDbInApplyingMarker() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED)
        dbSwapper.liveDbExists = true

        createApplier().runIfNeeded()

        val applyingMarker = fileStore.writtenMarkers.find { it.status == RestoreStatus.APPLYING }
        Assert.assertNotNull(applyingMarker)
        Assert.assertEquals(true, applyingMarker?.hadExistingLiveDb)
    }

    @Test
    fun prepared_noExistingLiveDb_skipsRollbackBackup() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED)
        dbSwapper.liveDbExists = false

        createApplier().runIfNeeded()

        Assert.assertFalse(dbSwapper.rollbackRequested)
        Assert.assertTrue(dbSwapper.replaceRequested)
        Assert.assertEquals(RestoreStatus.MIGRATION_PENDING, fileStore.lastWrittenMarker?.status)
        Assert.assertEquals(false, fileStore.lastWrittenMarker?.hadExistingLiveDb)
    }

    @Test
    fun prepared_snapshotFailure_doesNotReplaceDatabase() = runTest {
        fileStore.marker = marker(RestoreStatus.PREPARED)
        dbSwapper.liveDbExists = true
        reflector.prepareSnapshotResult = "snapshot write failed"

        createApplier().runIfNeeded()

        Assert.assertEquals(1, reflector.prepareSnapshotCalls)
        Assert.assertFalse(dbSwapper.replaceRequested)
        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(fileStore.events.contains("cleanupPending"))
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
        fileStore.marker = marker(RestoreStatus.APPLYING, hadExistingLiveDb = true)
        dbSwapper.hasRollbackBackup = true
        dbSwapper.restoreRollbackBackupResult = false

        createApplier().runIfNeeded()

        Assert.assertTrue(dbSwapper.restoreRollbackRequested)
        Assert.assertEquals(RestoreStatus.ROLLBACK_REQUIRED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(reflector.rollbackSnapshotCalls > 0)
        Assert.assertFalse(fileStore.events.contains("cleanupPending"))
    }

    @Test
    fun staleRollbackReady_withCompleteSnapshot_andExistingLiveDb_rollsBack() = runTest {
        fileStore.marker = marker(RestoreStatus.ROLLBACK_READY, hadExistingLiveDb = true)
        dbSwapper.hasRollbackBackup = true

        createApplier().runIfNeeded()

        Assert.assertTrue(dbSwapper.restoreRollbackRequested)
        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
    }

    @Test
    fun staleRollbackReady_withIncompleteSnapshot_preservesLiveDb() = runTest {
        fileStore.marker = marker(RestoreStatus.ROLLBACK_READY, hadExistingLiveDb = true)
        dbSwapper.hasRollbackBackup = false

        createApplier().runIfNeeded()

        Assert.assertFalse(dbSwapper.restoreRollbackRequested)
        Assert.assertFalse(dbSwapper.cleanupCorruptRequested)
        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(
            fileStore.lastWrittenMarker?.failureReason?.contains("incomplete rollback snapshot") == true,
        )
    }

    @Test
    fun staleRollbackReady_freshInstall_cleansUpCorruptLiveDb() = runTest {
        fileStore.marker = marker(RestoreStatus.ROLLBACK_READY, hadExistingLiveDb = false)
        dbSwapper.hasRollbackBackup = false

        createApplier().runIfNeeded()

        Assert.assertTrue(dbSwapper.cleanupCorruptRequested)
        Assert.assertFalse(dbSwapper.restoreRollbackRequested)
        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
    }

    @Test
    fun staleDbSwapped_withIncompleteSnapshot_preservesLiveDb() = runTest {
        fileStore.marker = marker(RestoreStatus.DB_SWAPPED, hadExistingLiveDb = true)
        dbSwapper.hasRollbackBackup = false

        createApplier().runIfNeeded()

        Assert.assertFalse(dbSwapper.restoreRollbackRequested)
        Assert.assertFalse(dbSwapper.cleanupCorruptRequested)
        Assert.assertEquals(RestoreStatus.ROLLBACK_REQUIRED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(
            fileStore.lastWrittenMarker?.failureReason?.contains("incomplete rollback snapshot") == true,
        )
    }

    @Test
    fun rollbackRequired_dataStoreFailure_preservesArtifactsAndRetries() = runTest {
        fileStore.marker = marker(RestoreStatus.ROLLBACK_REQUIRED, hadExistingLiveDb = true)
        dbSwapper.hasRollbackBackup = true
        reflector.rollbackSnapshotResult = "DataStore rollback failed"

        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.ROLLBACK_REQUIRED, fileStore.lastWrittenMarker?.status)
        Assert.assertFalse(fileStore.events.contains("cleanupPending"))

        reflector.rollbackSnapshotResult = null
        createApplier().runIfNeeded()

        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(fileStore.events.contains("cleanupPending"))
        Assert.assertEquals(2, reflector.rollbackSnapshotCalls)
    }

    @Test
    fun staleApplying_legacyMarkerWithoutHadExistingLiveDb_preservesFiles() = runTest {
        fileStore.marker = marker(RestoreStatus.APPLYING, hadExistingLiveDb = null)
        dbSwapper.liveDbExists = true
        dbSwapper.hasRollbackBackup = false

        createApplier().runIfNeeded()

        Assert.assertFalse(dbSwapper.restoreRollbackRequested)
        Assert.assertFalse(dbSwapper.cleanupCorruptRequested)
        Assert.assertEquals(RestoreStatus.FAILED, fileStore.lastWrittenMarker?.status)
        Assert.assertTrue(
            fileStore.lastWrittenMarker?.failureReason?.contains("manual recovery required") == true,
        )
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

    private fun marker(
        status: RestoreStatus,
        includeCookies: Boolean = false,
        databaseVersion: Int = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION,
        hadExistingLiveDb: Boolean? = null,
    ): PendingRestoreMarker {
        return PendingRestoreMarker(
            status = status,
            createdAt = "2026-07-03T00:00:00Z",
            includeCookies = includeCookies,
            databaseVersion = databaseVersion,
            hadExistingLiveDb = hadExistingLiveDb,
        )
    }

    /** [BackupDatabaseValidator] の fake 実装。 */
    internal class FakeBackupDatabaseValidator : BackupDatabaseValidator {
        val validatedFiles = mutableListOf<File>()
        var nextResult: String? = null
        var nextValidateResult: String? = null
        var userVersion: Int? = null

        override fun validate(dbFile: File): String? {
            validatedFiles += dbFile
            return nextValidateResult
        }

        override fun preValidate(dbFile: File, manifestDatabaseVersion: Int): String? {
            validatedFiles += dbFile
            return nextResult
        }

        override fun getUserVersion(dbFile: File): Int? = userVersion
    }

    /** [PendingRestoreFileStore] の fake 実装。 */
    private class FakePendingRestoreFileStore : PendingRestoreFileStore {
        override val pendingDir: File = File("pending-dir")
        override val rollbackDir: File = File(pendingDir, "rollback")
        override val quarantineRootDir: File = File(
            System.getProperty("java.io.tmpdir"),
            "pending-restore-quarantine-${System.nanoTime()}",
        )
        var marker: PendingRestoreMarker? = null
        var lastWrittenMarker: PendingRestoreMarker? = null
        val writtenMarkers = mutableListOf<PendingRestoreMarker>()
        val results = mutableListOf<PendingRestoreResultFile>()
        val events = mutableListOf<String>()
        var quarantineCreationFailure: IOException? = null
        var quarantineIncidentIsFile = false
        var cleanupFailure: IOException? = null
        var cleanupReturnsFalse = false
        var resultWriteFailure: IOException? = null
        var markerWriteFailure: IOException? = null

        override fun createQuarantineIncidentDir(): File {
            quarantineCreationFailure?.let { throw it }
            val incidentDir = File(quarantineRootDir, "incident-${System.nanoTime()}")
            quarantineRootDir.mkdirs()
            if (quarantineIncidentIsFile) {
                incidentDir.writeText("not a directory")
            } else {
                incidentDir.mkdirs()
            }
            return incidentDir
        }

        override fun readMarker(): PendingRestoreMarker? = marker

        override fun writeMarker(marker: PendingRestoreMarker) {
            markerWriteFailure?.let { throw it }
            lastWrittenMarker = marker
            this.marker = marker
            writtenMarkers += marker
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
            resultWriteFailure?.let { throw it }
            results += PendingRestoreResultFile(
                success = success,
                message = message,
                timestamp = timestamp,
                backupDatabaseVersion = backupDatabaseVersion,
                currentDatabaseVersion = currentDatabaseVersion,
                migrationRequired = migrationRequired,
                migrationCompleted = migrationCompleted,
                previousStatus = previousStatus,
                rollbackRequiredAt = rollbackRequiredAt,
                finalFailureReason = finalFailureReason,
            )
            events += "writeResult:$success:$message"
        }

        override fun cleanupPending(): Boolean {
            events += "cleanupPending"
            cleanupFailure?.let { throw it }
            if (cleanupReturnsFalse) return false
            marker = null
            return true
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
        var prepareSnapshotCalls = 0
        var rollbackSnapshotCalls = 0
        var prepareSnapshotResult: String? = null
        var rollbackSnapshotResult: String? = null
        var throwOnReflect: Exception? = null
        var result: String? = null

        override suspend fun prepareRollbackSnapshot(
            pendingDir: File,
            includeCookies: Boolean,
        ): String? {
            prepareSnapshotCalls++
            return prepareSnapshotResult
        }

        override suspend fun reflect(pendingDir: File, includeCookies: Boolean): String? {
            calls += pendingDir to includeCookies
            throwOnReflect?.let { throw it }
            return result
        }

        override suspend fun restoreRollbackSnapshot(pendingDir: File): String? {
            rollbackSnapshotCalls++
            return rollbackSnapshotResult
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
