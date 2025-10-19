package com.websarva.wings.android.slevo.data.datasource.local.impl

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.websarva.wings.android.slevo.data.datasource.local.SettingsLocalDataSource
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.data.model.GestureSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(
    name = "settings",
    produceMigrations = {
        listOf(
            // 以下はジェスチャー設定に関する古いプリファレンスを新しい形に移行するための DataMigration オブジェクトです。
            // 目的:
            // - 既存ストレージにジェスチャー関連キーが無い場合にデフォルト値を挿入し、アプリケーション側で常に期待できる形に整える。
            // 注意点:
            // - migrate は現在の Preferences を変更せず、新しい Preferences を返す（ここでは toMutablePreferences() を使って編集後に toPreferences() を返す）。
            // - デフォルトで割り当てが存在しない方向はキーを削除することで "未割当" を表現する。
            object : DataMigration<Preferences> {
                // shouldMigrate:
                // 初期化フラグが未設定かつジェスチャー関連のキーが一切存在しない
                // （＝真に未初期化なユーザー）である場合のみ migrate を実行する。
                // これにより、既にジェスチャー設定を持っているユーザー（意図的な削除含む）は
                // マイグレーションで上書きされにくくなります。
                override suspend fun shouldMigrate(currentData: Preferences): Boolean {
                    val notInitialized = currentData[GESTURE_ASSIGNMENTS_INITIALIZED_KEY] != true
                    val hasAnyGestureKey = currentData.contains(GESTURE_ENABLED_KEY)
                        || currentData.contains(GESTURE_SHOW_HINT_KEY)
                        || GestureDirection.entries.any { direction ->
                            val key = GESTURE_ACTION_KEYS.getValue(direction)
                            currentData.contains(key)
                        }
                    // フラグ未設定かつジェスチャー関連キーが一切無い場合のみ migrate
                    return notInitialized && !hasAnyGestureKey
                }

                // migrate:
                // - currentData を元に、欠けているキーに対してデフォルト値を挿入する
                // - 戻り値は新しい Preferences（ここでは mutablePreferences を toPreferences したもの）
                // 実装のポイント:
                // - toMutablePreferences() で編集可能なコピーを作り、必要なキーを追加／削除する
                // - GestureSettings.DEFAULT を参照してデフォルト値を補完する
                // - デフォルトが null の場合はキーを削除して "未設定" を表現する
                override suspend fun migrate(currentData: Preferences): Preferences {
                    val mutablePreferences = currentData.toMutablePreferences()
                    if (!mutablePreferences.contains(GESTURE_ENABLED_KEY)) {
                        // ジェスチャー有効フラグが無ければデフォルトを入れる
                        mutablePreferences[GESTURE_ENABLED_KEY] = GestureSettings.DEFAULT.isEnabled
                    }
                    if (!mutablePreferences.contains(GESTURE_SHOW_HINT_KEY)) {
                        // アクションヒント表示フラグが無ければデフォルトを入れる
                        mutablePreferences[GESTURE_SHOW_HINT_KEY] =
                            GestureSettings.DEFAULT.showActionHints
                    }
                    // 各方向について、キーが無ければデフォルト割当を設定する
                    GestureDirection.entries.forEach { direction ->
                        val key = GESTURE_ACTION_KEYS.getValue(direction)
                        if (!mutablePreferences.contains(key)) {
                            val defaultAction = GestureSettings.DEFAULT.assignments[direction]
                            if (defaultAction == null) {
                                // デフォルト割当が null の場合はキーを完全に削除して未割当を表現
                                mutablePreferences.remove(key)
                            } else {
                                // 存在するアクションは enum 名で保存
                                mutablePreferences[key] = defaultAction.name
                            }
                        }
                    }
                    // 初期化（マイグレーション）を行ったことを示すフラグを立てる
                    mutablePreferences[GESTURE_ASSIGNMENTS_INITIALIZED_KEY] = true
                    // 編集した mutablePreferences を Preferences に変換して返す
                    return mutablePreferences.toPreferences()
                }

                // cleanUp:
                // - DataMigration インタフェースの一部だが、ここでは後片付け不要のため no-op
                // - 必要に応じて一時リソースの削除などを実装可能
                override suspend fun cleanUp() {
                    // No-op
                }
            }
        )
    }
)
private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
private val TREE_SORT_KEY = booleanPreferencesKey("tree_sort")
private val THREAD_MINIMAP_SCROLLBAR_KEY = booleanPreferencesKey("thread_minimap_scrollbar")
private val TEXT_SCALE_KEY = floatPreferencesKey("text_scale")
private val INDIVIDUAL_TEXT_SCALE_KEY = booleanPreferencesKey("individual_text_scale")
private val HEADER_TEXT_SCALE_KEY = floatPreferencesKey("header_text_scale")
private val BODY_TEXT_SCALE_KEY = floatPreferencesKey("body_text_scale")
private val LINE_HEIGHT_KEY = floatPreferencesKey("line_height")
private val GESTURE_ENABLED_KEY = booleanPreferencesKey("gesture_enabled")
private val GESTURE_SHOW_HINT_KEY = booleanPreferencesKey("gesture_show_action_hint")
// 初期化フラグ: 一度でもマイグレーション（または初期化処理）を行ったかを示す
private val GESTURE_ASSIGNMENTS_INITIALIZED_KEY = booleanPreferencesKey("gesture_assignments_initialized")
private val GESTURE_ACTION_KEYS = GestureDirection.entries.associateWith { direction ->
    stringPreferencesKey("gesture_action_${direction.name.lowercase(Locale.ROOT)}")
}

@Singleton
class SettingsLocalDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsLocalDataSource {
    override fun observeIsDarkMode(): Flow<Boolean> =
        context.dataStore.data
            .map { prefs -> prefs[DARK_MODE_KEY] ?: false }

    override suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE_KEY] = enabled
        }
    }

    override fun observeIsTreeSort(): Flow<Boolean> =
        context.dataStore.data
            .map { prefs -> prefs[TREE_SORT_KEY] ?: false }

    override suspend fun setTreeSort(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[TREE_SORT_KEY] = enabled
        }
    }

    override fun observeIsThreadMinimapScrollbarEnabled(): Flow<Boolean> =
        context.dataStore.data
            .map { prefs -> prefs[THREAD_MINIMAP_SCROLLBAR_KEY] ?: true }

    override suspend fun setThreadMinimapScrollbarEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[THREAD_MINIMAP_SCROLLBAR_KEY] = enabled
        }
    }

    override fun observeTextScale(): Flow<Float> =
        context.dataStore.data
            .map { prefs -> prefs[TEXT_SCALE_KEY] ?: 1f }

    override suspend fun setTextScale(scale: Float) {
        context.dataStore.edit { prefs ->
            prefs[TEXT_SCALE_KEY] = scale
        }
    }

    override fun observeIsIndividualTextScale(): Flow<Boolean> =
        context.dataStore.data
            .map { prefs -> prefs[INDIVIDUAL_TEXT_SCALE_KEY] ?: false }

    override suspend fun setIndividualTextScale(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[INDIVIDUAL_TEXT_SCALE_KEY] = enabled
        }
    }

    override fun observeHeaderTextScale(): Flow<Float> =
        context.dataStore.data
            .map { prefs -> prefs[HEADER_TEXT_SCALE_KEY] ?: 0.85f }

    override suspend fun setHeaderTextScale(scale: Float) {
        context.dataStore.edit { prefs ->
            prefs[HEADER_TEXT_SCALE_KEY] = scale
        }
    }

    override fun observeBodyTextScale(): Flow<Float> =
        context.dataStore.data
            .map { prefs -> prefs[BODY_TEXT_SCALE_KEY] ?: 1f }

    override suspend fun setBodyTextScale(scale: Float) {
        context.dataStore.edit { prefs ->
            prefs[BODY_TEXT_SCALE_KEY] = scale
        }
    }

    override fun observeLineHeight(): Flow<Float> =
        context.dataStore.data
            .map { prefs -> prefs[LINE_HEIGHT_KEY] ?: DEFAULT_THREAD_LINE_HEIGHT }

    override suspend fun setLineHeight(height: Float) {
        context.dataStore.edit { prefs ->
            prefs[LINE_HEIGHT_KEY] = height
        }
    }

    override fun observeGestureSettings(): Flow<GestureSettings> =
        context.dataStore.data
            .map { prefs ->
                val isEnabled = prefs[GESTURE_ENABLED_KEY] ?: GestureSettings.DEFAULT.isEnabled
                val showActionHints =
                    prefs[GESTURE_SHOW_HINT_KEY] ?: GestureSettings.DEFAULT.showActionHints
                val assignments = GestureDirection.entries.associateWith { direction ->
                    val key = GESTURE_ACTION_KEYS.getValue(direction)
                    prefs[key]?.let { value ->
                        GestureAction.entries.firstOrNull { it.name == value }
                    }
                }
                GestureSettings(
                    isEnabled = isEnabled,
                    showActionHints = showActionHints,
                    assignments = assignments
                )
            }

    override suspend fun setGestureEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[GESTURE_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setGestureShowActionHints(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[GESTURE_SHOW_HINT_KEY] = show
        }
    }

    override suspend fun setGestureAction(direction: GestureDirection, action: GestureAction?) {
        val key = GESTURE_ACTION_KEYS.getValue(direction)
        context.dataStore.edit { prefs ->
            if (action == null) {
                prefs.remove(key)
            } else {
                prefs[key] = action.name
            }
        }
    }

    override suspend fun resetGestureSettings() {
        context.dataStore.edit { prefs ->
            prefs[GESTURE_ENABLED_KEY] = GestureSettings.DEFAULT.isEnabled
            prefs[GESTURE_SHOW_HINT_KEY] = GestureSettings.DEFAULT.showActionHints
            GestureDirection.entries.forEach { direction ->
                val key = GESTURE_ACTION_KEYS.getValue(direction)
                val defaultAction = GestureSettings.DEFAULT.assignments[direction]
                if (defaultAction == null) {
                    prefs.remove(key)
                } else {
                    prefs[key] = defaultAction.name
                }
            }
        }
    }
}
