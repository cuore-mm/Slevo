package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.squareup.moshi.Moshi
import com.websarva.wings.android.slevo.data.backup.restore.BackupRestoreMapper
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import com.websarva.wings.android.slevo.data.datasource.local.impl.SlevoPreferenceDataStores
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import kotlinx.coroutines.flow.first
import okhttp3.Cookie
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
     * [prepareCookies] + [writePreparedCookies] の wrapper として実装される。
     * conversion/serialization に失敗した Cookie が 1 件以上ある場合はエラーメッセージを返す。
     *
     * @param cookiesJson 検証済みの cookies JSON。
     * @return 成功時 `null`、失敗時エラーメッセージ。
     */
    suspend fun writeCookies(cookiesJson: BackupCookiesJson): String? {
        return when (val prepared = prepareCookies(cookiesJson)) {
            is PreparedCookies.Success -> {
                writePreparedCookies(prepared.cookieJsonSet)
                null
            }
            is PreparedCookies.Failure -> prepared.message
        }
    }

    /**
     * Cookie 復元データを DataStore 書き込み前に全件検証・serialize する。
     *
     * DataStore に触れない pure helper。1 件でも conversion または serialize に失敗した場合は
     * [PreparedCookies.Failure] を返す。データ変換がすべて成功した場合のみ [PreparedCookies.Success] を返す。
     *
     * @param cookiesJson 検証済みの cookies JSON。
     * @return 成功時は [PreparedCookies.Success]、失敗時は [PreparedCookies.Failure]。
     */
    fun prepareCookies(cookiesJson: BackupCookiesJson): PreparedCookies {
        val totalCount = cookiesJson.cookies.size
        var serializeFailed = 0

        val cookieJsonSet = cookiesJson.cookies.mapNotNull { item ->
            try {
                val cookie = BackupRestoreMapper.toCookie(item) ?: run {
                    serializeFailed++
                    return@mapNotNull null
                }
                val json = moshi.adapter(Cookie::class.java).toJson(cookie)
                json
            } catch (e: Exception) {
                serializeFailed++
                logD("prepareCookies serialize failed: domain=${item.domain}, name=${item.name}, reason=${e.message}")
                null
            }
        }.toSet()

        return if (serializeFailed > 0) {
            PreparedCookies.Failure("failed to serialize restored cookies: failed=$serializeFailed total=$totalCount")
        } else {
            PreparedCookies.Success(cookieJsonSet)
        }
    }

    /**
     * 事前検証済みの Cookie JSON set を cookies DataStore に書き込む。
     *
     * conversion / serialization は行わず、受け取った set をそのまま DataStore へ保存する。
     * DataStore I/O exception は catch せず caller へ伝播させる。
     *
     * @param cookieJsonSet 事前検証済みの Cookie JSON string set。
     */
    suspend fun writePreparedCookies(cookieJsonSet: Set<String>) {
        cookiesDataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.COOKIE_KEY] = cookieJsonSet
        }
        logD("writePreparedCookies datastore written: stringSetSize=${cookieJsonSet.size}")
    }

    // --- DataStore snapshot / rollback ---

    /**
     * DataStore snapshot for rollback on restore write failure.
     *
     * settings と tabs は常に snapshot、cookies は restore 対象時のみ [snapshotDataStores]
     * へ渡す `includeCookies` によって snapshot するかどうかを決める。
     *
     * @property settings settings DataStore の snapshot。
     * @property tabs tabs DataStore の snapshot。
     * @property cookies cookies DataStore の snapshot。cookies restore 対象外の場合は `null`。
     */
    data class DataStoreSnapshot(
        val settings: Preferences,
        val tabs: Preferences,
        val cookies: Preferences?,
    )

    /**
     * 通常実行時の DataStore 現在値 snapshot を取得する。
     *
     * DataStore write 前に呼ぶことで、write 失敗時およびprocess death後の rollback source として使う。
     * callerは返却値を [PendingRestoreDataStoreSnapshotStore] へatomicに永続化してからwriteを開始する。
     * [includeCookies] が `false` の場合、cookies snapshot は取得しない。
     *
     * [com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreApplier.RealPendingRestoreDataStoreReflector.reflect]
     * から cookie restore 対象フラグに応じて `preparedCookies != null` で呼ばれる。
     *
     * @param includeCookies cookies DataStore も snapshot するか。
     * @return 現在値の [DataStoreSnapshot]。
     */
    suspend fun snapshotDataStores(includeCookies: Boolean): DataStoreSnapshot {
        val settingsSnapshot = settingsDataStore.data.first()
        val tabsSnapshot = tabsDataStore.data.first()
        val cookiesSnapshot = if (includeCookies) cookiesDataStore.data.first() else null
        return DataStoreSnapshot(settingsSnapshot, tabsSnapshot, cookiesSnapshot)
    }

    /**
     * 対象 DataStore を snapshot 状態へ best-effort で巻き戻す。
     *
     * 各 `restore*` flag が `true` の store のみ restore する。
     * この function 内の exception は caller へそのまま伝播させるため、
     * 呼び出し側で try/catch して diagnostic/log として扱うこと。
     *
     * @param snapshot restore 元の snapshot。
     * @param restoreSettings settings DataStore を restore するか。
     * @param restoreTabs tabs DataStore を restore するか。
     * @param restoreCookies cookies DataStore を restore するか。
     */
    suspend fun restoreDataStores(
        snapshot: DataStoreSnapshot,
        restoreSettings: Boolean,
        restoreTabs: Boolean,
        restoreCookies: Boolean,
    ) {
        if (restoreSettings) restorePreferences(settingsDataStore, snapshot.settings)
        if (restoreTabs) restorePreferences(tabsDataStore, snapshot.tabs)
        if (restoreCookies && snapshot.cookies != null) {
            restorePreferences(cookiesDataStore, snapshot.cookies!!)
        }
    }

    /**
     * DataStore の現在の Preferences を snapshot で full overwrite する。
     *
     * `prefs.clear()` 後に snapshot の全 key/value を書き戻す。
     * [MutablePreferences] への unchecked cast は snapshot を元の DataStore
     * へ戻す用途に限定している。
     *
     * @param store restore 対象の DataStore。
     * @param snapshot 書き戻す Preferences snapshot。
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun restorePreferences(
        store: DataStore<Preferences>,
        snapshot: Preferences,
    ) {
        store.edit { mutablePrefs ->
            restoreToMutablePreferences(mutablePrefs, snapshot)
        }
    }

    /**
     * [MutablePreferences] を全削除し、snapshot の key/value を書き戻す。
     *
     * テストから直接呼び出せるよう internal として公開する。
     * unchecked cast をこの helper に閉じ込め、呼び出し側は DataStore の
     * `edit {}` block または mutablePreferencesOf 経由で渡せばよい。
     *
     * @param target 復元先の [MutablePreferences]。
     * @param snapshot 書き戻す Preferences snapshot。
     */
    @Suppress("UNCHECKED_CAST")
    internal fun restoreToMutablePreferences(
        target: MutablePreferences,
        snapshot: Preferences,
    ) {
        target.clear()
        snapshot.asMap().forEach { (key, value) ->
            target[key as Preferences.Key<Any>] = value
        }
    }

    /**
     * Cookie pre-validation の結果型。
     *
     * - [Success]: 全 Cookie conversion / serialization が成功し、DataStore 保存用 set が確定済み。
     * - [Failure]: 1 件以上の Cookie で conversion または serialization に失敗し、error message を保持する。
     */
    sealed class PreparedCookies {
        /** 全 Cookie conversion / serialization 成功。 */
        data class Success(val cookieJsonSet: Set<String>) : PreparedCookies()
        /** 1 件以上の failure。 */
        data class Failure(val message: String) : PreparedCookies()
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
