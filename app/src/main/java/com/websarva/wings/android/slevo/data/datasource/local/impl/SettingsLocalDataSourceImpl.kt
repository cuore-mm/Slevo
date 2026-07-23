package com.websarva.wings.android.slevo.data.datasource.local.impl

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.websarva.wings.android.slevo.data.datasource.local.SettingsLocalDataSource
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.ThemeMode
import com.websarva.wings.android.slevo.data.model.TextDisplaySettingsConstraints
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SettingsLocalDataSource] の DataStore 実装。
 *
 * ユーザー設定の永続化と監視を Preference DataStore で提供する。
 * DataStore instance は [SlevoPreferenceDataStores.settings] から取得し、
 * 同一 process 内で DataStore が多重生成されないことを保証する。
 */
@Singleton
class SettingsLocalDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsLocalDataSource {

    private val dataStore get() = SlevoPreferenceDataStores.settings(context)

    override fun observeThemeMode(): Flow<ThemeMode> =
        dataStore.data
            .map { prefs -> ThemeMode.fromStorageValue(prefs[SlevoPreferenceDataStores.THEME_MODE_KEY]) }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.THEME_MODE_KEY] = mode.storageValue
        }
    }

    override fun observeIsTreeSort(): Flow<Boolean> =
        dataStore.data
            .map { prefs -> prefs[SlevoPreferenceDataStores.TREE_SORT_KEY] ?: false }

    override suspend fun setTreeSort(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.TREE_SORT_KEY] = enabled
        }
    }

    override fun observeIsThreadMinimapScrollbarEnabled(): Flow<Boolean> =
        dataStore.data
            .map { prefs -> prefs[SlevoPreferenceDataStores.THREAD_MINIMAP_SCROLLBAR_KEY] ?: true }

    override suspend fun setThreadMinimapScrollbarEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.THREAD_MINIMAP_SCROLLBAR_KEY] = enabled
        }
    }

    override fun observeTextScale(): Flow<Float> =
        dataStore.data
            .map {
                prefs -> prefs[SlevoPreferenceDataStores.TEXT_SCALE_KEY]
                    ?: TextDisplaySettingsConstraints.DEFAULT_TEXT_SCALE
            }

    override suspend fun setTextScale(scale: Float) {
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.TEXT_SCALE_KEY] = scale
        }
    }

    override fun observeIsIndividualTextScale(): Flow<Boolean> =
        dataStore.data
            .map { prefs -> prefs[SlevoPreferenceDataStores.INDIVIDUAL_TEXT_SCALE_KEY] ?: false }

    override suspend fun setIndividualTextScale(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.INDIVIDUAL_TEXT_SCALE_KEY] = enabled
        }
    }

    override fun observeHeaderTextScale(): Flow<Float> =
        dataStore.data
            .map {
                prefs -> prefs[SlevoPreferenceDataStores.HEADER_TEXT_SCALE_KEY]
                    ?: TextDisplaySettingsConstraints.DEFAULT_HEADER_TEXT_SCALE
            }

    override suspend fun setHeaderTextScale(scale: Float) {
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.HEADER_TEXT_SCALE_KEY] = scale
        }
    }

    override fun observeBodyTextScale(): Flow<Float> =
        dataStore.data
            .map {
                prefs -> prefs[SlevoPreferenceDataStores.BODY_TEXT_SCALE_KEY]
                    ?: TextDisplaySettingsConstraints.DEFAULT_BODY_TEXT_SCALE
            }

    override suspend fun setBodyTextScale(scale: Float) {
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.BODY_TEXT_SCALE_KEY] = scale
        }
    }

    override fun observeLineHeight(): Flow<Float> =
        dataStore.data
            .map {
                prefs -> prefs[SlevoPreferenceDataStores.LINE_HEIGHT_KEY]
                    ?: TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT
            }

    override suspend fun setLineHeight(height: Float) {
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.LINE_HEIGHT_KEY] = height
        }
    }

    override fun observeIsRedirect5chNetToIoEnabled(): Flow<Boolean> =
        dataStore.data
            .map { prefs -> prefs[SlevoPreferenceDataStores.REDIRECT_5CH_NET_TO_IO_KEY] ?: true }

    override suspend fun getIsRedirect5chNetToIoEnabled(): Boolean {
        val prefs = dataStore.data.first()
        return prefs[SlevoPreferenceDataStores.REDIRECT_5CH_NET_TO_IO_KEY] ?: true
    }

    override suspend fun setRedirect5chNetToIoEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.REDIRECT_5CH_NET_TO_IO_KEY] = enabled
        }
    }

    override fun observeGestureSettings(): Flow<GestureSettings> =
        dataStore.data
            .map { prefs ->
                val isEnabled = prefs[SlevoPreferenceDataStores.GESTURE_ENABLED_KEY] ?: GestureSettings.DEFAULT.isEnabled
                val showActionHints =
                    prefs[SlevoPreferenceDataStores.GESTURE_SHOW_HINT_KEY] ?: GestureSettings.DEFAULT.showActionHints
                val assignments = GestureDirection.entries.associateWith { direction ->
                    val key = SlevoPreferenceDataStores.GESTURE_ACTION_KEYS.getValue(direction)
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
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.GESTURE_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setGestureShowActionHints(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.GESTURE_SHOW_HINT_KEY] = show
        }
    }

    override suspend fun setGestureAction(direction: GestureDirection, action: GestureAction?) {
        val key = SlevoPreferenceDataStores.GESTURE_ACTION_KEYS.getValue(direction)
        dataStore.edit { prefs ->
            if (action == null) {
                prefs.remove(key)
            } else {
                prefs[key] = action.name
            }
        }
    }

    override suspend fun resetGestureSettings() {
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.GESTURE_ENABLED_KEY] = GestureSettings.DEFAULT.isEnabled
            prefs[SlevoPreferenceDataStores.GESTURE_SHOW_HINT_KEY] = GestureSettings.DEFAULT.showActionHints
            GestureDirection.entries.forEach { direction ->
                val key = SlevoPreferenceDataStores.GESTURE_ACTION_KEYS.getValue(direction)
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
