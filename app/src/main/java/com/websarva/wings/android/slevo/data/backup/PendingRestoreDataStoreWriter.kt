package com.websarva.wings.android.slevo.data.backup

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import com.squareup.moshi.Moshi
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import com.websarva.wings.android.slevo.data.datasource.local.impl.SlevoPreferenceDataStores
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
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
 * **DataStore instance:** 独自に DataStore を生成せず、[SlevoPreferenceDataStores] から取得する。
 * これにより同一 process 内で DataStore が多重生成されないことを保証する。
 *
 * @param context アプリケーション Context。`filesDir` の取得にのみ使う。
 * @param moshi Cookie の JSON シリアライズに使う Moshi インスタンス。
 */
class PendingRestoreDataStoreWriter(
    private val context: Context,
    private val moshi: Moshi,
) {
    private val settingsDataStore get() = SlevoPreferenceDataStores.settings(context)
    private val tabsDataStore get() = SlevoPreferenceDataStores.tabs(context)
    private val cookiesDataStore get() = SlevoPreferenceDataStores.cookies(context)

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
            prefs[SlevoPreferenceDataStores.LAST_PAGE_KEY] = tabs.lastSelectedTabsPage
        }
    }

    /**
     * バックアップの cookies JSON を DataStore へ反映する。
     *
     * 既存 CookieLocalDataSourceImpl と同じ形式 (各 Cookie を個別 JSON 文字列として
     * StringSet に保存) で書き込む。
     * serialize に失敗した Cookie が 1 件以上ある場合はエラーメッセージを返す。
     *
     * @param cookiesJson 検証済みの cookies JSON。
     * @return 成功時 `null`、失敗時エラーメッセージ。
     */
    suspend fun writeCookies(cookiesJson: BackupCookiesJson): String? {
        val totalCount = cookiesJson.cookies.size
        var serializeFailed = 0

        val cookieJsonSet = cookiesJson.cookies.mapNotNull { item ->
            try {
                val cookie = BackupRestoreMapper.toCookie(item) ?: run {
                    serializeFailed++
                    return@mapNotNull null
                }
                val json = moshi.adapter(okhttp3.Cookie::class.java).toJson(cookie)
                json
            } catch (e: Exception) {
                serializeFailed++
                logD("writeCookies serialize failed: domain=${item.domain}, name=${item.name}, reason=${e.message}")
                null
            }
        }.toSet()

        if (serializeFailed > 0) {
            return "failed to serialize restored cookies: failed=$serializeFailed total=$totalCount"
        }

        cookiesDataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.COOKIE_KEY] = cookieJsonSet
        }

        logD("writeCookies datastore written: stringSetSize=${cookieJsonSet.size}")
        return null
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
        prefs[SlevoPreferenceDataStores.THEME_MODE_KEY] = settings.themeMode
        prefs[SlevoPreferenceDataStores.TREE_SORT_KEY] = settings.isTreeSort
        prefs[SlevoPreferenceDataStores.THREAD_MINIMAP_SCROLLBAR_KEY] = settings.isThreadMinimapScrollbarEnabled
        prefs[SlevoPreferenceDataStores.TEXT_SCALE_KEY] = settings.textScale
        prefs[SlevoPreferenceDataStores.INDIVIDUAL_TEXT_SCALE_KEY] = settings.isIndividualTextScale
        prefs[SlevoPreferenceDataStores.HEADER_TEXT_SCALE_KEY] = settings.headerTextScale
        prefs[SlevoPreferenceDataStores.BODY_TEXT_SCALE_KEY] = settings.bodyTextScale
        prefs[SlevoPreferenceDataStores.LINE_HEIGHT_KEY] = settings.lineHeight
        prefs[SlevoPreferenceDataStores.REDIRECT_5CH_NET_TO_IO_KEY] = settings.isRedirect5chNetToIoEnabled

        prefs[SlevoPreferenceDataStores.GESTURE_ENABLED_KEY] = settings.gestureSettings.enabled
        prefs[SlevoPreferenceDataStores.GESTURE_SHOW_HINT_KEY] = settings.gestureSettings.showActionHints

        GestureDirection.entries.forEach { direction ->
            val kebab = BackupRestoreMapper.kebabCaseFromPascalCase(direction.name)
            val key = SlevoPreferenceDataStores.GESTURE_ACTION_KEYS.getValue(direction)
            val actionValue = settings.gestureSettings.actions[kebab]
            val actionName = actionValue?.let { toExistingGestureActionNameOrNull(it) }

            // backup に存在しない方向、または未知 action は未割当として保存値を残さない。
            if (actionName == null) {
                prefs.remove(key)
            } else {
                prefs[key] = actionName
            }
        }

        prefs[SlevoPreferenceDataStores.GESTURE_ASSIGNMENTS_INITIALIZED_KEY] = true
    }

    companion object {
        private const val TAG = "PendRestDataStrWrtr"

        /**
         * テストで android.util.Log が使えない場合を考慮した安全ログ。
         */
        private fun logD(message: String) {
            try {
                Log.d(TAG, message)
            } catch (_: RuntimeException) {
                // JVM unit test の Log stub では例外になるため握りつぶす。
            }
        }

        /**
         * kebab-case 文字列を PascalCase へ変換する。
         *
         * 例: `"switch-to-next-tab"` → `"SwitchToNextTab"`
         */
        internal fun kebabToPascalCase(kebab: String): String =
            kebab.split("-").joinToString("") { part ->
                part.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }

        /**
         * 既存 [GestureAction] に存在する action 名のみ PascalCase で返す。
         */
        internal fun toExistingGestureActionNameOrNull(kebab: String): String? {
            val pascal = kebabToPascalCase(kebab)
            return GestureAction.entries.firstOrNull { it.name == pascal }?.name
        }
    }
}
