package com.websarva.wings.android.slevo.ui.settings.backup

/**
 * バックアップ作成画面の UI 状態。
 *
 * @property includeCookies 確認ダイアログのクッキー checkbox 選択状態。
 * @property showConfirmDialog 確認ダイアログの表示有無。
 * @property isExporting バックアップ作成処理中か。
 */
data class BackupUiState(
    val includeCookies: Boolean = false,
    val showConfirmDialog: Boolean = false,
    val isExporting: Boolean = false,
)
