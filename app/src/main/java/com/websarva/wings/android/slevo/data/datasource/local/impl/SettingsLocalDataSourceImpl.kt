package com.websarva.wings.android.slevo.data.datasource.local.impl

import android.content.Context
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

private val Context.dataStore by preferencesDataStore(name = "settings")
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
}
