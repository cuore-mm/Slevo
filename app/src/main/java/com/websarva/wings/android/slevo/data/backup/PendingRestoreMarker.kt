package com.websarva.wings.android.slevo.data.backup

import com.squareup.moshi.JsonClass

/**
 * pending restore の状態 marker。
 *
 * `filesDir/pending-restore/restore.json` に保存し、
 * 起動時の [PendingRestoreApplier] が状態遷移の判断に使う。
 *
 * 状態遷移:
 * ```
 * prepared -> applying -> db-swapped -> (deleted on success)
 *                                    \-> failed (on error)
 * ```
 *
 * - `prepared`: staging 完了、次回起動で適用待ち。
 * - `applying`: [PendingRestoreApplier] が DB 置換を開始した。
 * - `db-swapped`: DB 置換完了、DataStore 反映待ち。
 * - `failed`: 適用失敗。自動再試行しない。
 *
 * @property status 現在の状態。
 * @property createdAt marker 作成日時 (ISO 8601)。
 * @property includeCookies Cookie を復元対象に含むか。
 * @property databaseVersion バックアップの Room DB version。
 * @property failureReason 失敗理由。`failed` の場合のみ設定される。
 */
@JsonClass(generateAdapter = true)
data class PendingRestoreMarker(
    val status: RestoreStatus,
    val createdAt: String,
    val includeCookies: Boolean,
    val databaseVersion: Int,
    val failureReason: String? = null,
)

/**
 * pending restore の状態を表す enum。
 */
enum class RestoreStatus {
    /** staging 完了、次回起動で適用待ち。 */
    PREPARED,
    /** [PendingRestoreApplier] が DB 置換を開始した。 */
    APPLYING,
    /** DB 置換完了、DataStore 反映待ち。 */
    DB_SWAPPED,
    /** 適用失敗。自動再試行しない。 */
    FAILED,
}

/**
 * pending restore の適用結果を表す sealed class。
 *
 * [PendingRestoreApplier] が result file へ記録し、
 * UI が 1 回表示後に削除する。
 */
@JsonClass(generateAdapter = true)
data class PendingRestoreResultFile(
    val success: Boolean,
    val message: String,
    val timestamp: String,
)
