package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.core.log.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 起動後にpending restore resultを安全に消費するUI非依存consumer。
 *
 * markerとresultの組み合わせから通知可能な終端状態だけを公開し、Snackbar表示完了後の
 * conditional acknowledgeでは読み取り時tokenと現在のfile内容が一致する場合だけ削除する。
 */
@Singleton
@OptIn(ExperimentalStdlibApi::class)
class PendingRestoreResultConsumer @Inject constructor(
    @ApplicationContext context: Context,
    moshi: Moshi,
    private val logger: AppLogger,
) {
    private val appContext = context.applicationContext ?: context
    private val resultAdapter = moshi.adapter<PendingRestoreResultFile>()
    private val markerStore = RealPendingRestoreFileStore(appContext, moshi)
    private val resultFile = File(
        File(appContext.filesDir, PendingRestoreManager.RESULT_DIR_NAME),
        PendingRestoreManager.RESULT_FILENAME,
    )
    private val resultStore = AtomicPendingRestoreResultFile(resultFile, resultAdapter)
    private val markerFile = File(
        File(appContext.filesDir, PendingRestoreManager.PENDING_DIR_NAME),
        PendingRestoreManager.MARKER_FILENAME,
    )

    /**
     * result fileをmarkerと一緒に読み、UIへ渡せるtyped outcomeへ分類する。
     *
     * file I/Oやparse failureは通常起動を止めず、破損resultは同じ内容を条件付きで削除する。
     */
    fun read(): PendingRestoreResultRead {
        synchronized(PendingRestoreResultFileLock.monitor) {
            if (!resultStore.exists()) return PendingRestoreResultRead.Absent

            val raw = resultStore.readRaw() ?: run {
                logError("failed to read restore result")
                return PendingRestoreResultRead.Unreadable
            }

            val result = try {
                resultAdapter.fromJson(raw)
            } catch (exception: Exception) {
                logError("failed to parse restore result", exception)
                removeUnreadableResult(raw)
                return PendingRestoreResultRead.Unreadable
            }

            if (result == null) {
                logError("restore result was empty")
                removeUnreadableResult(raw)
                return PendingRestoreResultRead.Unreadable
            }

            val marker = markerStore.readMarker()
            if (markerFile.exists() && marker == null) {
                logError("restore marker is unreadable")
                removeUnreadableResult(raw)
                return PendingRestoreResultRead.Unreadable
            }

            return classify(marker, result, raw)
        }
    }

    /**
     * Snackbar表示済みのresultを、読み取り時tokenと現在のfile内容が一致する場合だけ削除する。
     *
     * fileがすでに存在しない場合は、別consumerが先にacknowledgeした正常状態として扱う。
     */
    fun acknowledge(token: String): Boolean {
        synchronized(PendingRestoreResultFileLock.monitor) {
            if (!resultStore.exists()) return true

            val raw = resultStore.readRaw() ?: run {
                logError("failed to read restore result for acknowledge")
                return false
            }

            if (fingerprint(raw) != token) return false
            return try {
                resultStore.delete()
            } catch (exception: Exception) {
                logError("failed to delete restore result", exception)
                false
            }
        }
    }

    // --- Result classification ---

    /** marker statusとresult fieldsの組み合わせを通知可能状態へ変換する。 */
    private fun classify(
        marker: PendingRestoreMarker?,
        result: PendingRestoreResultFile,
        raw: String,
    ): PendingRestoreResultRead {
        if (marker == null) {
            return classifyWithoutMarker(result, raw)
        }

        return when (marker.status) {
            RestoreStatus.COMPLETED -> when {
                result.success && result.migrationCompleted -> ready(result, raw)
                result.success -> PendingRestoreResultRead.Pending(marker.status)
                else -> unreadableMismatch("COMPLETED marker has failure result", raw)
            }
            RestoreStatus.FAILED, RestoreStatus.ROLLBACK_REQUIRED -> {
                if (result.success) {
                    unreadableMismatch("${marker.status} marker has success result", raw)
                } else {
                    ready(result, raw)
                }
            }
            RestoreStatus.PREPARED,
            RestoreStatus.APPLYING,
            RestoreStatus.ROLLBACK_READY,
            RestoreStatus.DB_SWAPPED,
            RestoreStatus.MIGRATION_PENDING,
            -> PendingRestoreResultRead.Pending(marker.status)
        }
    }

    /** markerがcleanup済みのresultを、result自身の最終性で分類する。 */
    private fun classifyWithoutMarker(
        result: PendingRestoreResultFile,
        raw: String,
    ): PendingRestoreResultRead {
        return if (result.success && !result.migrationCompleted) {
            PendingRestoreResultRead.Pending(null)
        } else {
            ready(result, raw)
        }
    }

    /** 診断messageを含めず、tokenと成功/失敗種別だけのnotificationを作る。 */
    private fun ready(
        result: PendingRestoreResultFile,
        raw: String,
    ): PendingRestoreResultRead.Ready = PendingRestoreResultRead.Ready(
        notification = PendingRestoreNotification(
            token = fingerprint(raw),
            type = if (result.success) {
                PendingRestoreNotificationType.SUCCESS
            } else {
                PendingRestoreNotificationType.FAILURE
            },
        ),
    )

    /** marker/result不整合を記録し、同じraw contentだけを削除する。 */
    private fun unreadableMismatch(
        reason: String,
        raw: String,
    ): PendingRestoreResultRead.Unreadable {
        logError(reason)
        removeUnreadableResult(raw)
        return PendingRestoreResultRead.Unreadable
    }

    /** parse不能または不整合resultを、読み取り後に内容照合してbest-effort削除する。 */
    private fun removeUnreadableResult(raw: String) {
        try {
            if (resultStore.exists() && resultStore.readRaw() == raw) {
                if (!resultStore.delete()) {
                    logError("failed to remove unreadable restore result")
                }
            }
        } catch (exception: Exception) {
            logError("failed to remove unreadable restore result", exception)
        }
    }

    /** result JSONのraw contentからschemaを変更せずprocess内acknowledge tokenを作る。 */
    private fun fingerprint(raw: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    /** 機密result payloadを含めずにnotification I/O errorを記録する。 */
    private fun logError(message: String, exception: Exception? = null) {
        try {
            logger.e(message, TAG, exception)
        } catch (_: RuntimeException) {
            // ログ実装の失敗は起動通知のread/deleteを妨げない。
        }
    }

    private companion object {
        private const val TAG = "PendingRestoreResult"
    }
}

/**
 * pending restore resultの読み取り状態。
 *
 * [Ready]だけがユーザー通知候補であり、[Pending]はmarker遷移後に再評価する。
 */
sealed interface PendingRestoreResultRead {
    /** result fileが存在しない状態。 */
    data object Absent : PendingRestoreResultRead

    /** restore適用中またはcompletion checker待ちの状態。 */
    data class Pending(val status: RestoreStatus?) : PendingRestoreResultRead

    /** UI通知可能な終端result。 */
    data class Ready(val notification: PendingRestoreNotification) : PendingRestoreResultRead

    /** parse、I/O、marker/result整合性の検証に失敗した状態。 */
    data object Unreadable : PendingRestoreResultRead
}

/**
 * root-level Snackbarへ渡すrestore通知。
 *
 * tokenは既存result JSONのSHA-256 fingerprintで、acknowledge時のconditional deleteに使う。
 */
data class PendingRestoreNotification(
    val token: String,
    val type: PendingRestoreNotificationType,
)

/** root-level Snackbarで表示するrestore結果の種別。 */
enum class PendingRestoreNotificationType {
    /** 復元成功。 */
    SUCCESS,

    /** 復元失敗。 */
    FAILURE,
}
