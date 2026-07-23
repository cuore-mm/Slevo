package com.websarva.wings.android.slevo.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Hiltへアプリ共通のblocking I/O dispatcherを提供する。
 *
 * ViewModelのfile I/OをMain dispatcherから分離し、unit testではvirtual dispatcherへ差し替えられる。
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutineDispatcherModule {
    /** pending restore resultの読み書きに使うIO dispatcherを返す。 */
    @Provides
    @Singleton
    @Named("pendingRestoreIo")
    fun providePendingRestoreIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
