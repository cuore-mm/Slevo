package com.websarva.wings.android.slevo.data.backup.pending

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration delegate の前に pending restore の開始証跡を記録する wrapper。
 *
 * wrapper 自身は delegate と同じ version pair を公開し、通常 migration では SQL、順序、例外を
 * delegate へそのまま委譲する。既に開始済みの pending restore は delegate を再実行しない。
 */
internal class PendingRestoreMigrationWrapper(
    private val delegate: Migration,
    private val recorder: PendingRestoreMigrationAttemptRecorder,
) : Migration(delegate.startVersion, delegate.endVersion) {
    /**
     * 証跡 commit 後に delegate を一度だけ呼び出す。
     *
     * `AlreadyStarted` は同一 process の Room open retry でも delegate 前に停止し、その他の
     * recorder 結果では delegate の original exception を保持して伝播する。
     */
    override fun migrate(db: SupportSQLiteDatabase) {
        when (recorder.record(startVersion)) {
            MigrationAttemptRecordingResult.AlreadyStarted -> {
                throw IllegalStateException(
                    "pending restore migration already started: $startVersion->$endVersion",
                )
            }
            MigrationAttemptRecordingResult.Recorded,
            MigrationAttemptRecordingResult.NotApplicable -> delegate.migrate(db)
        }
    }
}
