package com.websarva.wings.android.bbsviewer.data.datasource.local

import kotlinx.coroutines.flow.Flow

interface SettingsLocalDataSource {
    /** ダークモード設定を監視する */
    fun observeIsDarkMode(): Flow<Boolean>

    /** ダークモード設定を保存する */
    suspend fun setDarkMode(enabled: Boolean)
}
