package com.websarva.wings.android.slevo.data.backup.restore

/**
 * 復元確認ダイアログへ渡す、検証済みバックアップの最小 metadata。
 *
 * 一時 DB file や DataStore JSON を UI 層へ漏らさず、preview と restore preparation の
 * 両方で同じ作成日時・作成元 version・Cookie 含有情報を表現する。
 */
data class BackupConfirmationMetadata(
    val createdAt: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val containsCookies: Boolean,
)
