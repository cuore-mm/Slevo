package com.websarva.wings.android.slevo.data.backup.model

import com.squareup.moshi.JsonClass

/**
 * `datastore/tabs.json` の version 1 JSON モデル。
 *
 * タブ画面で最後に選択していたページ番号を保持する。
 */
@JsonClass(generateAdapter = true)
data class BackupTabsJson(
    val lastSelectedTabsPage: Int,
)
