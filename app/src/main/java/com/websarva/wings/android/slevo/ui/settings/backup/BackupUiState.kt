package com.websarva.wings.android.slevo.ui.settings.backup

import com.websarva.wings.android.slevo.data.backup.BackupPreview

/**
 * バックアップ作成と復元画面の UI 状態。
 *
 * @property includeCookies 確認ダイアログのクッキー checkbox 選択状態（export / restore 共有）。
 * @property showConfirmDialog バックアップ作成確認ダイアログの表示有無。
 * @property isExporting バックアップ作成処理中か。
 * @property restoreIncludeCookies 復元確認ダイアログのクッキー checkbox 選択状態。
 * @property showRestoreConfirmDialog 復元確認ダイアログの表示有無。
 * @property isPreviewLoading 復元 preview 読み込み中か。
 * @property isRestoring 復元準備処理中か。
 * @property showRestorePreparedDialog 復元準備完了ダイアログの表示有無。
 * @property restorePreview 検証済みの復元 preview。preview 成功時のみ設定される。
 * @property previewContainsCookies 選択されたバックアップに Cookie が含まれているか。
 */
data class BackupUiState(
    val includeCookies: Boolean = false,
    val showConfirmDialog: Boolean = false,
    val isExporting: Boolean = false,
    val restoreIncludeCookies: Boolean = false,
    val showRestoreConfirmDialog: Boolean = false,
    val isPreviewLoading: Boolean = false,
    val isRestoring: Boolean = false,
    val showRestorePreparedDialog: Boolean = false,
    val restorePreview: BackupPreview? = null,
    val previewContainsCookies: Boolean = false,
)
