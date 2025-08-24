package com.websarva.wings.android.slevo.data.datasource.local.impl

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.websarva.wings.android.slevo.data.datasource.local.TabsLocalDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tabsDataStore by preferencesDataStore(name = "tabs")
private val LAST_PAGE_KEY = intPreferencesKey("last_selected_page")

@Singleton
class TabsLocalDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TabsLocalDataSource {
    override fun observeLastSelectedPage(): Flow<Int> =
        context.tabsDataStore.data.map { prefs -> prefs[LAST_PAGE_KEY] ?: 0 }

    override suspend fun setLastSelectedPage(page: Int) {
        context.tabsDataStore.edit { prefs ->
            prefs[LAST_PAGE_KEY] = page
        }
    }
}

