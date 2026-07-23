package com.websarva.wings.android.slevo.data.backup.model

import com.squareup.moshi.JsonClass

/**
 * バックアップ ZIP に含める manifest の JSON モデル。
 *
 * `backupFormatVersion = 1` を version 1 の互換性契約とし、
 * `backupMode` は初期実装では `"full"` 固定とする。
 * `included` は各カテゴリの含有有無を保持し、復元実装が判定に使う。
 */
@JsonClass(generateAdapter = true)
data class BackupManifest(
    val backupFormatVersion: Int = 1,
    val backupMode: String = "full",
    val createdAt: String,
    val appVersionCode: Long,
    val appVersionName: String,
    val databaseVersion: Int,
    val included: IncludedContents,
)

/**
 * バックアップに含めるカテゴリの指定。
 *
 * 初期実装では `database`、`settings`、`tabs` は常に true。
 * `cookies` は確認ダイアログでのユーザー選択値を反映する。
 */
@JsonClass(generateAdapter = true)
data class IncludedContents(
    val database: Boolean = true,
    val settings: Boolean = true,
    val tabs: Boolean = true,
    val cookies: Boolean,
)
