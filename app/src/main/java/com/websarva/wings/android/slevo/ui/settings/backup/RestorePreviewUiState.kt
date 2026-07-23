package com.websarva.wings.android.slevo.ui.settings.backup

/**
 * 復元確認ダイアログが表示する、UI 専用の immutable preview state。
 *
 * data 層の一時 file や検証済み JSON を保持せず、ViewModel の StateFlow で configuration
 * change 中も確認内容を維持する。process death 用の永続化は行わない。
 */
data class RestorePreviewUiState(
    val createdAt: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val containsCookies: Boolean,
)
