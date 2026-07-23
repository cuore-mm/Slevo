package com.websarva.wings.android.slevo.data.datasource.local.impl

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.websarva.wings.android.slevo.data.datasource.local.CookieLocalDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.Cookie
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [CookieLocalDataSource] の DataStore 実装。
 *
 * DataStore instance は [SlevoPreferenceDataStores.cookies] から取得し、
 * 同一 process 内で DataStore が多重生成されないことを保証する。
 */
@Singleton
class CookieLocalDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) : CookieLocalDataSource {

    private val dataStore get() = SlevoPreferenceDataStores.cookies(context)

    private val cookieListAdapter = moshi.adapter<List<Cookie>>(
        Types.newParameterizedType(List::class.java, Cookie::class.java)
    )

    override fun getCookies(): Flow<List<Cookie>> {
        return dataStore.data.map { preferences ->
            preferences[SlevoPreferenceDataStores.COOKIE_KEY]?.mapNotNull { json ->
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
            try {
                moshi.adapter(Cookie::class.java).toJson(cookie)
            } catch (e: Exception) {
                null
            }
        }.toSet()

        dataStore.edit { preferences ->
            preferences[SlevoPreferenceDataStores.COOKIE_KEY] = cookieJsonSet
        }
    }
}
