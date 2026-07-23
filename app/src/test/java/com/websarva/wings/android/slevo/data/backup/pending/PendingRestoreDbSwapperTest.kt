package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import android.content.pm.ApplicationInfo
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun createRollbackBackup_copiesMainDbAndWal() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        File(dbDir, liveDb.name + "-wal").writeText("wal")
        val rollbackDir = tempFolder.newFolder("rollback")

        val error = swapper.createRollbackBackup(liveDb, rollbackDir)

        assertEquals(null, error)
        assertEquals("live", File(rollbackDir, liveDb.name).readText())
        assertEquals("wal", File(rollbackDir, liveDb.name + "-wal").readText())
    }

    @Test
    fun createRollbackBackup_failsWhenDirectoryCannotBeCreated() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("live") }
        val rollbackDir = tempFolder.newFile("rollback-file")

        val error = swapper.createRollbackBackup(liveDb, rollbackDir)

        assertTrue(requireNotNull(error).isNotBlank())
    }

    @Test
    fun replaceDbFile_replacesLiveDbAndDeletesWal() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("old") }
        File(dbDir, liveDb.name + "-wal").writeText("wal")
        val stagedDb = tempFolder.newFile("staged.db").apply { writeText("new") }

        val error = swapper.replaceDbFile(stagedDb, liveDb)

        assertEquals(null, error)
        assertEquals("new", liveDb.readText())
        assertFalse(File(dbDir, liveDb.name + "-wal").exists())
    }

    @Test
    fun replaceDbFile_returnsErrorWhenStagedMissing() {
        val liveDb = swapper.getLiveDbFile()
        val error = swapper.replaceDbFile(File(tempFolder.root, "missing.db"), liveDb)
        assertEquals("staged DB file not found", error)
    }

    @Test
    fun restoreRollbackBackup_restoresMainDbAndWal() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("broken") }
        val rollbackDir = tempFolder.newFolder("rollback-restore")
        File(rollbackDir, liveDb.name).writeText("restored")
        File(rollbackDir, liveDb.name + "-wal").writeText("wal")

        val restored = swapper.restoreRollbackBackup(liveDb, rollbackDir)

        assertTrue(restored)
        assertEquals("restored", liveDb.readText())
        assertEquals("wal", File(dbDir, liveDb.name + "-wal").readText())
    }

    @Test
    fun cleanupCorruptFreshInstallDb_removesMainDbAndWal() {
        val liveDb = swapper.getLiveDbFile().apply { writeText("broken") }
        File(dbDir, liveDb.name + "-wal").writeText("wal")

        swapper.cleanupCorruptFreshInstallDb(liveDb)

        assertFalse(liveDb.exists())
        assertFalse(File(dbDir, liveDb.name + "-wal").exists())
    }
}
