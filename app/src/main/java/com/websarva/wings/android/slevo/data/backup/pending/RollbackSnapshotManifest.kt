package com.websarva.wings.android.slevo.data.backup.pending

import com.squareup.moshi.JsonClass

/**
 * rollback snapshot の完成状態を宣言する metadata。
 *
 * `rollback-ready.json` に保存され、rollback snapshot が自己完結しているかを
 * [RealPendingRestoreDbSwapper] が検証するために使う。
 *
 * 必須 file set:
 * - main DB file (必須)
 * - `-wal` (manifest が `walIncluded = true` の場合のみ必須)
 *
 * `-shm`は SQLite が再生成できるため、snapshot には含めない。
 *
 * @property formatVersion manifest format version。互換性判定に使う。
 * @property mainDbFileName rollback directory 内の main DB file 名。
 * @property walIncluded snapshot が `-wal` を含むか。`true` の場合、rollback 時に WAL 復元が必須。
 */
@JsonClass(generateAdapter = true)
data class RollbackSnapshotManifest(
    val formatVersion: Int,
    val mainDbFileName: String,
    val walIncluded: Boolean,
) {
    companion object {
        /** 現在の format version。 */
        const val CURRENT_FORMAT_VERSION = 1

        /** rollback snapshot completion marker の file 名。 */
        const val ROLLBACK_READY_FILENAME = "rollback-ready.json"
    }
}
