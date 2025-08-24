package com.websarva.wings.android.slevo.data.datasource.local

import kotlinx.coroutines.flow.Flow

interface TabsLocalDataSource {
    fun observeLastSelectedPage(): Flow<Int>
    suspend fun setLastSelectedPage(page: Int)
}

