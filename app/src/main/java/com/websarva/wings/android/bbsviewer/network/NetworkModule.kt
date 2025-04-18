package com.websarva.wings.android.bbsviewer.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        // ログ出力用インターセプター
        val logging = HttpLoggingInterceptor { message ->
            android.util.Log.d("OkHttp", message)
        }.apply {
            // BODY レベルでヘッダーもボディも丸ごとログ出力
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }
}
