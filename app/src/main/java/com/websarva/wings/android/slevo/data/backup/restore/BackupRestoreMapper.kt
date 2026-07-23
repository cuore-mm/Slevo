package com.websarva.wings.android.slevo.data.backup.restore

import com.websarva.wings.android.slevo.data.backup.model.BackupCookieItem
import com.websarva.wings.android.slevo.data.backup.model.BackupGestureSettings
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.ThemeMode
import okhttp3.Cookie

/**
 * バックアップ DTO から既存アプリのデータモデルへ逆変換する mapper。
 *
 * [com.websarva.wings.android.slevo.data.backup.export.BackupDataMapper] の export 方向に対する restore 方向の変換を提供する。
 * enum は小文字 kebab-case 文字列から既存 enum へ戻す。
 *
 * 起動時 pending restore の DataStore 反映では、
 * [com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreDataStoreWriter] がこの mapper 経由で値を変換して DataStore へ保存する。
 * Hilt 経由の DataSource、Repository、DAO、AppDatabase には依存しない。
 */
object BackupRestoreMapper {

    // --- Settings ---

    /**
     * [BackupSettingsJson] から [ThemeMode] へ逆変換する。
     *
     * 未知の値は `null` を返す。呼び出し側で validation 済みであることを前提にする。
     */
    fun toThemeMode(themeMode: String): ThemeMode? = when (themeMode) {
        "light" -> ThemeMode.LIGHT
        "dark" -> ThemeMode.DARK
        "system" -> ThemeMode.SYSTEM
        else -> null
    }

    /**
     * [BackupGestureSettings] から [GestureSettings] へ逆変換する。
     *
     * - actions の key は kebab-case gesture direction 文字列。
     * - value は kebab-case gesture action 文字列または null (未割り当て)。
     * - JSON に存在しない既知 direction は未割当として `null` で出力する。
     * - 未知 direction key は caller が事前 validation で排除済みである前提。
     *
     * @param actions backup の gesture actions map。
     * @return [GestureSettings]。
     */
    fun toGestureSettings(
        enabled: Boolean,
        showActionHints: Boolean,
        actions: Map<String, String?>,
    ): GestureSettings {
        val assignments = GestureDirection.entries.associateWith { direction ->
            val kebab = kebabCaseFromPascalCase(direction.name)
            val actionStr = actions[kebab]
            if (actionStr == null) {
                null
            } else {
                GestureAction.entries.firstOrNull {
                    kebabCaseFromPascalCase(it.name) == actionStr
                }
            }
        }
        return GestureSettings(
            isEnabled = enabled,
            showActionHints = showActionHints,
            assignments = assignments,
        )
    }

    // --- Tabs ---

    /**
     * バックアップの `lastSelectedTabsPage` をそのまま返す。
     *
     * 値自体は [BackupReader] で validation 済み。
     */
    fun toLastSelectedTabsPage(page: Int): Int = page

    // --- Cookies ---

    /**
     * [BackupCookieItem] を [Cookie] へ逆変換する。
     *
     * OkHttp [Cookie.Builder] を使い、バックアップの 9 field を復元する。
     * [BackupReader] で name/domain/path の空チェック済みである前提。
     *
     * `hostOnly=true` の場合は [Cookie.Builder.hostOnlyDomain] を、
     * `hostOnly=false` の場合は [Cookie.Builder.domain] を呼び分ける。
     *
     * @return 変換成功時は [Cookie]、builder が拒否する値の場合は `null`。
     */
    fun toCookie(item: BackupCookieItem): Cookie? {
        return try {
            // --- Builder 構築 ---
            val builder = Cookie.Builder()
                .name(item.name)
                .value(item.value)
                .path(item.path)
                .expiresAt(item.expiresAt)

            // --- Domain scope: hostOnly で分岐 ---
            if (item.hostOnly) {
                builder.hostOnlyDomain(item.domain)
            } else {
                builder.domain(item.domain)
            }

            if (item.secure) builder.secure()
            if (item.httpOnly) builder.httpOnly()
            builder.build()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * [BackupCookieItem] のリストを [Cookie] のリストへ逆変換する。
     *
     * 変換に失敗した item は除外する。
     */
    fun toCookies(items: List<BackupCookieItem>): List<Cookie> =
        items.mapNotNull { toCookie(it) }

    // --- Kebab-case helper ---

    /**
     * Kotlin enum の PascalCase 名を kebab-case へ変換する。
     *
     * [com.websarva.wings.android.slevo.data.backup.export.BackupDataMapper.enumNameToKebabCase] の逆変換ではないが、
     * 同じ変換ルールで kebab-case 文字列を生成する。
     * 例: `"RightUp"` → `"right-up"`, `"SwitchToNextTab"` → `"switch-to-next-tab"`
     */
    internal fun kebabCaseFromPascalCase(pascal: String): String {
        if (pascal.isEmpty()) return pascal
        val result = StringBuilder()
        var i = 0
        while (i < pascal.length) {
            if (i > 0 && pascal[i].isUpperCase()) {
                val isStartOfAcronym = i + 1 < pascal.length && pascal[i + 1].isLowerCase()
                val isEndOfAcronym = i > 1 && pascal[i - 1].isUpperCase() &&
                    pascal.getOrNull(i - 2)?.isUpperCase() == true
                if (isStartOfAcronym || !isEndOfAcronym) {
                    result.append('-')
                }
            }
            result.append(pascal[i].lowercaseChar())
            i++
        }
        return result.toString()
    }
}
