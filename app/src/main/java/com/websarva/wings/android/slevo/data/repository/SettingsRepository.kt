package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.local.SettingsLocalDataSource
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

    fun observeTextScale(): Flow<Float> =
        local.observeTextScale()

    suspend fun setTextScale(scale: Float) =
        local.setTextScale(scale)

    fun observeIsIndividualTextScale(): Flow<Boolean> =
        local.observeIsIndividualTextScale()

    suspend fun setIndividualTextScale(enabled: Boolean) =
        local.setIndividualTextScale(enabled)

    fun observeHeaderTextScale(): Flow<Float> =
        local.observeHeaderTextScale()

    suspend fun setHeaderTextScale(scale: Float) =
        local.setHeaderTextScale(scale)

    fun observeBodyTextScale(): Flow<Float> =
        local.observeBodyTextScale()

    suspend fun setBodyTextScale(scale: Float) =
        local.setBodyTextScale(scale)
}
