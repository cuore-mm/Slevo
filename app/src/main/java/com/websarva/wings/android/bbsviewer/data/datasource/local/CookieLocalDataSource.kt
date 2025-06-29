package com.websarva.wings.android.bbsviewer.data.datasource.local

import okhttp3.Cookie
import kotlinx.coroutines.flow.Flow

/**
 * クッキーを永続化するためのローカルデータソース
 */
interface CookieLocalDataSource {
    /** 保存されているすべてのクッキーを取得する */
    fun getCookies(): Flow<List<Cookie>>

    /** クッキーのリストを保存する */
    suspend fun saveCookies(cookies: List<Cookie>)
}
