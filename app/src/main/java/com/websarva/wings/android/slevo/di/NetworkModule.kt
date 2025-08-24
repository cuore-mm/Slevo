package com.websarva.wings.android.slevo.di

import android.content.Context
import android.content.pm.PackageManager
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // Moshiのインスタンスを提供
    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            // OkHttpのCookieクラスをシリアライズ/デシリアライズするためのアダプタを追加
            .add(CookieJsonAdapter())
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        cookieJar: PersistentCookieJar
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor { message ->
            android.util.Log.d("OkHttp", message)
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // キャッシュディレクトリとサイズを設定
        val cacheSize = 10 * 1024 * 1024L // 10 MB
        val cacheDirectory = File(context.cacheDir, "http-cache")
        val cache = Cache(cacheDirectory, cacheSize)

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .cache(cache) // キャッシュを設定
            .cookieJar(cookieJar) // ★ ここでCookieJarをセット
            .build()
    }

    @Provides
    @Singleton
    @Named("VersionName")
    fun provideVersionName(@ApplicationContext context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0" // 取得失敗時のフォールバック
        }
    }

    @Provides
    @Singleton
    @Named("UserAgent")
    fun provideUserAgent(@Named("VersionName") versionName: String): String {
        return "Monazilla/1.00 (Slevo/$versionName)"
    }
}

// CookieをMoshiで扱うためのカスタムアダプタ
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
