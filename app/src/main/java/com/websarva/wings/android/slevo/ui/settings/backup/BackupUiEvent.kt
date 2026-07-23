package com.websarva.wings.android.slevo.ui.settings.backup

/**
 * バックアップ作成・復元画面で ViewModel から UI に通知する一回限りのイベント。
 *
 * 復元準備成功はイベントではなく [BackupUiState.showRestorePreparedDialog] で扱う。
 */
sealed interface BackupUiEvent {
    /** バックアップ作成成功。 */
    data object ExportSucceeded : BackupUiEvent
    /** バックアップ作成失敗。 */
    data object ExportFailed : BackupUiEvent

    /** 復元準備失敗（リトライ可能）。 */
    data object RestorePrepareFailed : BackupUiEvent
    /** 選択されたファイルが無効または未対応のバックアップ。 */
    data object InvalidBackup : BackupUiEvent
}
