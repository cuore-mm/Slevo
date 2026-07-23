package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import android.util.AtomicFile
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [RealPendingRestoreFileStore] の marker/result I/O と cleanup を検証する。
 */
@OptIn(ExperimentalStdlibApi::class)
@RunWith(RobolectricTestRunner::class)
class PendingRestoreFileStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val moshi = Moshi.Builder().build()
    private val resultAdapter = moshi.adapter<PendingRestoreResultFile>()

    @Test
    fun marker_roundTripsSuccessfully() {
        val store = createStore()
        val marker = PendingRestoreMarker(
            status = RestoreStatus.PREPARED,
            createdAt = "2026-07-03T00:00:00Z",
            includeCookies = true,
            databaseVersion = 9,
        )

        store.writeMarker(marker)

        assertEquals(marker, store.readMarker())
    }

    @Test
    fun marker_updateReadsOnlyTheNewlyCommittedMarker() {
        val store = createStore()
        val first = marker(RestoreStatus.PREPARED)
        val second = marker(RestoreStatus.APPLYING)

        store.writeMarker(first)
        store.writeMarker(second)

        assertEquals(second, store.readMarker())
    }

    @Test
    fun interruptedUpdate_recoversThePreviouslyCommittedMarker() {
        val store = createStore()
        val previous = marker(RestoreStatus.PREPARED)
        val markerFile = File(store.pendingDir, PendingRestoreManager.MARKER_FILENAME)

        store.writeMarker(previous)
        val interruptedWrite = AtomicFile(markerFile).startWrite()
        interruptedWrite.write("{\"status\":\"partial".toByteArray())
        interruptedWrite.close()

        assertEquals(previous, store.readMarker())
    }

    /** marker の pre-commit failure 注入時に既存の marker を保持する。 */
    @Test
    fun marker_startWriteFailure_preservesPreviouslyCommittedMarker() {
        val store = createStore()
        val previous = marker(RestoreStatus.PREPARED)
        val markerFile = File(store.pendingDir, PendingRestoreManager.MARKER_FILENAME)
        store.writeMarker(previous)

        val failingAtomicFile = spyk(AtomicFile(markerFile))
        every { failingAtomicFile.startWrite() } throws IOException("start write failed")
        val writer = AtomicPendingRestoreMarkerFile(
            markerFile = markerFile,
            adapter = moshi.adapter<PendingRestoreMarker>(),
            atomicFile = failingAtomicFile,
        )

        try {
            writer.write(marker(RestoreStatus.COMPLETED))
            throw AssertionError("marker write should fail before publication")
        } catch (_: IOException) {
            assertEquals(previous, store.readMarker())
        }
    }

    /** marker の atomic replace failure 注入時に最後の valid JSON を保持する。 */
    @Test
    fun marker_finishWriteFailure_preservesPreviouslyCommittedMarker() {
        val store = createStore()
        val previous = marker(RestoreStatus.PREPARED)
        val markerFile = File(store.pendingDir, PendingRestoreManager.MARKER_FILENAME)
        store.writeMarker(previous)

        val failingAtomicFile = spyk(AtomicFile(markerFile))
        every { failingAtomicFile.finishWrite(any()) } throws IOException("finish write failed")
        val writer = AtomicPendingRestoreMarkerFile(
            markerFile = markerFile,
            adapter = moshi.adapter<PendingRestoreMarker>(),
            atomicFile = failingAtomicFile,
        )

        try {
            writer.write(marker(RestoreStatus.COMPLETED))
            throw AssertionError("marker replace should fail")
        } catch (_: IOException) {
            assertEquals(previous, store.readMarker())
            assertNotEquals(RestoreStatus.COMPLETED, store.readMarker()?.status)
        }
    }

    @Test
    fun interruptedInitialWrite_doesNotExposePartialMarker() {
        val store = createStore()
        val markerFile = File(store.pendingDir, PendingRestoreManager.MARKER_FILENAME)

        val interruptedWrite = AtomicFile(markerFile).startWrite()
        interruptedWrite.write("{\"status\":\"partial".toByteArray())
        interruptedWrite.close()

        assertNull(store.readMarker())
    }

    @Test
    fun backupOnlyMarker_recoversWhenBaseFileIsMissing() {
        val store = createStore()
        val previous = marker(RestoreStatus.PREPARED)
        val markerFile = File(store.pendingDir, PendingRestoreManager.MARKER_FILENAME)

        store.writeMarker(previous)
        assertTrue(markerFile.delete())
        File("${markerFile.path}.bak").writeText(
            moshi.adapter<PendingRestoreMarker>().toJson(previous),
        )

        assertEquals(previous, store.readMarker())
    }

    @Test
    fun markerWriteFailure_isReportedBeforeWriting() {
        val store = createStore()
        store.pendingDir.parentFile?.mkdirs()
        store.pendingDir.writeText("not a directory")

        try {
            store.writeMarker(marker(RestoreStatus.PREPARED))
            assertTrue("a file cannot be used as marker parent", false)
        } catch (_: IllegalStateException) {
            assertTrue(true)
        }
    }

    @Test
    fun malformedMarker_returnsNull() {
        val store = createStore()
        File(store.pendingDir, PendingRestoreManager.MARKER_FILENAME).apply {
            parentFile?.mkdirs()
            writeText("{not-json")
        }

        assertNull(store.readMarker())
    }

    @Test
    fun writeResult_writesSuccessAndFailureFiles() {
        val store = createStore()

        store.writeResult(true, "ok", "2026-07-03T00:00:00Z")
        store.writeResult(false, "ng", "2026-07-03T00:00:01Z")

        val resultFile = File(
            tempFolder.root,
            "files/${PendingRestoreManager.RESULT_DIR_NAME}/${PendingRestoreManager.RESULT_FILENAME}",
        )
        val result = resultAdapter.fromJson(resultFile.readText())
        assertNotNull(result)
        assertEquals(false, result!!.success)
        assertEquals("ng", result.message)
    }

    /** result write が中断しても reader は直前の完全な JSON を読む。 */
    @Test
    fun interruptedResultUpdate_recoversPreviouslyCommittedResult() {
        val store = createStore()
        val resultFile = File(
            tempFolder.root,
            "files/${PendingRestoreManager.RESULT_DIR_NAME}/${PendingRestoreManager.RESULT_FILENAME}",
        )
        val previous = PendingRestoreResultFile(
            success = true,
            message = "previous",
            timestamp = "2026-07-03T00:00:00Z",
            migrationCompleted = true,
        )
        store.writeResult(
            success = previous.success,
            message = previous.message,
            timestamp = previous.timestamp,
            migrationCompleted = previous.migrationCompleted,
        )

        val interruptedWrite = AtomicFile(resultFile).startWrite()
        interruptedWrite.write("{\"success\":true,\"message\":\"partial".toByteArray())
        interruptedWrite.close()

        val reader = AtomicPendingRestoreResultFile(resultFile, resultAdapter)
        assertEquals(previous, resultAdapter.fromJson(reader.readRaw()!!))
    }

    /** result の pre-commit failure 注入時に既存の result を保持する。 */
    @Test
    fun result_startWriteFailure_preservesPreviouslyCommittedResult() {
        val store = createStore()
        val previous = PendingRestoreResultFile(
            success = true,
            message = "previous",
            timestamp = "2026-07-03T00:00:00Z",
            migrationCompleted = true,
        )
        store.writeResult(
            success = previous.success,
            message = previous.message,
            timestamp = previous.timestamp,
            migrationCompleted = previous.migrationCompleted,
        )
        val resultFile = File(
            tempFolder.root,
            "files/${PendingRestoreManager.RESULT_DIR_NAME}/${PendingRestoreManager.RESULT_FILENAME}",
        )

        val failingAtomicFile = spyk(AtomicFile(resultFile))
        every { failingAtomicFile.startWrite() } throws IOException("start write failed")
        val writer = AtomicPendingRestoreResultFile(resultFile, resultAdapter, failingAtomicFile)

        try {
            writer.write(previous.copy(message = "replacement"))
            throw AssertionError("result write should fail before publication")
        } catch (_: IOException) {
            assertEquals(previous, resultAdapter.fromJson(writer.readRaw()!!))
        }
    }

    /** result の atomic replace failure 注入時に最後の valid JSON を保持する。 */
    @Test
    fun result_finishWriteFailure_preservesPreviouslyCommittedResult() {
        val store = createStore()
        val previous = PendingRestoreResultFile(
            success = true,
            message = "previous",
            timestamp = "2026-07-03T00:00:00Z",
            migrationCompleted = true,
        )
        store.writeResult(
            success = previous.success,
            message = previous.message,
            timestamp = previous.timestamp,
            migrationCompleted = previous.migrationCompleted,
        )
        val resultFile = File(
            tempFolder.root,
            "files/${PendingRestoreManager.RESULT_DIR_NAME}/${PendingRestoreManager.RESULT_FILENAME}",
        )

        val failingAtomicFile = spyk(AtomicFile(resultFile))
        every { failingAtomicFile.finishWrite(any()) } throws IOException("finish write failed")
        val writer = AtomicPendingRestoreResultFile(resultFile, resultAdapter, failingAtomicFile)

        try {
            writer.write(previous.copy(message = "replacement"))
            throw AssertionError("result replace should fail")
        } catch (_: IOException) {
            val restored = resultAdapter.fromJson(writer.readRaw()!!)
            assertEquals(previous, restored)
            assertNotEquals("replacement", restored?.message)
        }
    }

    @Test
    fun cleanupPending_removesPendingDirectory() {
        val store = createStore()
        File(store.pendingDir, "database/slevo.db").apply {
            parentFile?.mkdirs()
            writeText("db")
        }

        store.cleanupPending()

        assertFalse(store.pendingDir.exists())
    }

    @Test
    fun cleanupPending_removesAtomicMarkerBackup() {
        val store = createStore()
        store.writeMarker(marker(RestoreStatus.PREPARED))
        val markerFile = File(store.pendingDir, PendingRestoreManager.MARKER_FILENAME)
        val interruptedWrite = AtomicFile(markerFile).startWrite()
        interruptedWrite.write("{partial".toByteArray())
        interruptedWrite.close()
        File("${markerFile.path}.bak").writeText("legacy-backup")

        store.cleanupPending()

        assertFalse(store.pendingDir.exists())
        assertFalse(File("${markerFile.path}.bak").exists())
    }

    @Test
    fun quarantineIncident_isOutsidePendingDirectory() {
        val store = createStore()

        val incidentDir = store.createQuarantineIncidentDir()

        assertEquals(
            tempFolder.root.resolve("files").canonicalFile,
            store.quarantineRootDir.parentFile?.canonicalFile,
        )
        assertTrue(
            "quarantine must not be a pending directory descendant",
            !incidentDir.canonicalPath.startsWith("${store.pendingDir.canonicalPath}${File.separator}"),
        )
    }

    @Test
    fun quarantineIncident_createsUniqueDirectoriesWithoutOverwriting() {
        val store = createStore()
        val first = store.createQuarantineIncidentDir()
        val firstDb = File(first, "slevo.db").apply { writeText("first") }

        val second = store.createQuarantineIncidentDir()
        val secondDb = File(second, "slevo.db").apply { writeText("second") }

        assertNotEquals(first.canonicalFile, second.canonicalFile)
        assertTrue(firstDb.exists())
        assertTrue(secondDb.exists())
        assertEquals("first", firstDb.readText())
        assertEquals("second", secondDb.readText())
    }

    @Test
    fun cleanupPending_preservesQuarantineIncident() {
        val store = createStore()
        val incident = store.createQuarantineIncidentDir()
        val database = File(incident, "slevo.db").apply { writeText("database") }
        val wal = File(incident, "slevo.db-wal").apply { writeText("wal") }
        val shm = File(incident, "slevo.db-shm").apply { writeText("shm") }
        File(store.pendingDir, "database/slevo.db").apply {
            parentFile?.mkdirs()
            writeText("staged")
        }

        store.cleanupPending()

        assertFalse(store.pendingDir.exists())
        assertTrue(database.exists())
        assertTrue(wal.exists())
        assertTrue(shm.exists())
        assertEquals("database", database.readText())
    }

    /** pending payload だけを削除し、success result は UI consumer 用に保持する。 */
    @Test
    fun cleanupPending_removesOwnedPayloadsAndPreservesResult() {
        val store = createStore()
        store.writeMarker(marker(RestoreStatus.COMPLETED))
        store.writeResult(
            success = true,
            message = "ok",
            timestamp = "2026-07-03T00:00:00Z",
            migrationCompleted = true,
        )
        listOf(
            File(store.pendingDir, "database/slevo.db"),
            File(store.pendingDir, "datastore/settings.json"),
            File(store.rollbackDir, "slevo.db"),
            File(store.pendingDir, PendingRestoreManager.DATASTORE_ROLLBACK_SNAPSHOT_FILENAME),
        ).forEach { file ->
            file.parentFile?.mkdirs()
            file.writeText("owned")
        }

        assertTrue(store.cleanupPending())
        assertFalse(store.pendingDir.exists())
        assertTrue(
            File(
                tempFolder.root,
                "files/${PendingRestoreManager.RESULT_DIR_NAME}/${PendingRestoreManager.RESULT_FILENAME}",
            ).exists(),
        )
    }

    @Test
    fun quarantineIncident_failsWhenRootCannotBeCreated() {
        val store = createStore()
        store.quarantineRootDir.parentFile?.mkdirs()
        store.quarantineRootDir.writeText("not a directory")

        try {
            store.createQuarantineIncidentDir()
            assertTrue("a file cannot be used as quarantine root", false)
        } catch (_: IOException) {
            assertTrue(true)
        }
    }

    @Test
    fun cleanupResult_removesResultDirectory() {
        val store = createStore()
        store.writeResult(true, "ok", "2026-07-03T00:00:00Z")

        store.cleanupResult()

        val resultDir = File(tempFolder.root, "files/${PendingRestoreManager.RESULT_DIR_NAME}")
        assertFalse(resultDir.exists())
    }

    private fun createStore(): RealPendingRestoreFileStore {
        val filesDir = tempFolder.newFolder("files")
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
        return RealPendingRestoreFileStore(context, moshi)
    }

    private fun marker(status: RestoreStatus): PendingRestoreMarker = PendingRestoreMarker(
        status = status,
        createdAt = "2026-07-03T00:00:00Z",
        includeCookies = true,
        databaseVersion = 9,
    )
}
