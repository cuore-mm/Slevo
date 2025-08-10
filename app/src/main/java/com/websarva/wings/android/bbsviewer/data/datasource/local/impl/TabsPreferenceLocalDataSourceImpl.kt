package com.websarva.wings.android.bbsviewer.data.datasource.local.impl

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.websarva.wings.android.bbsviewer.data.datasource.local.TabsPreferenceLocalDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "tabs")
private val LAST_TAB_PAGE_KEY = intPreferencesKey("last_tab_page")

@Singleton
class TabsPreferenceLocalDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TabsPreferenceLocalDataSource {
    override fun observeLastTabPage(): Flow<Int> =
        context.dataStore.data.map { prefs -> prefs[LAST_TAB_PAGE_KEY] ?: 0 }

    override suspend fun setLastTabPage(page: Int) {
        context.dataStore.edit { prefs ->
            prefs[LAST_TAB_PAGE_KEY] = page
        }
    }
}
