package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.datasource.local.SettingsLocalDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val local: SettingsLocalDataSource
) {
    fun observeIsDarkMode(): Flow<Boolean> =
        local.observeIsDarkMode()

    suspend fun setDarkMode(enabled: Boolean) =
        local.setDarkMode(enabled)

    fun observeIsTreeSort(): Flow<Boolean> =
        local.observeIsTreeSort()

    suspend fun setTreeSort(enabled: Boolean) =
        local.setTreeSort(enabled)
}
