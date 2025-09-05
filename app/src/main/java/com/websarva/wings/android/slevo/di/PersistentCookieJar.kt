package com.websarva.wings.android.slevo.di

import com.websarva.wings.android.slevo.data.datasource.local.CookieLocalDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersistentCookieJar @Inject constructor(
    private val localDataSource: CookieLocalDataSource
) : CookieJar {

    private val cache = ConcurrentHashMap<String, List<Cookie>>()
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        // 初期化時にDataStoreからクッキーを読み込む
        val cookies = runBlocking { localDataSource.getCookies().first() }
        cookies.groupBy { it.domain }.forEach { (domain, cookieList) ->
            cache[domain] = cookieList
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val acceptedCookies = mutableListOf<Cookie>()
        val expiredCookies = mutableListOf<Cookie>()

        // URLにマッチするドメインのクッキーを探す
        cache.keys.filter { url.host.endsWith(it) }.forEach { domain ->
            cache[domain]?.forEach { cookie ->
                if (cookie.expiresAt < now) {
                    expiredCookies.add(cookie)
                } else if (cookie.matches(url)) {
                    acceptedCookies.add(cookie)
                }
            }
        }

        // 期限切れのクッキーをキャッシュから削除
        if (expiredCookies.isNotEmpty()) {
            val allCookies = cache.values.flatten().toMutableList()
            allCookies.removeAll(expiredCookies)
            scope.launch { localDataSource.saveCookies(allCookies) }
        }

        return acceptedCookies
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val domain = url.host
        val currentCookies = cache[domain]?.toMutableList() ?: mutableListOf()

        cookies.forEach { newCookie ->
            // 既存のクッキーを新しいもので上書き
            currentCookies.removeAll { it.name == newCookie.name }
            currentCookies.add(newCookie)
        }
        cache[domain] = currentCookies

        // DataStoreに保存
        scope.launch {
            localDataSource.saveCookies(cache.values.flatten())
        }
    }

    /**
     * 指定したクッキーを削除する
     */
    fun removeCookie(target: Cookie) {
        val domain = target.domain
        val current = cache[domain]?.toMutableList() ?: return
        current.removeAll { it.name == target.name && it.path == target.path }
        if (current.isEmpty()) {
            cache.remove(domain)
        } else {
            cache[domain] = current
        }
        scope.launch {
            localDataSource.saveCookies(cache.values.flatten())
        }
    }

    /**
     * 指定したホストに関連するすべてのクッキーを削除する
     */
    fun clear(host: String) {
        val targets = cache.keys.filter { host.endsWith(it) }
        targets.forEach { cache.remove(it) }
        scope.launch {
            localDataSource.saveCookies(cache.values.flatten())
        }
    }

    /**
     * 指定したホストの MonaTicket クッキーのみを削除する
     */
    fun clearMonaTicket(host: String) {
        val targets = cache.keys.filter { host.endsWith(it) }
        targets.forEach { domain ->
            val remaining = cache[domain]?.filterNot { it.name == "MonaTicket" }
            if (remaining.isNullOrEmpty()) {
                cache.remove(domain)
            } else {
                cache[domain] = remaining
            }
        }
        scope.launch {
            localDataSource.saveCookies(cache.values.flatten())
        }
    }
}
