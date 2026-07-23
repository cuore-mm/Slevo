package com.websarva.wings.android.slevo.data.backup

import com.websarva.wings.android.slevo.data.backup.model.BackupCookieItem
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupGestureSettings
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.ThemeMode
import okhttp3.Cookie

/**
 * 既存アプリのデータモデルをバックアップ DTO へ変換する mapper。
 *
 * 各変換はバックアップ schema version 1 の互換性契約に従い、
 * enum は小文字 kebab-case 文字列、配列は安定した順序で出力する。
 */
object BackupDataMapper {

    // --- Settings ---

    /**
     * 設定値を [BackupSettingsJson] へ変換する。
     *
     * gesture actions の map key は昇順出力する。
     */
    fun toBackupSettingsJson(
        themeMode: ThemeMode,
        isTreeSort: Boolean,
        isThreadMinimapScrollbarEnabled: Boolean,
        textScale: Float,
        isIndividualTextScale: Boolean,
        headerTextScale: Float,
        bodyTextScale: Float,
        lineHeight: Float,
        isRedirect5chNetToIoEnabled: Boolean,
        gestureSettings: GestureSettings,
    ): BackupSettingsJson = BackupSettingsJson(
        themeMode = themeMode.toBackupString(),
        isTreeSort = isTreeSort,
        isThreadMinimapScrollbarEnabled = isThreadMinimapScrollbarEnabled,
        textScale = textScale,
        isIndividualTextScale = isIndividualTextScale,
        headerTextScale = headerTextScale,
        bodyTextScale = bodyTextScale,
        lineHeight = lineHeight,
        isRedirect5chNetToIoEnabled = isRedirect5chNetToIoEnabled,
        gestureSettings = toBackupGestureSettings(gestureSettings),
    )

    private fun toBackupGestureSettings(settings: GestureSettings): BackupGestureSettings {
        // actions は非 null 値を持つ方向だけを含め、direction kebab-case key の昇順で出力する。
        // Moshi のデフォルト Map<String, String?> では null value が破棄されるため、
        // 未割り当て方向は map から省略する。
        val sortedActions = GestureDirection.entries
            .mapNotNull { direction ->
                val action = settings.assignments[direction]?.toBackupString() ?: return@mapNotNull null
                direction.toBackupString() to action
            }
            .sortedBy { it.first }
            .toMap(LinkedHashMap())
        return BackupGestureSettings(
            enabled = settings.isEnabled,
            showActionHints = settings.showActionHints,
            actions = sortedActions,
        )
    }

    // --- Tabs ---

    /**
     * タブ選択ページ値を [BackupTabsJson] へ変換する。
     */
    fun toBackupTabsJson(lastSelectedTabsPage: Int): BackupTabsJson =
        BackupTabsJson(lastSelectedTabsPage = lastSelectedTabsPage)

    // --- Cookies ---

    /**
     * OkHttp Cookie のリストを [BackupCookiesJson] へ変換する。
     *
     * `cookies` 配列は `domain`、`path`、`name` の昇順で出力する。
     */
    fun toBackupCookiesJson(cookies: List<Cookie>): BackupCookiesJson {
        val items = cookies
            .sortedWith(compareBy({ it.domain }, { it.path }, { it.name }))
            .map { cookie ->
                BackupCookieItem(
                    name = cookie.name,
                    value = cookie.value,
                    domain = cookie.domain,
                    path = cookie.path,
                    expiresAt = cookie.expiresAt,
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    hostOnly = cookie.hostOnly,
                    persistent = cookie.persistent,
                )
            }
        return BackupCookiesJson(cookies = items)
    }

    // --- Enum to kebab-case helpers ---

    /**
     * [ThemeMode] enum を version 1 バックアップ文字列へ変換する。
     */
    fun ThemeMode.toBackupString(): String = when (this) {
        ThemeMode.LIGHT -> "light"
        ThemeMode.DARK -> "dark"
        ThemeMode.SYSTEM -> "system"
    }

    fun GestureDirection.toBackupString(): String =
        enumNameToKebabCase(name)

    fun GestureAction.toBackupString(): String =
        enumNameToKebabCase(name)

    /**
     * Kotlin enum の `PascalCase` 名を `kebab-case` 文字列へ変換する。
     *
     * 大文字連続は 1 単語扱いにする (例: `ToRight` → `"to-right"`)。
     */
    internal fun enumNameToKebabCase(enumName: String): String {
        if (enumName.isEmpty()) return enumName
        val result = StringBuilder()
        var i = 0
        while (i < enumName.length) {
            // 次の非先頭位置で大文字が出現したら区切りを入れる。
            if (i > 0 && enumName[i].isUpperCase()) {
                // 大文字連続の終端かどうか判定する。
                val isStartOfAcronym = i + 1 < enumName.length && enumName[i + 1].isLowerCase()
                val isEndOfAcronym = i > 1 && enumName[i - 1].isUpperCase() &&
                    enumName.getOrNull(i - 2)?.isUpperCase() == true
                if (isStartOfAcronym || !isEndOfAcronym) {
                    result.append('-')
                }
            }
            result.append(enumName[i].lowercaseChar())
            i++
        }
        return result.toString()
    }
}
