package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.squareup.moshi.Moshi
import com.websarva.wings.android.slevo.data.backup.restore.BackupDatabaseValidator
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room migration 完了後の post-migration validation と cleanup を担当する。
 *
 * [DatabaseCallback.onOpen()] から `Provider<PendingRestoreCompletionChecker>` 経由で
 * 非同期に起動される。Room が DB を open して migration を完了した後に呼ばれるため、
 * live DB file に対して strict validation ([BackupDatabaseValidator.validate]) を実行できる。
 *
 * 処理:
 * 1. marker が MIGRATION_PENDING か確認。なければ即 return（idempotent）。
 * 2. live DB の strict validation を実行。
 * 3. 成功 → COMPLETED marker を書き、success result と cleanup。
 * 4. 失敗 → ROLLBACK_REQUIRED result を書き、marker を ROLLBACK_REQUIRED へ atomic replace。
 *    書き込み失敗時は MIGRATION_PENDING marker と rollback backup を残し、live DB は変更しない。
 */
@Singleton
class PendingRestoreCompletionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val dbValidator: BackupDatabaseValidator,
) {
    private val fileStore: PendingRestoreFileStore
        get() = fileStoreOverride ?: _realFileStore
    private var fileStoreOverride: PendingRestoreFileStore? = null
    private val _realFileStore by lazy {
        RealPendingRestoreFileStore(context.applicationContext ?: context, moshi)
    }

    /** テスト用に liveDbFile を外部注入可能にする。null の場合は context から取得。 */
    internal var liveDbFileOverride: File? = null

    /**
     * marker が MIGRATION_PENDING の場合、post-migration validation と cleanup を実行する。
     *
     * このメソッドは idempotent。marker がない、または status が MIGRATION_PENDING でなければ
     * 即 return する。I/O を含むため [kotlinx.coroutines.Dispatchers.IO] 上で呼ぶこと。
     * cancellation は呼び出し元へ伝播し、それ以外の operational exception はログへ記録して
     * return する。失敗した操作より後の write/cleanup は実行しない。
     */
    fun runIfNeeded() {
        try {
            val marker = fileStore.readMarker() ?: return
            if (marker.status != RestoreStatus.MIGRATION_PENDING) return

            val liveDbFile = getLiveDbFile()
            val validationError = dbValidator.validate(liveDbFile)

            if (validationError == null) {
                onSuccess(marker)
            } else {
                onFailure(marker, validationError)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logOperationalException("completion checker", e)
        }
    }

    /** post-migration validation 成功時の処理。 */
    private fun onSuccess(marker: PendingRestoreMarker) {
        // --- COMPLETED marker を先に書く（durable）---
        val completedMarker = marker.copy(status = RestoreStatus.COMPLETED)
        fileStore.writeMarker(completedMarker)

        // success result と marker-last cleanup は applier の stale COMPLETED recovery と共有する。
        PendingRestoreCompletionFinalizer(
            fileStore = fileStore,
            nowProvider = { System.currentTimeMillis().toString() },
            currentDbVersion = AppDatabase.Companion.CURRENT_DATABASE_VERSION,
            logWarning = { message, error ->
                logOperationalException(
                    message,
                    error as? Exception ?: IllegalStateException(message),
                )
            },
        ).complete(completedMarker, "restore completed successfully (migration confirmed)")
    }

    /** post-migration validation 失敗時の処理。 */
    private fun onFailure(marker: PendingRestoreMarker, error: String) {
        // --- ROLLBACK_REQUIRED result を先に書く ---
        // result 書き込み失敗時は marker を変更しない（MIGRATION_PENDING のまま）。
        fileStore.writeResult(
            success = false,
            message = "post-migration validation failed: $error",
            timestamp = System.currentTimeMillis().toString(),
            backupDatabaseVersion = marker.databaseVersion,
            currentDatabaseVersion = AppDatabase.Companion.CURRENT_DATABASE_VERSION,
            migrationRequired = marker.databaseVersion < AppDatabase.Companion.CURRENT_DATABASE_VERSION,
            migrationCompleted = false,
        )

        // --- marker を ROLLBACK_REQUIRED へ atomic replace ---
        // marker が source of truth。replace 失敗時は MIGRATION_PENDING を残す。
        fileStore.writeMarker(marker.copy(status = RestoreStatus.ROLLBACK_REQUIRED))

        // live DB file は変更しない。Room が開いている可能性があるため。
        // rollback は次回 cold start の PendingRestoreApplier が行う。
    }

    /** live DB file のパスを取得する。 */
    private fun getLiveDbFile(): File {
        liveDbFileOverride?.let { return it }
        val ctx = context.applicationContext ?: context
        val dbName = if (ctx.packageName.contains(".debug") ||
            (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        ) {
            "slevo_dev_database"
        } else {
            "slevo_database"
        }
        return ctx.getDatabasePath(dbName)
    }

    /** テスト用に file store を差し替える。 */
    internal fun setFileStoreForTest(store: PendingRestoreFileStore) {
        fileStoreOverride = store
    }

    /** operational exception を機密情報を含めずログへ記録する。 */
    private fun logOperationalException(operation: String, error: Exception) {
        val exceptionType = error::class.java.simpleName
            .filter { it.isLetterOrDigit() || it == '_' }
            .ifBlank { "Exception" }
        try {
            Log.e(TAG, "$operation failed: $exceptionType")
        } catch (_: RuntimeException) {
            // JVM unit test の android.util.Log stub では例外になるため握りつぶす。
        }
    }

    private companion object {
        private const val TAG = "PendingRestoreCompletionChecker"
    }
}
