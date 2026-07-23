package com.websarva.wings.android.slevo.data.datasource.local.impl

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.data.model.GestureSettings
import java.io.File
import java.util.Locale

/**
 * settings / tabs / cookies の Preferences DataStore を一元提供する object。
 *
 * 同一 process 内で同じ `.preferences_pb` file 用の DataStore instance が
 * 複数生成されないよう、この object を通して DataStore を取得する。
 *
 * 通常実行時の DataSource (`SettingsLocalDataSourceImpl` 等) と
 * 起動時 restore writer (`PendingRestoreDataStoreWriter`) の双方が
 * この provider を使う。
 */
object SlevoPreferenceDataStores {

    private const val SETTINGS_NAME = "settings"
    private const val TABS_NAME = "tabs"
    private const val COOKIES_NAME = "cookies"

    /** settings DataStore の file path。 */
    const val SETTINGS_PATH = "datastore/$SETTINGS_NAME.preferences_pb"
    /** tabs DataStore の file path。 */
    const val TABS_PATH = "datastore/$TABS_NAME.preferences_pb"
    /** cookies DataStore の file path。 */
    const val COOKIES_PATH = "datastore/$COOKIES_NAME.preferences_pb"

    /**
     * settings の Preferences DataStore を返す。
     *
     * ジェスチャー設定の初期化 migration を含む。
     * 初回呼び出し時にのみ DataStore instance を生成し、以降は同じ instance を返す。
     */
    fun settings(context: Context): DataStore<Preferences> {
        return settingsStore ?: synchronized(this) {
            settingsStore ?: createSettingsStore(context.applicationContext ?: context)
                .also { settingsStore = it }
        }
    }

    /**
     * tabs の Preferences DataStore を返す。
     */
    fun tabs(context: Context): DataStore<Preferences> {
        return tabsStore ?: synchronized(this) {
            tabsStore ?: createTabsStore(context.applicationContext ?: context)
                .also { tabsStore = it }
        }
    }

    /**
     * cookies の Preferences DataStore を返す。
     */
    fun cookies(context: Context): DataStore<Preferences> {
        return cookiesStore ?: synchronized(this) {
            cookiesStore ?: createCookiesStore(context.applicationContext ?: context)
                .also { cookiesStore = it }
        }
    }

    @Volatile private var settingsStore: DataStore<Preferences>? = null
    @Volatile private var tabsStore: DataStore<Preferences>? = null
    @Volatile private var cookiesStore: DataStore<Preferences>? = null

    private fun createSettingsStore(context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            migrations = listOf(GestureDefaultsMigration),
            produceFile = { File(context.filesDir, SETTINGS_PATH) },
        )
    }

    private fun createTabsStore(context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { File(context.filesDir, TABS_PATH) },
        )
    }

    private fun createCookiesStore(context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = { File(context.filesDir, COOKIES_PATH) },
        )
    }

    internal fun resetForTest() {
        settingsStore = null
        tabsStore = null
        cookiesStore = null
    }

    // --- Settings keys ---

    internal val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    internal val TREE_SORT_KEY = booleanPreferencesKey("tree_sort")
    internal val THREAD_MINIMAP_SCROLLBAR_KEY = booleanPreferencesKey("thread_minimap_scrollbar")
    internal val TEXT_SCALE_KEY = floatPreferencesKey("text_scale")
    internal val INDIVIDUAL_TEXT_SCALE_KEY = booleanPreferencesKey("individual_text_scale")
    internal val HEADER_TEXT_SCALE_KEY = floatPreferencesKey("header_text_scale")
    internal val BODY_TEXT_SCALE_KEY = floatPreferencesKey("body_text_scale")
    internal val LINE_HEIGHT_KEY = floatPreferencesKey("line_height")
    internal val REDIRECT_5CH_NET_TO_IO_KEY = booleanPreferencesKey("redirect_5ch_net_to_io")
    internal val GESTURE_ENABLED_KEY = booleanPreferencesKey("gesture_enabled")
    internal val GESTURE_SHOW_HINT_KEY = booleanPreferencesKey("gesture_show_action_hint")
    internal val GESTURE_ASSIGNMENTS_INITIALIZED_KEY =
        booleanPreferencesKey("gesture_assignments_initialized")
    internal val GESTURE_ACTION_KEYS = GestureDirection.entries.associateWith { direction ->
        stringPreferencesKey("gesture_action_${direction.name.lowercase(Locale.ROOT)}")
    }

    // --- Tabs keys ---

    internal val LAST_PAGE_KEY = intPreferencesKey("last_selected_page")

    // --- Cookies keys ---

    internal val COOKIE_KEY = stringSetPreferencesKey("app_cookies")

    // --- Migration ---

    /**
     * ジェスチャー設定のデフォルト値を挿入する migration。
     *
     * 初期化フラグが未設定かつジェスチャー関連キーが一切存在しない
     * (＝真に未初期化なユーザー) の場合のみ migrate を実行する。
     */
    internal object GestureDefaultsMigration : DataMigration<Preferences> {
        override suspend fun shouldMigrate(currentData: Preferences): Boolean {
            val notInitialized = currentData[GESTURE_ASSIGNMENTS_INITIALIZED_KEY] != true
            val hasAnyGestureKey = currentData.contains(GESTURE_ENABLED_KEY)
                || currentData.contains(GESTURE_SHOW_HINT_KEY)
                || GestureDirection.entries.any { direction ->
                    val key = GESTURE_ACTION_KEYS.getValue(direction)
                    currentData.contains(key)
                }
            return notInitialized && !hasAnyGestureKey
        }

        override suspend fun migrate(currentData: Preferences): Preferences {
            val prefs = currentData.toMutablePreferences()
            if (!prefs.contains(GESTURE_ENABLED_KEY)) {
                prefs[GESTURE_ENABLED_KEY] = GestureSettings.DEFAULT.isEnabled
            }
            if (!prefs.contains(GESTURE_SHOW_HINT_KEY)) {
                prefs[GESTURE_SHOW_HINT_KEY] = GestureSettings.DEFAULT.showActionHints
            }
            GestureDirection.entries.forEach { direction ->
                val key = GESTURE_ACTION_KEYS.getValue(direction)
                if (!prefs.contains(key)) {
                    val defaultAction = GestureSettings.DEFAULT.assignments[direction]
                    if (defaultAction == null) {
                        prefs.remove(key)
                    } else {
                        prefs[key] = defaultAction.name
                    }
                }
            }
            prefs[GESTURE_ASSIGNMENTS_INITIALIZED_KEY] = true
            return prefs.toPreferences()
        }

        override suspend fun cleanUp() {
            // No-op
        }
    }
}
