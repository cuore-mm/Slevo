package com.websarva.wings.android.slevo.data.datasource.local

import kotlinx.coroutines.flow.Flow

interface TabsLocalDataSource {
    fun observeLastSelectedTabsPage(): Flow<Int>
    suspend fun setLastSelectedTabsPage(page: Int)
}
