package com.websarva.wings.android.slevo.ui.settings.backup

/**
 * バックアップ作成と復元画面の UI 状態。
 *
 * @property includeCookies 確認ダイアログのクッキー checkbox 選択状態（export / restore 共有）。
 * @property showConfirmDialog バックアップ作成確認ダイアログの表示有無。
 * @property isExporting バックアップ作成処理中か。
 * @property restoreIncludeCookies 復元確認ダイアログのクッキー checkbox 選択状態。
 * @property restorePreview 復元確認ダイアログが表示する UI 専用 preview。null の場合は
 *   ダイアログを表示しない。
 * @property isPreviewLoading 復元 preview 読み込み中か。
 * @property isRestoring 復元準備処理中か。
 * @property showRestorePreparedDialog 復元準備完了ダイアログの表示有無。
 * @property pendingResults Snackbar 表示待ちの操作結果。先頭だけが表示対象で、表示完了後に
 *   acknowledge されるまで後続結果とともに FIFO 順を維持する。
 */
data class BackupUiState(
    val includeCookies: Boolean = false,
    val showConfirmDialog: Boolean = false,
    val isExporting: Boolean = false,
    val restoreIncludeCookies: Boolean = false,
    val isPreviewLoading: Boolean = false,
    val isRestoring: Boolean = false,
    val showRestorePreparedDialog: Boolean = false,
    val restorePreview: RestorePreviewUiState? = null,
    val pendingResults: List<BackupUiEvent> = emptyList(),
)
