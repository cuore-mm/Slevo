package com.websarva.wings.android.slevo.ui.common

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 画像読み込み状態の共通ストアを提供する Hilt モジュール。
 */
@Module
@InstallIn(SingletonComponent::class)
object ImageLoadCoordinatorStore {
    /**
     * 画像読み込み状態を画面間で共有するためのシングルトン。
     */
    @Provides
    @Singleton
    fun provideImageLoadCoordinator(): ImageLoadCoordinator {
        return ImageLoadCoordinator()
    }
}
