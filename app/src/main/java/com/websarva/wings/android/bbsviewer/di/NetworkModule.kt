package com.websarva.wings.android.bbsviewer.di

import android.content.Context
import android.content.pm.PackageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient { // ApplicationContextをインジェクト
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
        return "Monazilla/1.00 (BBSViewer/$versionName)"
    }
}
