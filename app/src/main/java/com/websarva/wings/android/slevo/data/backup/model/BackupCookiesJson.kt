package com.websarva.wings.android.slevo.data.backup.model

import com.squareup.moshi.JsonClass

/**
 * `datastore/cookies.json` の version 1 JSON モデル。
 *
 * OkHttp `Cookie` のバックアップ互換 9 フィールドを保持する。
 * `cookies` 配列は `domain`、`path`、`name` の昇順で出力する。
 */
@JsonClass(generateAdapter = true)
data class BackupCookiesJson(
    val cookies: List<BackupCookieItem>,
)

/**
 * バックアップ用の 1 Cookie エントリ。
 *
 * OkHttp `Cookie` の `name`、`value`、`domain`、`path`、`expiresAt`、
 * `secure`、`httpOnly`、`hostOnly`、`persistent` をそのまま保持する。
 * `expiresAt` はセッション Cookie で `Long.MAX_VALUE` を取りうる。
 */
@JsonClass(generateAdapter = true)
data class BackupCookieItem(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
    val persistent: Boolean,
)
