package com.websarva.wings.android.slevo.data.datasource.local

import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * 設定値のローカル永続化アクセスを抽象化するデータソース。
 */
interface SettingsLocalDataSource {
    /** テーマモード設定を監視する */
    fun observeThemeMode(): Flow<ThemeMode>

    /** テーマモード設定を保存する */
    suspend fun setThemeMode(mode: ThemeMode)

    /** レスのデフォルト並び順（ツリー順か）を監視する */
    fun observeIsTreeSort(): Flow<Boolean>

    /** レスのデフォルト並び順を保存する */
    suspend fun setTreeSort(enabled: Boolean)

    /** スレッド画面のミニマップ付きスクロールバーを表示するかを監視する */
    fun observeIsThreadMinimapScrollbarEnabled(): Flow<Boolean>

    /** スレッド画面のミニマップ付きスクロールバーを表示するかを保存する */
    suspend fun setThreadMinimapScrollbarEnabled(enabled: Boolean)

    /** レス表示の文字倍率を監視する */
    fun observeTextScale(): Flow<Float>

    /** レス表示の文字倍率を保存する */
    suspend fun setTextScale(scale: Float)

    /** ヘッダーと本文の文字サイズを個別に設定するかどうかを監視する */
    fun observeIsIndividualTextScale(): Flow<Boolean>

    /** ヘッダーと本文の文字サイズを個別に設定するかどうかを保存する */
    suspend fun setIndividualTextScale(enabled: Boolean)

    /** ヘッダー文字サイズの倍率を監視する */
    fun observeHeaderTextScale(): Flow<Float>

    /** ヘッダー文字サイズの倍率を保存する */
    suspend fun setHeaderTextScale(scale: Float)

    /** 本文文字サイズの倍率を監視する */
    fun observeBodyTextScale(): Flow<Float>

    /** 本文文字サイズの倍率を保存する */
    suspend fun setBodyTextScale(scale: Float)

    /** 行間の倍率を監視する */
    fun observeLineHeight(): Flow<Float>

    /** 行間の倍率を保存する */
    suspend fun setLineHeight(height: Float)

    /** 5ch.net を 5ch.io として開く設定を監視する */
    fun observeIsRedirect5chNetToIoEnabled(): Flow<Boolean>

    /** 5ch.net を 5ch.io として開く設定を保存する */
    suspend fun setRedirect5chNetToIoEnabled(enabled: Boolean)

    /** ジェスチャー設定を監視する */
    fun observeGestureSettings(): Flow<GestureSettings>

    /** ジェスチャー機能の有効/無効を保存する */
    suspend fun setGestureEnabled(enabled: Boolean)

    /** ジェスチャーのアクションヒントの表示/非表示を保存する */
    suspend fun setGestureShowActionHints(show: Boolean)

    /** 指定方向のジェスチャーアクションを保存する */
    suspend fun setGestureAction(direction: GestureDirection, action: GestureAction?)

    /** ジェスチャー設定を初期値にリセットする */
    suspend fun resetGestureSettings()
}
