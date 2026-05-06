package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.local.SettingsLocalDataSource
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * アプリ設定の読み書きを仲介するリポジトリ。
 *
 * UI層はこのリポジトリ経由で DataStore 由来の設定状態を購読・更新する。
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val local: SettingsLocalDataSource
) {
    fun observeThemeMode(): Flow<ThemeMode> =
        local.observeThemeMode()

    suspend fun setThemeMode(mode: ThemeMode) =
        local.setThemeMode(mode)

    fun observeIsTreeSort(): Flow<Boolean> =
        local.observeIsTreeSort()

    suspend fun setTreeSort(enabled: Boolean) =
        local.setTreeSort(enabled)

    fun observeIsThreadMinimapScrollbarEnabled(): Flow<Boolean> =
        local.observeIsThreadMinimapScrollbarEnabled()

    suspend fun setThreadMinimapScrollbarEnabled(enabled: Boolean) =
        local.setThreadMinimapScrollbarEnabled(enabled)

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

    fun observeLineHeight(): Flow<Float> =
        local.observeLineHeight()

    suspend fun setLineHeight(height: Float) =
        local.setLineHeight(height)

    /**
     * 5ch.net を 5ch.io として開く設定を監視する。
     */
    fun observeIsRedirect5chNetToIoEnabled(): Flow<Boolean> =
        local.observeIsRedirect5chNetToIoEnabled()

    /**
     * 5ch.net を 5ch.io として開く設定を保存する。
     */
    suspend fun setRedirect5chNetToIoEnabled(enabled: Boolean) =
        local.setRedirect5chNetToIoEnabled(enabled)

    fun observeGestureSettings(): Flow<GestureSettings> =
        local.observeGestureSettings()

    suspend fun setGestureEnabled(enabled: Boolean) =
        local.setGestureEnabled(enabled)

    suspend fun setGestureShowActionHints(show: Boolean) =
        local.setGestureShowActionHints(show)

    suspend fun setGestureAction(direction: GestureDirection, action: GestureAction?) =
        local.setGestureAction(direction, action)

    suspend fun resetGestureSettings() =
        local.resetGestureSettings()
}
