package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import android.content.pm.ApplicationInfo
import com.websarva.wings.android.slevo.data.backup.BackupMoshiFactory
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [RealPendingRestoreDbSwapper] の DB file 操作を検証する。
 */
class PendingRestoreDbSwapperTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dbDir: File
    private lateinit var swapper: RealPendingRestoreDbSwapper

    @Before
    fun setUp() {
        dbDir = tempFolder.newFolder("databases")
        val context = mockk<Context>(relaxed = true)
        val appInfo = ApplicationInfo().apply { flags = 0 }
        every { context.applicationContext } returns context
        every { context.applicationInfo } returns appInfo
        every { context.packageName } returns "com.example.slevo"
        every { context.getDatabasePath(any()) } answers { File(dbDir, firstArg<String>()) }
        swapper = RealPendingRestoreDbSwapper(context)
    }

    // --- Rollback backup creation ---

    @Test
    fun createRollbackBackup_mainDbOnly_createsManifestAndHasBackup() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        val rollbackDir = tempFolder.newFolder("rollback")

        val error = swapper.createRollbackBackup(liveDb, rollbackDir)

        assertNull(error)
        assertTrue(manifestFile(rollbackDir).exists())
        assertEquals("live", mainFile(rollbackDir, liveDb).readText())
        assertTrue(swapper.hasRollbackBackup(rollbackDir, liveDb))
    }

    @Test
    fun createRollbackBackup_emptyWal_skipsWalAndMarksWalNotIncluded() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        File(dbDir, liveDb.name + "-wal").apply { createNewFile() }
        val rollbackDir = tempFolder.newFolder("rollback")

        val error = swapper.createRollbackBackup(liveDb, rollbackDir)

        assertNull(error)
        assertFalse(walFile(rollbackDir, liveDb).exists())
        val manifest = readManifest(rollbackDir)
        assertFalse(manifest.walIncluded)
    }

    @Test
    fun createRollbackBackup_nonEmptyWal_copiesWalAndMarksWalIncluded() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        File(dbDir, liveDb.name + "-wal").writeText("wal")
        val rollbackDir = tempFolder.newFolder("rollback")

        val error = swapper.createRollbackBackup(liveDb, rollbackDir)

        assertNull(error)
        assertEquals("wal", walFile(rollbackDir, liveDb).readText())
        val manifest = readManifest(rollbackDir)
        assertTrue(manifest.walIncluded)
    }

    @Test
    fun createRollbackBackup_doesNotCopyShm() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        File(dbDir, liveDb.name + "-wal").writeText("wal")
        File(dbDir, liveDb.name + "-shm").writeText("shm")
        val rollbackDir = tempFolder.newFolder("rollback")

        swapper.createRollbackBackup(liveDb, rollbackDir)

        assertFalse(File(rollbackDir, liveDb.name + "-shm").exists())
    }

    @Test
    fun createRollbackBackup_mainDbCopyFailure_returnsErrorAndNoManifest() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        val rollbackDir = tempFolder.newFolder("rollback")
        swapper.mainDbCopy = { _, _ -> throw RuntimeException("injected main copy failure") }

        val error = swapper.createRollbackBackup(liveDb, rollbackDir)

        assertTrue(requireNotNull(error).contains("main DB"))
        assertFalse(manifestFile(rollbackDir).exists())
    }

    @Test
    fun createRollbackBackup_nonEmptyWalCopyFailure_returnsErrorAndNoManifest() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        File(dbDir, liveDb.name + "-wal").writeText("wal")
        val rollbackDir = tempFolder.newFolder("rollback")
        swapper.walCopy = { _, _ -> throw RuntimeException("injected wal copy failure") }

        val error = swapper.createRollbackBackup(liveDb, rollbackDir)

        assertTrue(requireNotNull(error).contains("WAL"))
        assertFalse(manifestFile(rollbackDir).exists())
    }

    @Test
    fun createRollbackBackup_manifestPublishFailure_returnsErrorAndNoManifest() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        val rollbackDir = tempFolder.newFolder("rollback")
        swapper.manifestPublisher = { _, _ -> throw RuntimeException("injected publish failure") }

        val error = swapper.createRollbackBackup(liveDb, rollbackDir)

        assertTrue(requireNotNull(error).contains("publish"))
        assertFalse(manifestFile(rollbackDir).exists())
    }

    @Test
    fun createRollbackBackup_failsWhenDirectoryCannotBeCreated() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        val rollbackDir = tempFolder.newFile("rollback-file")

        val error = swapper.createRollbackBackup(liveDb, rollbackDir)

        assertTrue(requireNotNull(error).isNotBlank())
    }

    // --- Snapshot completeness validation ---

    @Test
    fun hasRollbackBackup_partialSnapshotWithoutManifest_returnsFalse() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        val rollbackDir = tempFolder.newFolder("rollback")
        mainFile(rollbackDir, liveDb).writeText("live")

        assertFalse(swapper.hasRollbackBackup(rollbackDir, liveDb))
    }

    @Test
    fun hasRollbackBackup_manifestClaimsWalButWalMissing_returnsFalse() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        val rollbackDir = tempFolder.newFolder("rollback")
        mainFile(rollbackDir, liveDb).writeText("live")
        writeManifest(rollbackDir, liveDb, walIncluded = true)

        assertFalse(swapper.hasRollbackBackup(rollbackDir, liveDb))
    }

    @Test
    fun hasRollbackBackup_invalidManifestJson_returnsFalse() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        val rollbackDir = tempFolder.newFolder("rollback")
        mainFile(rollbackDir, liveDb).writeText("live")
        manifestFile(rollbackDir).writeText("{invalid")

        assertFalse(swapper.hasRollbackBackup(rollbackDir, liveDb))
    }

    @Test
    fun hasRollbackBackup_unsupportedManifestVersion_returnsFalse() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        val rollbackDir = tempFolder.newFolder("rollback")
        mainFile(rollbackDir, liveDb).writeText("live")
        writeManifest(rollbackDir, liveDb, walIncluded = false, formatVersion = 999)

        assertFalse(swapper.hasRollbackBackup(rollbackDir, liveDb))
    }

    // --- Replace ---

    @Test
    fun replaceDbFile_replacesLiveDbAndDeletesWal() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("old") }
        File(dbDir, liveDb.name + "-wal").writeText("wal")
        val stagedDb = tempFolder.newFile("staged.db").apply { writeText("new") }

        val error = swapper.replaceDbFile(stagedDb, liveDb)

        assertNull(error)
        assertEquals("new", liveDb.readText())
        assertFalse(File(dbDir, liveDb.name + "-wal").exists())
    }

    @Test
    fun replaceDbFile_returnsErrorWhenStagedMissing() {
        val liveDb = swapper.getLiveDbFile()
        val error = swapper.replaceDbFile(File(tempFolder.root, "missing.db"), liveDb)
        assertEquals("staged DB file not found", error)
    }

    // --- Restore ---

    @Test
    fun restoreRollbackBackup_mainOnly_restoresMainDb() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("broken") }
        val rollbackDir = tempFolder.newFolder("rollback-restore")
        mainFile(rollbackDir, liveDb).writeText("restored")
        writeManifest(rollbackDir, liveDb, walIncluded = false)

        val restored = swapper.restoreRollbackBackup(liveDb, rollbackDir)

        assertTrue(restored)
        assertEquals("restored", liveDb.readText())
    }

    @Test
    fun restoreRollbackBackup_mainAndWal_restoresBoth() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("broken") }
        val rollbackDir = tempFolder.newFolder("rollback-restore")
        mainFile(rollbackDir, liveDb).writeText("restored")
        walFile(rollbackDir, liveDb).writeText("wal")
        writeManifest(rollbackDir, liveDb, walIncluded = true)

        val restored = swapper.restoreRollbackBackup(liveDb, rollbackDir)

        assertTrue(restored)
        assertEquals("restored", liveDb.readText())
        assertEquals("wal", walFile(dbDir, liveDb).readText())
    }

    @Test
    fun restoreRollbackBackup_requiredWalMissing_returnsFalse() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("broken") }
        val rollbackDir = tempFolder.newFolder("rollback-restore")
        mainFile(rollbackDir, liveDb).writeText("restored")
        writeManifest(rollbackDir, liveDb, walIncluded = true)

        val restored = swapper.restoreRollbackBackup(liveDb, rollbackDir)

        assertFalse(restored)
    }

    @Test
    fun restoreRollbackBackup_invalidManifest_returnsFalseWithoutTouchingLiveDb() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("broken") }
        val rollbackDir = tempFolder.newFolder("rollback-restore")
        mainFile(rollbackDir, liveDb).writeText("restored")
        manifestFile(rollbackDir).writeText("{invalid")

        val restored = swapper.restoreRollbackBackup(liveDb, rollbackDir)

        assertFalse(restored)
        assertEquals("broken", liveDb.readText())
    }

    @Test
    fun restoreRollbackBackup_doesNotRestoreShm() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("broken") }
        val rollbackDir = tempFolder.newFolder("rollback-restore")
        mainFile(rollbackDir, liveDb).writeText("restored")
        File(rollbackDir, liveDb.name + "-shm").writeText("shm")
        writeManifest(rollbackDir, liveDb, walIncluded = false)

        val restored = swapper.restoreRollbackBackup(liveDb, rollbackDir)

        assertTrue(restored)
        assertFalse(File(dbDir, liveDb.name + "-shm").exists())
    }

    @Test
    fun restoreRollbackBackup_mainRestoreFailure_returnsFalse() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("broken") }
        val rollbackDir = tempFolder.newFolder("rollback-restore")
        mainFile(rollbackDir, liveDb).apply { writeText("restored") }
        writeManifest(rollbackDir, liveDb, walIncluded = false)
        swapper.mainDbRestore = { _, _ -> throw RuntimeException("injected main restore failure") }

        val restored = swapper.restoreRollbackBackup(liveDb, rollbackDir)

        assertFalse(restored)
    }

    @Test
    fun restoreRollbackBackup_walRestoreFailure_returnsFalse() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("broken") }
        val rollbackDir = tempFolder.newFolder("rollback-restore")
        mainFile(rollbackDir, liveDb).writeText("restored")
        walFile(rollbackDir, liveDb).writeText("wal")
        writeManifest(rollbackDir, liveDb, walIncluded = true)
        swapper.walRestore = { _, _ -> throw RuntimeException("injected wal restore failure") }

        val restored = swapper.restoreRollbackBackup(liveDb, rollbackDir)

        assertFalse(restored)
    }

    // --- Cleanup ---

    @Test
    fun cleanupCorruptFreshInstallDb_removesMainDbAndWal() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("broken") }
        File(dbDir, liveDb.name + "-wal").writeText("wal")

        swapper.cleanupCorruptFreshInstallDb(liveDb)

        assertFalse(liveDb.exists())
        assertFalse(File(dbDir, liveDb.name + "-wal").exists())
    }

    // --- Helpers ---

    private fun mainFile(rollbackDir: File, liveDb: File): File =
        File(rollbackDir, liveDb.name)

    private fun walFile(parentDir: File, liveDb: File): File =
        File(parentDir, liveDb.name + "-wal")

    private fun manifestFile(rollbackDir: File): File =
        File(rollbackDir, RollbackSnapshotManifest.ROLLBACK_READY_FILENAME)

    private fun readManifest(rollbackDir: File): RollbackSnapshotManifest {
        val adapter = BackupMoshiFactory.create().adapter(RollbackSnapshotManifest::class.java)
        return requireNotNull(adapter.fromJson(manifestFile(rollbackDir).readText()))
    }

    private fun writeManifest(
        rollbackDir: File,
        liveDb: File,
        walIncluded: Boolean,
        formatVersion: Int = RollbackSnapshotManifest.CURRENT_FORMAT_VERSION,
    ) {
        val manifest = RollbackSnapshotManifest(
            formatVersion = formatVersion,
            mainDbFileName = liveDb.name,
            walIncluded = walIncluded,
        )
        val adapter = BackupMoshiFactory.create().adapter(RollbackSnapshotManifest::class.java)
        manifestFile(rollbackDir).writeText(adapter.toJson(manifest))
    }
}
