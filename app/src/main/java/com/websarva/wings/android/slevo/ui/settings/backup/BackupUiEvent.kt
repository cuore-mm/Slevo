package com.websarva.wings.android.slevo.ui.settings.backup

/**
 * バックアップ作成・復元画面で表示待ちとなる操作結果。
 *
 * 各結果は ViewModel instance 内で単調増加する [id] を持ち、Snackbar 表示完了まで
 * [BackupUiState.pendingResults] に保持される。復元準備成功は結果ではなく
 * [BackupUiState.showRestorePreparedDialog] で扱う。
 */
sealed interface BackupUiEvent {
    /** バックアップ作成成功。 */
    data class ExportSucceeded(override val id: Long) : BackupUiEvent

    /** バックアップ作成失敗。 */
    data class ExportFailed(override val id: Long) : BackupUiEvent

    /** 復元準備失敗（リトライ可能）。 */
    data class RestorePrepareFailed(override val id: Long) : BackupUiEvent

    /** 選択されたファイルが無効または未対応のバックアップ。 */
    data class InvalidBackup(override val id: Long) : BackupUiEvent

    /** 表示待ち結果を識別する ViewModel instance 内の単調増加 ID。 */
    val id: Long
}
