package com.websarva.wings.android.slevo.data.backup.export

/** バックアップ作成結果の sealed class。 */
sealed class BackupExportResult {
    /** バックアップ作成成功。 */
    data object Success : BackupExportResult()
    /** バックアップ作成失敗。 [detail] は ViewModel でログ出力し、UI へは共通失敗文言を表示する。 */
    data class Failure(val detail: String) : BackupExportResult()
}
