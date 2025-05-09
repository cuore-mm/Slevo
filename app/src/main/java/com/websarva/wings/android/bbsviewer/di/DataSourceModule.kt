package com.websarva.wings.android.bbsviewer.di

import com.websarva.wings.android.bbsviewer.data.datasource.local.BbsLocalDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.local.BoardRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.local.SettingsLocalDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.local.impl.BbsLocalDataSourceImpl
import com.websarva.wings.android.bbsviewer.data.datasource.local.impl.BoardRemoteDataSourceImpl
import com.websarva.wings.android.bbsviewer.data.datasource.local.impl.SettingsLocalDataSourceImpl
import com.websarva.wings.android.bbsviewer.data.datasource.remote.BbsMenuDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.remote.impl.BbsMenuDataSourceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt モジュール：DataSource の依存性を提供
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataSourceModule {

    /** BBS メニュー取得用 */
    @Binds
    @Singleton
    abstract fun bindBbsMenuDataSource(
        impl: BbsMenuDataSourceImpl
    ): BbsMenuDataSource

    /** ローカル DB 操作用 */
    @Binds
    @Singleton
    abstract fun bindBbsLocalDataSource(
        impl: BbsLocalDataSourceImpl
    ): BbsLocalDataSource

    /** 設定保存用 */
    @Binds
    @Singleton
    abstract fun bindSettingsLocalDataSource(
        impl: SettingsLocalDataSourceImpl
    ): SettingsLocalDataSource

    /** スレッド一覧取得用 */
    @Binds
    @Singleton
    abstract fun bindBoardRemoteDataSource(
        impl: BoardRemoteDataSourceImpl
    ): BoardRemoteDataSource
}

