package com.websarva.wings.android.slevo.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room DB の一貫性ある snapshot を一時ファイルへ出力する exporter。
 */
@Singleton
class DatabaseBackupExporter @Inject constructor(
    private val gate: DatabaseWriteGate,
    private val dbConn: DatabaseConnection,
    private val dbPathResolver: DatabasePathResolver,
) {
    internal var maxRetries: Int = MAX_CHECKPOINT_RETRIES
    internal var retryDelayMs: Long = CHECKPOINT_RETRY_DELAY_MS
    internal var sqliteOpsProvider: SqliteOpsProvider = dbConn

    suspend fun exportDatabase(sessionDir: File): File {
        sessionDir.mkdirs()
        val tmpDbFile = File(sessionDir, TMP_DB_FILENAME)

        gate.withWritesSuspended {
            val ops = sqliteOpsProvider.create()
            val dbPath = dbPathResolver.getDatabasePath()

            val checkpointResult = performCheckpointWithRetry(ops)
            if (!checkpointResult.isComplete) {
                throw DatabaseBackupException("WAL checkpoint incomplete after $maxRetries retries")
            }

            var committed = false
            try {
                ops.beginImmediate()
                try {
                    copyFile(dbPath, tmpDbFile)
                    ops.commit()
                    committed = true
                } catch (e: Exception) {
                    tryOps(ops::rollback)
                    throw e
                }
            } finally {
                if (!committed) tryOps(ops::rollback)
            }
        }

        verifyIntegrity(tmpDbFile)
        return tmpDbFile
    }

    private suspend fun performCheckpointWithRetry(ops: SqliteOps): CheckpointResult {
        var lastResult: CheckpointResult? = null
        for (attempt in 1..maxRetries) {
            val result = ops.checkpoint()
            lastResult = result
            if (result.isComplete) return result
            if (attempt < maxRetries) delay(retryDelayMs)
        }
        return lastResult ?: ops.checkpoint()
    }

    private fun verifyIntegrity(dbFile: File) {
        val tmpDb = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val cursor = tmpDb.rawQuery("PRAGMA integrity_check", null)
            val result = cursor.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            if (result != "ok") throw DatabaseBackupException("integrity check failed: $result")
        } finally { tmpDb.close() }
    }

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                input.channel.use { inChannel ->
                    output.channel.use { outChannel ->
                        inChannel.transferTo(0, inChannel.size(), outChannel)
                    }
                }
            }
        }
    }

    /**
     * `PRAGMA wal_checkpoint(TRUNCATE)` の結果。
     * `busy == 0` かつ `log == checkpointed` で checkpoint 完了。
     */
    data class CheckpointResult(val busy: Int, val log: Int, val checkpointed: Int) {
        val isComplete: Boolean get() = busy == 0 && log == checkpointed
    }

    companion object {
        const val MAX_CHECKPOINT_RETRIES = 3
        const val CHECKPOINT_RETRY_DELAY_MS = 100L
        const val TMP_DB_FILENAME = "slevo.db"
    }
}

// --- Abstracted interfaces for testability ---

/** SQLite WAL checkpoint、トランザクション制御の抽象。 */
interface SqliteOps {
    /** `PRAGMA wal_checkpoint(TRUNCATE)` を実行し、busy/log/checkpointed を返す。 */
    fun checkpoint(): DatabaseBackupExporter.CheckpointResult
    fun beginImmediate()
    fun commit()
    fun rollback()
}

/** [SqliteOps] の生成インターフェース。 */
interface SqliteOpsProvider {
    fun create(): SqliteOps
}

/** データベース接続情報。テスト用 fake で置き換え可能。 */
interface DatabaseConnection : SqliteOpsProvider {
    val databaseName: String?
    val writableDatabase: SupportSQLiteDatabase
}

/** DB ファイルパス解決。テスト用 fake で置き換え可能。 */
interface DatabasePathResolver {
    fun getDatabasePath(): File
}

// --- Production implementations ---

/**
 * Room [AppDatabase] をラップした [DatabaseConnection] 実装。
 */
@Singleton
class AppDatabaseConnection @Inject constructor(
    private val db: AppDatabase,
) : DatabaseConnection {
    override val databaseName: String? get() = db.openHelper.databaseName
    override val writableDatabase: SupportSQLiteDatabase get() = db.openHelper.writableDatabase
    override fun create(): SqliteOps = RealSqliteOps(writableDatabase)
}

/**
 * [Context.getDatabasePath] を使う [DatabasePathResolver] 実装。
 */
class ContextDatabasePathResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    db: AppDatabase,
) : DatabasePathResolver {
    private val databaseName: String? = db.openHelper.databaseName
    override fun getDatabasePath(): File = databaseName?.let { context.getDatabasePath(it) }
        ?: throw IllegalStateException("databaseName is null")
}

/** 実 SupportSQLiteDatabase への操作。 */
class RealSqliteOps(private val db: SupportSQLiteDatabase) : SqliteOps {
    override fun checkpoint(): DatabaseBackupExporter.CheckpointResult {
        val cursor = db.query("PRAGMA wal_checkpoint(TRUNCATE)")
        return cursor.use { c ->
            if (c.moveToFirst()) DatabaseBackupExporter.CheckpointResult(c.getInt(0), c.getInt(1), c.getInt(2))
            else DatabaseBackupExporter.CheckpointResult(-1, 0, 0)
        }
    }
    override fun beginImmediate() { db.execSQL("BEGIN IMMEDIATE") }
    override fun commit() { db.execSQL("COMMIT") }
    override fun rollback() { db.execSQL("ROLLBACK") }
}

private inline fun tryOps(block: () -> Unit) {
    try { block() } catch (_: Exception) { }
}

/**
 * DB エクスポート中の失敗を表す例外。
 */
class DatabaseBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)
