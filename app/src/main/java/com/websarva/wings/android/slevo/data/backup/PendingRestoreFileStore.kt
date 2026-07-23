package com.websarva.wings.android.slevo.data.backup

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import java.io.File

/**
 * pending restore の marker/result file と cleanup を扱う抽象。
 *
 * [PendingRestoreApplier] から file I/O の詳細を分離し、
 * state machine orchestration だけに集中できるようにする。
 */
internal interface PendingRestoreFileStore {
    /** pending restore directory。 */
    val pendingDir: File

    /** rollback backup directory。 */
    val rollbackDir: File

    /** marker を読み取る。parse 失敗時は null。 */
    fun readMarker(): PendingRestoreMarker?

    /** marker を上書き保存する。 */
    fun writeMarker(marker: PendingRestoreMarker)

    /** result file を保存する。 */
    fun writeResult(success: Boolean, message: String, timestamp: String)

    /** pending directory を cleanup する。 */
    fun cleanupPending()

    /** result directory を cleanup する。 */
    fun cleanupResult()
}

/**
 * [PendingRestoreFileStore] の本番実装。
 *
 * marker/result JSON の encode/decode と pending/result directory cleanup を
 * DB/Hilt 非依存で扱う。
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

    private val markerFile = File(pendingDir, PendingRestoreManager.MARKER_FILENAME)
    private val resultDir = File(appContext.filesDir, PendingRestoreManager.RESULT_DIR_NAME)
    private val resultFile = File(resultDir, PendingRestoreManager.RESULT_FILENAME)

    /** malformed marker は通常起動優先のため null 扱いにする。 */
    override fun readMarker(): PendingRestoreMarker? {
        if (!markerFile.exists()) return null
        return try {
            markerAdapter.fromJson(markerFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    override fun writeMarker(marker: PendingRestoreMarker) {
        markerFile.parentFile?.mkdirs()
        markerFile.writeText(markerAdapter.toJson(marker))
    }

    override fun writeResult(success: Boolean, message: String, timestamp: String) {
        resultDir.mkdirs()
        resultFile.writeText(
            resultAdapter.toJson(
                PendingRestoreResultFile(
                    success = success,
                    message = message,
                    timestamp = timestamp,
                ),
            ),
        )
    }

    override fun cleanupPending() {
        try {
            pendingDir.deleteRecursively()
        } catch (e: Exception) {
            logWarn("cleanup pending failed: ${e.message}")
        }
    }

    override fun cleanupResult() {
        try {
            if (resultDir.exists()) {
                resultDir.deleteRecursively()
            }
        } catch (e: Exception) {
            logWarn("cleanup result failed: ${e.message}")
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
    }
}
