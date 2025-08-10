package com.websarva.wings.android.bbsviewer.data.datasource.local

import kotlinx.coroutines.flow.Flow

interface TabsPreferenceLocalDataSource {
    fun observeLastTabPage(): Flow<Int>
    suspend fun setLastTabPage(page: Int)
}
