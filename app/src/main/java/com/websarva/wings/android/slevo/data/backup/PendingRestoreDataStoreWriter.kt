package com.websarva.wings.android.slevo.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.squareup.moshi.Moshi
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import com.websarva.wings.android.slevo.data.model.GestureDirection
import java.io.File
import java.util.Locale

/**
 * pending restore の DataStore JSON を DB 非依存で反映する writer。
 *
 * 起動時に [PendingRestoreApplier] から呼び出され、
 * バックアップ内の DataStore JSON を Preferences DataStore へ保存する。
 *
 * **依存制約:** Hilt 経由の DataSource、Repository、DAO、[com.websarva.wings.android.slevo.data.datasource.local.AppDatabase]
 * には一切依存しない。この writer は `SlevoApplication.onCreate()` の `super.onCreate()` 直後、
 * Hilt による `AppDatabase` 生成前に実行される。
 *
 * DataStore ファイルパスは既存の `preferencesDataStore(name = "settings")` 等と
 * 同一パス (`<filesDir>/datastore/<name>.preferences_pb`) を直接指定する。
 *
 * @param context アプリケーション Context。`filesDir` の取得にのみ使う。
 * @param moshi Cookie の JSON シリアライズに使う Moshi インスタンス。
 */
class PendingRestoreDataStoreWriter(
    private val context: Context,
    private val moshi: Moshi,
) {
    private val settingsDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(context.filesDir, SETTINGS_PATH)
        }

    private val tabsDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(context.filesDir, TABS_PATH)
        }

    private val cookiesDataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            File(context.filesDir, COOKIES_PATH)
        }

    /**
     * バックアップの settings JSON を DataStore へ反映する。
     *
     * 設定値はすべて上書きする。既存 DataStore の値は保持しない (full overwrite)。
     * gesture actions に存在しない既知 direction は未割当としてキーを削除する。
     *
     * @param settings 検証済みの settings JSON。
     */
    suspend fun writeSettings(settings: BackupSettingsJson) {
        settingsDataStore.edit { prefs ->
            applySettingsToPreferences(prefs, settings)
        }
    }

    /**
     * バックアップの tabs JSON を DataStore へ反映する。
     *
     * @param tabs 検証済みの tabs JSON。
     */
    suspend fun writeTabs(tabs: BackupTabsJson) {
        tabsDataStore.edit { prefs ->
            prefs[LAST_SELECTED_PAGE_KEY] = tabs.lastSelectedTabsPage
        }
    }

    /**
     * バックアップの cookies JSON を DataStore へ反映する。
     *
     * 既存 CookieLocalDataSourceImpl と同じ形式 (各 Cookie を個別 JSON 文字列として
     * StringSet に保存) で書き込む。
     *
     * @param cookiesJson 検証済みの cookies JSON。
     */
    suspend fun writeCookies(cookiesJson: BackupCookiesJson) {
        val cookieJsonSet = cookiesJson.cookies.mapNotNull { item ->
            try {
                // CookieLocalDataSourceImpl と同じ Moshi adapter を使って
                // OkHttp Cookie 互換 JSON を生成する。
                val cookie = BackupRestoreMapper.toCookie(item) ?: return@mapNotNull null
                moshi.adapter(okhttp3.Cookie::class.java).toJson(cookie)
            } catch (_: Exception) {
                null
            }
        }.toSet()
        cookiesDataStore.edit { prefs ->
            prefs[COOKIES_KEY] = cookieJsonSet
        }
    }

    /**
     * settings JSON の全 field を [MutablePreferences] へ適用する。
     *
     * テストから直接呼び出せるよう internal として公開する。
     * gesture actions に存在しない既知 direction はキーを削除する。
     */
    internal fun applySettingsToPreferences(
        prefs: MutablePreferences,
        settings: BackupSettingsJson,
    ) {
        prefs[THEME_MODE_KEY] = settings.themeMode
        prefs[TREE_SORT_KEY] = settings.isTreeSort
        prefs[THREAD_MINIMAP_SCROLLBAR_KEY] = settings.isThreadMinimapScrollbarEnabled
        prefs[TEXT_SCALE_KEY] = settings.textScale
        prefs[INDIVIDUAL_TEXT_SCALE_KEY] = settings.isIndividualTextScale
        prefs[HEADER_TEXT_SCALE_KEY] = settings.headerTextScale
        prefs[BODY_TEXT_SCALE_KEY] = settings.bodyTextScale
        prefs[LINE_HEIGHT_KEY] = settings.lineHeight
        prefs[REDIRECT_KEY] = settings.isRedirect5chNetToIoEnabled

        prefs[GESTURE_ENABLED_KEY] = settings.gestureSettings.enabled
        prefs[GESTURE_SHOW_HINT_KEY] = settings.gestureSettings.showActionHints

        // 既知 direction すべてについて、actions に存在すれば設定、存在しなければ削除する。
        GestureDirection.entries.forEach { direction ->
            val kebab = BackupRestoreMapper.kebabCaseFromPascalCase(direction.name)
            val key = gestureActionKey(direction)
            val actionValue = settings.gestureSettings.actions[kebab]
            if (actionValue != null) {
                // kebab-case action 文字列を PascalCase enum name へ変換して保存する。
                // 既存 SettingsLocalDataSourceImpl は enum name を直接保存する。
                prefs[key] = kebabToPascalCase(actionValue)
            } else {
                prefs.remove(key)
            }
        }
    }

    companion object {
        internal const val SETTINGS_PATH = "datastore/settings.preferences_pb"
        internal const val TABS_PATH = "datastore/tabs.preferences_pb"
        internal const val COOKIES_PATH = "datastore/cookies.preferences_pb"

        // --- Settings keys ---
        internal val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        internal val TREE_SORT_KEY = booleanPreferencesKey("tree_sort")
        internal val THREAD_MINIMAP_SCROLLBAR_KEY = booleanPreferencesKey("thread_minimap_scrollbar")
        internal val TEXT_SCALE_KEY = floatPreferencesKey("text_scale")
        internal val INDIVIDUAL_TEXT_SCALE_KEY = booleanPreferencesKey("individual_text_scale")
        internal val HEADER_TEXT_SCALE_KEY = floatPreferencesKey("header_text_scale")
        internal val BODY_TEXT_SCALE_KEY = floatPreferencesKey("body_text_scale")
        internal val LINE_HEIGHT_KEY = floatPreferencesKey("line_height")
        internal val REDIRECT_KEY = booleanPreferencesKey("redirect_5ch_net_to_io")
        internal val GESTURE_ENABLED_KEY = booleanPreferencesKey("gesture_enabled")
        internal val GESTURE_SHOW_HINT_KEY = booleanPreferencesKey("gesture_show_action_hint")

        // --- Tabs keys ---
        internal val LAST_SELECTED_PAGE_KEY = intPreferencesKey("last_selected_page")

        // --- Cookies keys ---
        internal val COOKIES_KEY = stringSetPreferencesKey("app_cookies")

        internal fun gestureActionKey(direction: GestureDirection) =
            stringPreferencesKey("gesture_action_${direction.name.lowercase(Locale.ROOT)}")

        /**
         * kebab-case 文字列を PascalCase へ変換する。
         *
         * 例: `"switch-to-next-tab"` → `"SwitchToNextTab"`
         */
        internal fun kebabToPascalCase(kebab: String): String =
            kebab.split("-").joinToString("") { part ->
                part.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
    }
}
