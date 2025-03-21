package com.websarva.wings.android.bbsviewer.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://example.com/") // 実際のURLは @Url で指定
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create()) // 文字列用
            .build()
    }

    @Provides
    @Singleton
    fun provideDatService(retrofit: Retrofit): DatService {
        return retrofit.create(DatService::class.java)
    }
}
