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

    /** pending directory を cleanup する。 */
    fun cleanupPending()

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
    override fun readMarker(): PendingRestoreMarker? = markerStore.read()

    override fun writeMarker(marker: PendingRestoreMarker) = markerStore.write(marker)

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
            resultDir.mkdirs()
            resultFile.writeText(
                resultAdapter.toJson(
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
                ),
            )
        }
    }

    override fun cleanupPending() {
        try {
            pendingDir.deleteRecursively()
        } catch (e: Exception) {
            logWarn("cleanup pending failed: ${e.message}")
        }
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
