package com.websarva.wings.android.slevo.data.backup.pending

/**
 * Room migration の delegate 開始を pending restore marker へ記録した結果。
 *
 * `Recorded` は今回の migration 開始を durable に記録した状態、`NotApplicable` は通常 migration
 * または chain の後段、`AlreadyStarted` は同じ復元世代の開始証跡が既にある状態を表す。
 */
enum class MigrationAttemptRecordingResult {
    /** 今回の migration 開始証跡を commit した。 */
    Recorded,

    /** pending restore の開始 migration ではない。 */
    NotApplicable,

    /** 同じ開始 version の証跡が既に commit 済みである。 */
    AlreadyStarted,
}

/**
 * pending restore の最初の Room migration 開始を marker に永続化する recorder。
 *
 * marker の status、開始 version、既存証跡を lock 内で再評価し、条件が一致した場合だけ
 * `migrationAttemptStarted=true` を atomic に公開する。書き込み例外は caller へ伝播する。
 */
class PendingRestoreMigrationAttemptRecorder internal constructor(
    private val fileStore: PendingRestoreFileStore,
) {
    /**
     * 指定 version の migration 開始を条件付きで記録する。
     *
     * @return marker 更新結果。persistent read/write 例外の場合は delegate 前に例外を返す。
     */
    fun record(startVersion: Int): MigrationAttemptRecordingResult {
        return fileStore.mutateMarkerAtomically { marker ->
            when {
                marker == null -> PendingRestoreMarkerMutation(
                    result = MigrationAttemptRecordingResult.NotApplicable,
                )
                marker.status != RestoreStatus.MIGRATION_PENDING -> PendingRestoreMarkerMutation(
                    result = MigrationAttemptRecordingResult.NotApplicable,
                )
                marker.databaseVersion != startVersion -> PendingRestoreMarkerMutation(
                    result = MigrationAttemptRecordingResult.NotApplicable,
                )
                marker.migrationAttemptStarted -> PendingRestoreMarkerMutation(
                    result = MigrationAttemptRecordingResult.AlreadyStarted,
                )
                else -> PendingRestoreMarkerMutation(
                    result = MigrationAttemptRecordingResult.Recorded,
                    replacement = marker.copy(migrationAttemptStarted = true),
                )
            }
        }
    }
}
