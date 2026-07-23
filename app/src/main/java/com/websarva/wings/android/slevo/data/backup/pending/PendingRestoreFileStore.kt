package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Result fileをprocess内で直列化するmonitor。
 *
 * 起動時applier、Room completion checker、UI consumerは別のstore instanceを使うため、全instanceが
 * 同じmonitorを共有してresultのwrite/read/conditional delete競合を防ぐ。
 */
internal object PendingRestoreResultFileLock {
    /** result file操作を保護するmonitor。 */
    val monitor = Any()
}

/**
 * marker の条件判定と atomic publication を同一 process 内で保護する monitor。
 *
 * marker を扱う全 store instance が共有し、recorder の read-check-write と通常の marker 操作を
 * 同じ排他境界へ置く。
 */
internal object PendingRestoreMarkerFileLock {
    /** marker file 操作を保護する monitor。 */
    val monitor = Any()
}

/**
 * marker の conditional mutation の結果と、公開する置換 marker をまとめる値。
 *
 * `replacement` が null の場合は marker を変更せず、現在の marker に対する判定結果だけを返す。
 */
internal data class PendingRestoreMarkerMutation<T>(
    val result: T,
    val replacement: PendingRestoreMarker? = null,
)

/**
 * pending restore のatomic marker/result file とcleanupを扱う抽象。
 *
 * [PendingRestoreApplier] から file I/O の詳細を分離し、
 * state machine orchestrationだけに集中できるようにする。markerのatomic publicationは
 * 本番実装の責務であり、result fileとquarantine artifactは別のlifecycleを持つ。
 */
internal interface PendingRestoreFileStore {
    /** pending restore directory。 */
    val pendingDir: File

    /** rollback backup directory。 */
    val rollbackDir: File

    /** pending cleanupから独立したquarantine artifactのroot directory。 */
    val quarantineRootDir: File

    /** 1回のquarantine failure専用のincident directoryを作成する。 */
    fun createQuarantineIncidentDir(): File

    /** marker を読み取る。parse 失敗時は null。 */
    fun readMarker(): PendingRestoreMarker?

    /** marker を上書き保存する。 */
    fun writeMarker(marker: PendingRestoreMarker)

    /**
     * 最新 marker を lock 内で読み取り、必要な場合だけ atomic replace する。
     *
     * callback は lock 内の最新値に対して一度だけ評価されるため、stale な copy を公開しない。
     */
    fun <T> mutateMarkerAtomically(
        mutation: (PendingRestoreMarker?) -> PendingRestoreMarkerMutation<T>,
    ): T {
        synchronized(PendingRestoreMarkerFileLock.monitor) {
            val decision = mutation(readMarker())
            decision.replacement?.let(::writeMarker)
            return decision.result
        }
    }

    /** result file を保存する。診断情報を含む。 */
    fun writeResult(
        success: Boolean,
        message: String,
        timestamp: String,
        backupDatabaseVersion: Int? = null,
        currentDatabaseVersion: Int? = null,
        migrationRequired: Boolean = false,
        migrationCompleted: Boolean = false,
        previousStatus: String? = null,
        rollbackRequiredAt: String? = null,
        finalFailureReason: String? = null,
    )

    /**
     * owned payload を削除してから marker を最後に除去し、cleanup 完了の成否を返す。
     *
     * success result と quarantine incident は削除せず、payload または marker が残れば false。
     */
    fun cleanupPending(): Boolean

    /** result directory を cleanup する。 */
    fun cleanupResult()
}

/**
 * [PendingRestoreFileStore] の本番実装。
 *
 * atomic marker/result JSON のencode/decodeとpending/result directory cleanup、および
 * pending cleanupから独立したquarantine artifactの保存先をDB/Hilt非依存で扱う。
 */
@OptIn(ExperimentalStdlibApi::class)
internal class RealPendingRestoreFileStore(
    context: Context,
    moshi: Moshi,
) : PendingRestoreFileStore {
    private val appContext = context.applicationContext ?: context
    private val markerAdapter = moshi.adapter<PendingRestoreMarker>()
    private val resultAdapter = moshi.adapter<PendingRestoreResultFile>()

    override val pendingDir: File =
        File(appContext.filesDir, PendingRestoreManager.PENDING_DIR_NAME)
    override val rollbackDir: File =
        File(pendingDir, PendingRestoreManager.ROLLBACK_DIR_NAME)
    override val quarantineRootDir: File =
        File(appContext.filesDir, PendingRestoreManager.QUARANTINE_DIR_NAME)

    private val markerFile = File(pendingDir, PendingRestoreManager.MARKER_FILENAME)
    private val markerStore = AtomicPendingRestoreMarkerFile(markerFile, markerAdapter)
    private val resultDir = File(appContext.filesDir, PendingRestoreManager.RESULT_DIR_NAME)
    private val resultFile = File(resultDir, PendingRestoreManager.RESULT_FILENAME)
    private val resultStore = AtomicPendingRestoreResultFile(resultFile, resultAdapter)

    /**
     * 既存artifactを上書きしないquarantine incident directoryを作成する。
     *
     * rootとincidentの作成はそれぞれ明示的に検証し、作成できない場合はcallerへ
     * 例外を返す。incidentはpending directory外にあるため、pending cleanupで削除されない。
     */
    override fun createQuarantineIncidentDir(): File {
        if (!quarantineRootDir.exists() && !quarantineRootDir.mkdirs()) {
            throw IOException("failed to create quarantine root: $quarantineRootDir")
        }
        if (!quarantineRootDir.isDirectory) {
            throw IOException("quarantine root is not a directory: $quarantineRootDir")
        }

        repeat(MAX_QUARANTINE_DIRECTORY_ATTEMPTS) {
            val incidentDir = File(
                quarantineRootDir,
                "incident-${UUID.randomUUID()}",
            )
            if (incidentDir.mkdir()) {
                return incidentDir
            }
        }

        throw IOException("failed to create unique quarantine incident in $quarantineRootDir")
    }

    /** malformed marker は通常起動優先のため null 扱いにする。 */
    override fun readMarker(): PendingRestoreMarker? = synchronized(PendingRestoreMarkerFileLock.monitor) {
        markerStore.read()
    }

    override fun writeMarker(marker: PendingRestoreMarker) {
        synchronized(PendingRestoreMarkerFileLock.monitor) {
            markerStore.write(marker)
        }
    }

    /** result JSONを共有monitor下で保存し、別instanceのreaderと競合しないようにする。 */
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
        synchronized(PendingRestoreResultFileLock.monitor) {
            resultStore.write(
                PendingRestoreResultFile(
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
                ),
            )
        }
    }

    /**
     * staged payload を先に削除し、active marker を最後に除去する。
     *
     * success result と quarantine incident は別 lifecycle のため触らない。既にない payload
     * は成功扱いにし、残存 payload または marker の削除失敗だけを retryable failure とする。
     */
    override fun cleanupPending(): Boolean {
        synchronized(PendingRestoreMarkerFileLock.monitor) {
            return try {
                // --- Owned pending payload ---
                val payloads = listOf(
                    File(pendingDir, "database"),
                    File(pendingDir, "datastore"),
                    rollbackDir,
                    File(pendingDir, PendingRestoreManager.DATASTORE_ROLLBACK_SNAPSHOT_FILENAME),
                )
                if (payloads.any { !deletePayload(it) }) {
                    logWarn("cleanup pending payload failed")
                    return false
                }

                // --- Marker removal commit point ---
                if (!markerStore.delete()) {
                    logWarn("cleanup pending marker failed")
                    return false
                }
                // 空 directory の削除は best effort。payload と marker が消えていれば成功扱いにする。
                if (pendingDir.exists() && pendingDir.listFiles().orEmpty().isEmpty()) {
                    pendingDir.delete()
                }
                true
            } catch (e: Exception) {
                logWarn("cleanup pending failed: ${e.message}")
                false
            }
        }
    }

    /** payload が残っていないことまで確認して削除する。 */
    private fun deletePayload(payload: File): Boolean {
        if (!payload.exists()) return true
        return payload.deleteRecursively() && !payload.exists()
    }

    /** result directoryを共有monitor下で削除する。 */
    override fun cleanupResult() {
        synchronized(PendingRestoreResultFileLock.monitor) {
            try {
                if (resultDir.exists()) {
                    resultDir.deleteRecursively()
                }
            } catch (e: Exception) {
                logWarn("cleanup result failed: ${e.message}")
            }
        }
    }

    private fun logWarn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: RuntimeException) {
            // JVM unit test の android.util.Log stub では例外になるため握りつぶす。
        }
    }

    /** 定数。 */
    private companion object {
        private const val TAG = "PendingRestoreFileStore"
        private const val MAX_QUARANTINE_DIRECTORY_ATTEMPTS = 3
    }
}
