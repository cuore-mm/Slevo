package com.websarva.wings.android.bbsviewer.data.datasource.local

import kotlinx.coroutines.flow.Flow

interface TabsLocalDataSource {
    fun observeLastSelectedPage(): Flow<Int>
    suspend fun setLastSelectedPage(page: Int)
}

