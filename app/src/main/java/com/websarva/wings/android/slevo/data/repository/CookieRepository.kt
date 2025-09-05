package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.local.CookieLocalDataSource
import com.websarva.wings.android.slevo.di.PersistentCookieJar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import okhttp3.Cookie

@Singleton
class CookieRepository @Inject constructor(
    private val local: CookieLocalDataSource,
    private val cookieJar: PersistentCookieJar,
) {
    fun observeCookies(): Flow<List<Cookie>> = local.getCookies()

    suspend fun remove(cookie: Cookie) {
        cookieJar.removeCookie(cookie)
    }
}

