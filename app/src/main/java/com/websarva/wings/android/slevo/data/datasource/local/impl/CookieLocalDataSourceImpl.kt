package com.websarva.wings.android.slevo.data.datasource.local.impl

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.websarva.wings.android.slevo.data.datasource.local.CookieLocalDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.Cookie
import javax.inject.Inject
import javax.inject.Singleton

// DataStoreのインスタンスを定義
private val Context.cookieDataStore by preferencesDataStore(name = "cookies")
private val COOKIE_KEY = stringSetPreferencesKey("app_cookies")

@Singleton
class CookieLocalDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) : CookieLocalDataSource {

    // CookieオブジェクトをJSONに変換するためのアダプタ
    private val cookieListAdapter = moshi.adapter<List<Cookie>>(
        Types.newParameterizedType(List::class.java, Cookie::class.java)
    )

    override fun getCookies(): Flow<List<Cookie>> {
        return context.cookieDataStore.data.map { preferences ->
            preferences[COOKIE_KEY]?.mapNotNull { json ->
                // JSONからCookieオブジェクトにデシリアライズ
                try {
                    moshi.adapter(Cookie::class.java).fromJson(json)
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
        }
    }

    override suspend fun saveCookies(cookies: List<Cookie>) {
        val cookieJsonSet = cookies.mapNotNull { cookie ->
            // CookieオブジェクトをJSONにシリアライズ
            try {
                moshi.adapter(Cookie::class.java).toJson(cookie)
            } catch (e: Exception) {
                null
            }
        }.toSet()

        context.cookieDataStore.edit { preferences ->
            preferences[COOKIE_KEY] = cookieJsonSet
        }
    }
}
