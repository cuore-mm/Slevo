package com.websarva.wings.android.slevo.data.backup.model

import com.squareup.moshi.JsonClass

/**
 * `datastore/settings.json` の version 1 JSON モデル。
 *
 * すべて boolean/number/string で表現し、enum は小文字 kebab-case 文字列として保存する。
 * gesture は専用モデルに分割し、`gestureSettings.actions` の key は昇順出力する。
 */
@JsonClass(generateAdapter = true)
data class BackupSettingsJson(
    val themeMode: String,
    val isTreeSort: Boolean,
    val isThreadMinimapScrollbarEnabled: Boolean,
    val textScale: Float,
    val isIndividualTextScale: Boolean,
    val headerTextScale: Float,
    val bodyTextScale: Float,
    val lineHeight: Float,
    val isRedirect5chNetToIoEnabled: Boolean,
    val gestureSettings: BackupGestureSettings,
)

/**
 * ジェスチャー設定の JSON 表現。
 *
 * `enabled` はジェスチャー全体の有効無効。
 * `actions` は gesture direction (kebab-case key) から gesture action (kebab-case value)
 * または null (未割り当て) へのマップ。key は昇順で出力する。
 */
@JsonClass(generateAdapter = true)
data class BackupGestureSettings(
    val enabled: Boolean,
    val showActionHints: Boolean,
    val actions: Map<String, String?>,
)
