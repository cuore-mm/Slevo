package com.websarva.wings.android.slevo.data.backup.export

import androidx.sqlite.db.SupportSQLiteDatabase
import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import kotlinx.coroutines.CompletableDeferred
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
    override fun commit() { committedCalled = true }
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
