package com.websarva.wings.android.slevo.data.datasource.local.impl

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.websarva.wings.android.slevo.data.datasource.local.SettingsLocalDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")
private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
private val TREE_SORT_KEY = booleanPreferencesKey("tree_sort")

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
}
