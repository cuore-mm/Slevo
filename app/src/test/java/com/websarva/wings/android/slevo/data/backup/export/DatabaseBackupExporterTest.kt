package com.websarva.wings.android.slevo.data.backup.export

import androidx.sqlite.db.SupportSQLiteDatabase
import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel

/**
 * [DatabaseBackupExporter] の fake/抽象化テスト。
 *
 * 実 SQLite に依存しない checkpoint retry、transaction flow、
 * gate 呼び出し、cleanup を fake で検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseBackupExporterTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private fun newExporter(
        ops: SqliteOps,
        gate: DatabaseWriteGate,
        dbPath: File,
    ): DatabaseBackupExporter {
        val exporter = DatabaseBackupExporter(
            gate = gate,
            dbConn = FakeDatabaseConnection(ops),
            dbPathResolver = FakeDatabasePathResolver(dbPath),
        )
        exporter.maxRetries = 3
        exporter.retryDelayMs = 0L
        return exporter
    }

    // --- 2.7: checkpoint retry ---

    @Test
    fun checkpointRetry_succeedsOnFirstAttempt() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val srcDb = tmpDir.newFile("slevo.db")
        srcDb.writeText("fake db content")
        val ops = FakeSqliteOps(checkpointResults = listOf(
            DatabaseBackupExporter.CheckpointResult(busy = 0, log = 5, checkpointed = 5)
        ))
        val exporter = newExporter(ops, gate, srcDb)
        val session = tmpDir.newFolder("session")

        try { exporter.exportDatabase(session) } catch (_: Exception) { }

        assertEquals(1, ops.checkpointCallCount)
        assertTrue(ops.committedCalled)
        assertFalse(ops.rolledBackCalled)
    }

    @Test
    fun checkpointRetry_retriesWhenIncomplete() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val srcDb = tmpDir.newFile("slevo.db")
        srcDb.writeText("fake db content")
        val ops = FakeSqliteOps(checkpointResults = listOf(
            DatabaseBackupExporter.CheckpointResult(busy = 1, log = 5, checkpointed = 3),
            DatabaseBackupExporter.CheckpointResult(busy = 0, log = 5, checkpointed = 5),
        ))
        val exporter = newExporter(ops, gate, srcDb)
        val session = tmpDir.newFolder("session")

        try { exporter.exportDatabase(session) } catch (_: Exception) { }

        assertEquals(2, ops.checkpointCallCount)
        assertTrue(ops.committedCalled)
    }

    @Test
    fun checkpointRetry_failsAfterMaxRetries() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val srcDb = tmpDir.newFile("slevo.db")
        srcDb.writeText("fake db content")
        val ops = FakeSqliteOps(checkpointResults = listOf(
            DatabaseBackupExporter.CheckpointResult(busy = 1, log = 5, checkpointed = 3),
            DatabaseBackupExporter.CheckpointResult(busy = 0, log = 5, checkpointed = 3),
            DatabaseBackupExporter.CheckpointResult(busy = 1, log = 5, checkpointed = 5),
        ))
        val exporter = newExporter(ops, gate, srcDb)
        val session = tmpDir.newFolder("session")

        try {
            exporter.exportDatabase(session)
            fail("expected DatabaseBackupException")
        } catch (e: DatabaseBackupException) {
            assertTrue(e.message!!.contains("checkpoint incomplete"))
        }

        assertEquals(3, ops.checkpointCallCount)
        assertFalse(ops.committedCalled)
        // checkpoint 失敗時はトランザクション未開始のため rollback 不要。
        assertFalse(ops.rolledBackCalled)
    }

    // --- 2.9: gate 呼び出し検証 ---

    @Test
    fun gate_withWritesSuspended_isCalled() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val srcDb = tmpDir.newFile("slevo.db")
        srcDb.writeText("fake db content")
        val ops = FakeSqliteOps(checkpointResults = listOf(
            DatabaseBackupExporter.CheckpointResult(busy = 0, log = 0, checkpointed = 0)
        ))
        val exporter = newExporter(ops, gate, srcDb)
        val session = tmpDir.newFolder("session")

        // export 実行（integrity check で失敗するが gate は復旧する）。
        try { exporter.exportDatabase(session) } catch (_: Exception) { }

        // gate 復旧後、後続 writer が走れる。
        val after = CompletableDeferred<Unit>()
        val job = launch {
            gate.withWritePermit { after.complete(Unit) }
        }
        advanceUntilIdle()
        job.join()
        assertTrue("gate should be released after export", after.isCompleted)
    }

    // --- 2.10: cleanup on failure ---

    @Test
    fun rollback_onCopyFailure() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        // 存在しないディレクトリを srcDb にしてコピー失敗を起こす。
        val srcDb = File("/nonexistent/slevo.db")
        val ops = FakeSqliteOps(checkpointResults = listOf(
            DatabaseBackupExporter.CheckpointResult(busy = 0, log = 0, checkpointed = 0)
        ))
        val exporter = newExporter(ops, gate, srcDb)
        val session = tmpDir.newFolder("session")

        try {
            exporter.exportDatabase(session)
            fail("expected exception")
        } catch (_: Exception) { }

        assertFalse(ops.committedCalled)
        assertTrue(ops.rolledBackCalled)
    }

    @Test
    fun gate_releasedAfterException() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val srcDb = tmpDir.newFile("slevo.db")
        srcDb.writeText("fake db content")
        val ops = FakeSqliteOps(checkpointResults = listOf(
            DatabaseBackupExporter.CheckpointResult(busy = 0, log = 0, checkpointed = 0),
        ))
        val exporter = newExporter(ops, gate, srcDb)
        val session = tmpDir.newFolder("session")
        try { exporter.exportDatabase(session) } catch (_: Exception) { }

        // gate が復旧していることを確認。
        val after = CompletableDeferred<Unit>()
        val job = launch {
            gate.withWritePermit { after.complete(Unit) }
        }
        advanceUntilIdle()
        job.join()
        assertTrue("gate should be recovered", after.isCompleted)
    }

    // --- 疑似 cancellation テスト ---

    @Test
    fun rollback_onCheckpointException() = runTest(UnconfinedTestDispatcher()) {
        val gate = DatabaseWriteGate()
        val srcDb = tmpDir.newFile("slevo.db")
        srcDb.writeText("fake db content")
        val ops = ThrowingCheckpointOps()
        val exporter = newExporter(ops, gate, srcDb)
        val session = tmpDir.newFolder("session")

        try {
            exporter.exportDatabase(session)
            fail("expected exception")
        } catch (_: RuntimeException) { }

        // checkpoint が例外を投げた場合、トランザクションは未開始なので rollback は不要。
        // gate は解放されているべき。
        assertFalse(ops.committedCalled)
        // rollback は呼ばれなくてよい（BEGIN IMMEDIATE 前に失敗しているため）。
        val after = CompletableDeferred<Unit>()
        val job = launch {
            gate.withWritePermit { after.complete(Unit) }
        }
        advanceUntilIdle()
        job.join()
        assertTrue("gate should be released", after.isCompleted)
    }

    // --- 8.3: exact short-transfer completion ---

    @Test
    fun copyFile_repeatsShortTransfersWithExactPositionsAndCommitsAfterCopy() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = DatabaseWriteGate()
            val sourceBytes = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
            val srcDb = tmpDir.newFile("short-transfer.db").apply { writeBytes(sourceBytes) }
            val session = tmpDir.newFolder("short-transfer-session")
            val destination = File(session, DatabaseBackupExporter.TMP_DB_FILENAME)
            val positions = mutableListOf<Long>()
            val counts = mutableListOf<Long>()
            val transferSizes = mutableListOf(2L, 3L, sourceBytes.size.toLong() - 5L)
            val ops = FakeSqliteOps(
                checkpointResults = listOf(
                    DatabaseBackupExporter.CheckpointResult(0, 0, 0),
                ),
                onCommit = {
                    assertTrue(destination.exists())
                    assertEquals(sourceBytes.toList(), destination.readBytes().toList())
                },
            )
            val exporter = newExporter(ops, gate, srcDb)
            exporter.transferTo = { source, position, count, target ->
                positions += position
                counts += count
                val requested = transferSizes.removeAt(0)
                source.transferTo(position, requested, target)
            }

            var integrityFailure: Exception? = null
            try {
                exporter.exportDatabase(session)
            } catch (e: Exception) {
                // Fake bytes are not a real SQLite database; copy/commit assertions still run first.
                integrityFailure = e
            }

            assertEquals(listOf(0L, 2L, 5L), positions)
            assertEquals(listOf(8L, 6L, 3L), counts)
            assertEquals(sourceBytes.toList(), destination.readBytes().toList())
            assertTrue(ops.committedCalled)
            assertTrue("integrity check should reject fake DB bytes", integrityFailure != null)
        }

    // --- 8.4: zero progress and exception propagation ---

    @Test
    fun copyFile_zeroProgressFailsImmediatelyWithoutIntegrityCheckOrCommit() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = DatabaseWriteGate()
            val srcDb = tmpDir.newFile("zero-progress.db").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val session = tmpDir.newFolder("zero-progress-session")
            val positions = mutableListOf<Long>()
            val destination = File(session, DatabaseBackupExporter.TMP_DB_FILENAME)
            val ops = FakeSqliteOps(
                checkpointResults = listOf(DatabaseBackupExporter.CheckpointResult(0, 0, 0)),
            )
            val exporter = newExporter(ops, gate, srcDb)
            exporter.transferTo = { source, position, count, target ->
                positions += position
                if (positions.size == 1) source.transferTo(position, 1L, target) else 0L
            }

            try {
                exporter.exportDatabase(session)
                fail("expected IOException")
            } catch (e: IOException) {
                assertTrue(e.message!!.contains("no progress"))
            }

            assertEquals(listOf(0L, 1L), positions)
            assertFalse(ops.committedCalled)
            assertTrue(ops.rolledBackCalled)
            assertTrue(destination.exists())
            assertGateReleased(gate)
        }

    @Test
    fun copyFile_preservesIOExceptionAndClosesChannelsBeforeRethrowing() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = DatabaseWriteGate()
            val srcDb = tmpDir.newFile("io-failure.db").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val session = tmpDir.newFolder("io-failure-session")
            val ops = FakeSqliteOps(
                checkpointResults = listOf(DatabaseBackupExporter.CheckpointResult(0, 0, 0)),
            )
            val exporter = newExporter(ops, gate, srcDb)
            val expected = IOException("transfer failed")
            var sourceChannel: FileChannel? = null
            var targetChannel: FileChannel? = null
            exporter.transferTo = { source, _, _, target ->
                sourceChannel = source
                targetChannel = target
                throw expected
            }

            var thrown: IOException? = null
            try {
                exporter.exportDatabase(session)
                fail("expected IOException")
            } catch (e: IOException) {
                thrown = e
            }

            assertTrue(thrown === expected)
            assertFalse(ops.committedCalled)
            assertTrue(ops.rolledBackCalled)
            assertFalse(requireNotNull(sourceChannel).isOpen)
            assertFalse(requireNotNull(targetChannel).isOpen)
            assertGateReleased(gate)
        }

    @Test
    fun copyFile_preservesCancellationAndRollsBackBeforeGateRelease() =
        runTest(UnconfinedTestDispatcher()) {
            val gate = DatabaseWriteGate()
            val srcDb = tmpDir.newFile("cancelled-transfer.db").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val session = tmpDir.newFolder("cancelled-transfer-session")
            val ops = FakeSqliteOps(
                checkpointResults = listOf(DatabaseBackupExporter.CheckpointResult(0, 0, 0)),
            )
            val exporter = newExporter(ops, gate, srcDb)
            val expected = CancellationException("cancelled")
            exporter.transferTo = { _, _, _, _ -> throw expected }

            var thrown: CancellationException? = null
            try {
                exporter.exportDatabase(session)
                fail("expected CancellationException")
            } catch (e: CancellationException) {
                thrown = e
            }

            assertTrue(thrown === expected)
            assertFalse(ops.committedCalled)
            assertTrue(ops.rolledBackCalled)
            assertGateReleased(gate)
        }

    /** gate 解放後に通常 writer が実行できることを確認する。 */
    private suspend fun assertGateReleased(gate: DatabaseWriteGate) {
        val completed = CompletableDeferred<Unit>()
        gate.withWritePermit { completed.complete(Unit) }
        assertTrue("gate should be released", completed.isCompleted)
    }

    // --- CheckpointResult ---

    @Test
    fun checkpointResult_isComplete_onlyWhenBusyZeroAndLogEqualsCheckpointed() {
        assertTrue(DatabaseBackupExporter.CheckpointResult(0, 5, 5).isComplete)
        assertFalse(DatabaseBackupExporter.CheckpointResult(1, 5, 5).isComplete)
        assertFalse(DatabaseBackupExporter.CheckpointResult(0, 5, 3).isComplete)
    }

    @Test
    fun checkpointResult_handlesEmptyResults() {
        assertTrue(DatabaseBackupExporter.CheckpointResult(0, 0, 0).isComplete)
    }
}

// --- Fake SQLite Ops ---

internal class FakeSqliteOps(
    private val checkpointResults: List<DatabaseBackupExporter.CheckpointResult>,
    private val onCommit: () -> Unit = {},
) : SqliteOps {
    var checkpointCallCount = 0
    var committedCalled = false
    var rolledBackCalled = false
    var beginCalled = false

    override fun checkpoint(): DatabaseBackupExporter.CheckpointResult {
        val result = if (checkpointCallCount < checkpointResults.size) {
            checkpointResults[checkpointCallCount]
        } else {
            checkpointResults.last()
        }
        checkpointCallCount++
        return result
    }

    override fun beginImmediate() { beginCalled = true }
    override fun commit() {
        onCommit()
        committedCalled = true
    }
    override fun rollback() { rolledBackCalled = true }
}

internal class ThrowingCheckpointOps : SqliteOps {
    var committedCalled = false
    var rolledBackCalled = false

    override fun checkpoint(): DatabaseBackupExporter.CheckpointResult =
        throw RuntimeException("checkpoint failed")

    override fun beginImmediate() { }
    override fun commit() { committedCalled = true }
    override fun rollback() { rolledBackCalled = true }
}

internal class FakeDatabaseConnection(
    private val ops: SqliteOps,
) : DatabaseConnection {
    override val databaseName: String = "test_db"
    override val writableDatabase: SupportSQLiteDatabase
        get() = throw UnsupportedOperationException("not used in tests")

    override fun create(): SqliteOps = ops
}

internal class FakeDatabasePathResolver(
    private val path: File,
) : DatabasePathResolver {
    override fun getDatabasePath(): File = path
}
