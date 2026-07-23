package com.websarva.wings.android.slevo.data.datasource.local.impl

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.websarva.wings.android.slevo.data.datasource.local.TabsLocalDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [TabsLocalDataSource] の DataStore 実装。
 *
 * DataStore instance は [SlevoPreferenceDataStores.tabs] から取得し、
 * 同一 process 内で DataStore が多重生成されないことを保証する。
 */
@Singleton
class TabsLocalDataSourceImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : TabsLocalDataSource {

    private val dataStore get() = SlevoPreferenceDataStores.tabs(context)

    override fun observeLastSelectedTabsPage(): Flow<Int> =
        dataStore.data.map { prefs -> prefs[SlevoPreferenceDataStores.LAST_PAGE_KEY] ?: 0 }

    override suspend fun setLastSelectedTabsPage(page: Int) {
        dataStore.edit { prefs ->
            prefs[SlevoPreferenceDataStores.LAST_PAGE_KEY] = page
        }
    }
}
