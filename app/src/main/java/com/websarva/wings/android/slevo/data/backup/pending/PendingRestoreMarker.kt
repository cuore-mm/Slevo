package com.websarva.wings.android.slevo.data.backup.pending

import com.squareup.moshi.JsonClass

/**
 * pending restore の状態 marker。
 *
 * `filesDir/pending-restore/restore.json` に保存し、
 * 起動時の [PendingRestoreApplier] が状態遷移の判断に使う。
 *
 * 状態遷移:
 * ```
 * prepared -> applying -> db-swapped -> migration-pending -> completed -> (deleted on success)
 *                                    \            \               \
 *                                     +------------+---------------+-> failed (on error)
 * ```
 *
 * - `prepared`: staging 完了、次回起動で適用待ち。
 * - `applying`: [PendingRestoreApplier] が DB 置換を開始した。
 * - `db-swapped`: DB 置換完了、DataStore 反映待ち。
 * - `migration-pending`: DB swap と DataStore 反映完了。Room migration/current DB open の成功確認待ち。
 * - `rollback-required`: Room open 後の post-migration validation 失敗。次回 cold start で rollback する。
 * - `completed`: post-migration validation 成功を durable に記録済み。cleanup 未完了時も rollback 禁止。
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
@JsonClass(generateAdapter = false)
enum class RestoreStatus {
    /** staging 完了、次回起動で適用待ち。 */
    PREPARED,
    /** [PendingRestoreApplier] が DB 置換を開始した。 */
    APPLYING,
    /** DB 置換完了、DataStore 反映待ち。 */
    DB_SWAPPED,
    /** DB swap と DataStore 反映完了。Room migration/current DB open の成功確認待ち。 */
    MIGRATION_PENDING,
    /** Room open 後の post-migration validation 失敗。次回 cold start で rollback する。 */
    ROLLBACK_REQUIRED,
    /** post-migration validation 成功 durable 記録済み。cleanup 未完了時も rollback 禁止。 */
    COMPLETED,
    /** 適用失敗。自動再試行しない。 */
    FAILED,
}

/**
 * pending restore の適用結果を表す data class。
 *
 * [PendingRestoreApplier] が result file へ記録し、
 * UI が 1 回表示後に削除する。
 *
 * @property success 復元成功か。
 * @property message 成功/失敗メッセージ。
 * @property timestamp 記録日時。
 * @property backupDatabaseVersion バックアップの Room DB version。不明時は null。
 * @property currentDatabaseVersion 現在の Room DB version。
 * @property migrationRequired 古い DB version の migration が必要だったか。
 * @property migrationCompleted migration が完了したか。失敗時は false。
 * @property previousStatus rollback-required から transition した場合の前状態。
 * @property rollbackRequiredAt rollback-required が記録された日時。
 * @property finalFailureReason 最終的な失敗理由。
 */
@JsonClass(generateAdapter = true)
data class PendingRestoreResultFile(
    val success: Boolean,
    val message: String,
    val timestamp: String,
    val backupDatabaseVersion: Int? = null,
    val currentDatabaseVersion: Int? = null,
    val migrationRequired: Boolean = false,
    val migrationCompleted: Boolean = false,
    val previousStatus: String? = null,
    val rollbackRequiredAt: String? = null,
    val finalFailureReason: String? = null,
)
