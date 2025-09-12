package com.websarva.wings.android.slevo.data.datasource.local

import kotlinx.coroutines.flow.Flow

interface SettingsLocalDataSource {
    /** ダークモード設定を監視する */
    fun observeIsDarkMode(): Flow<Boolean>

    /** ダークモード設定を保存する */
    suspend fun setDarkMode(enabled: Boolean)

    /** レスのデフォルト並び順（ツリー順か）を監視する */
    fun observeIsTreeSort(): Flow<Boolean>

    /** レスのデフォルト並び順を保存する */
    suspend fun setTreeSort(enabled: Boolean)

    /** レス本文の文字倍率を監視する */
    fun observePostTextScale(): Flow<Float>

    /** レス本文の文字倍率を保存する */
    suspend fun setPostTextScale(scale: Float)
}
