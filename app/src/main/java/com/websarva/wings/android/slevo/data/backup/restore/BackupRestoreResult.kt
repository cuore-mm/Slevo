package com.websarva.wings.android.slevo.data.backup.restore

/**
 * バックアップ復元の準備結果を表す sealed class。
 *
 * 復元準備は ZIP 検証から pending restore marker 作成までを含み、
 * 実際の DB 置換と DataStore 反映は次回起動時に [com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreApplier] が行う。
 *
 * - [Success]: pending restore の準備が完了し、次回起動で適用される。
 * - [Failure]: I/O エラーや pending restore 作成失敗など、リトライ可能なエラー。
 * - [Invalid]: ZIP 形式や manifest の問題など、バックアップ自体が無効。
 */
sealed class BackupRestoreResult {

    /**
     * 復元準備成功。次回アプリ起動で復元が適用される。
     *
     * @property metadata 復元確認に必要な、検証済みバックアップの metadata。
     */
    data class Success(val metadata: BackupConfirmationMetadata) : BackupRestoreResult()

    /**
     * 復元準備失敗（リトライ可能）。
     *
     * [detail] は ViewModel でログ出力し、UI へは共通失敗文言を表示する。
     */
    data class Failure(val detail: String) : BackupRestoreResult()

    /**
     * バックアップが無効。
     *
     * ZIP 形式不正、manifest 不正、DB schema 不一致、JSON 不正などを含む。
     * [detail] は ViewModel でログ出力し、UI へは無効バックアップ文言を表示する。
     */
    data class Invalid(val detail: String) : BackupRestoreResult()
}
