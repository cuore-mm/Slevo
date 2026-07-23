package com.websarva.wings.android.slevo.ui.settings.backup

/**
 * バックアップ作成画面で ViewModel から UI に通知する一回限りのイベント。
 */
sealed interface BackupUiEvent {
    /** バックアップ作成成功。 */
    data object ExportSucceeded : BackupUiEvent
    /** バックアップ作成失敗。 */
    data object ExportFailed : BackupUiEvent
}
