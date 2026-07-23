package com.websarva.wings.android.slevo.data.backup

import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cookie

/**
 * 通常実行時と startup restore で共有する Moshi 構成を提供する factory。
 *
 * [CookieJsonAdapter] と [KotlinJsonAdapterFactory] を組み込み、
 * `okhttp3.Cookie` のシリアライズ/デシリアライズが
 * [CookieLocalDataSourceImpl] と [PendingRestoreDataStoreWriter] で
 * 同一の pipe-delimited 形式になることを保証する。
 */
object BackupMoshiFactory {
    /**
     * Cookie adapter を含む共有 Moshi インスタンスを生成する。
     *
     * @return Cookie serialization 対応の Moshi。
     */
    fun create(): Moshi {
        return Moshi.Builder()
            .add(CookieJsonAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
    }
}

/**
 * OkHttp の [Cookie] と pipe-delimited 文字列を相互変換する Moshi adapter。
 *
 * 形式: `"name|value|expiresAt|domain|path|secure|httpOnly"`
 *
 * `CookieLocalDataSourceImpl` と [PendingRestoreDataStoreWriter] の
 * 両方で統一した形式で保存するために使う。
 */
class CookieJsonAdapter {
    @ToJson
    fun toJson(cookie: Cookie): String {
        return "${cookie.name}|${cookie.value}|${cookie.expiresAt}|${cookie.domain}|${cookie.path}|${cookie.secure}|${cookie.httpOnly}"
    }

    @FromJson
    fun fromJson(json: String): Cookie? {
        val parts = json.split("|")
        return try {
            Cookie.Builder()
                .name(parts[0])
                .value(parts[1])
                .expiresAt(parts[2].toLong())
                .domain(parts[3])
                .path(parts[4])
                .apply {
                    if (parts[5].toBoolean()) secure()
                    if (parts[6].toBoolean()) httpOnly()
                }
                .build()
        } catch (e: Exception) {
            null
        }
    }
}
