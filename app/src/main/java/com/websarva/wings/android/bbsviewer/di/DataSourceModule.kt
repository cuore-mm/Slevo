package com.websarva.wings.android.bbsviewer.di

import com.websarva.wings.android.bbsviewer.data.datasource.local.AppDatabase
import com.websarva.wings.android.bbsviewer.data.datasource.local.BbsLocalDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.CategoryDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.impl.BbsLocalDataSourceImpl
import com.websarva.wings.android.bbsviewer.data.datasource.remote.BbsMenuDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.remote.impl.BbsMenuDataSourceImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt モジュール：DataSource の依存性を提供
 *
 * - ネットワーク用 DataSource (BbsMenuDataSource)
 * - ローカル DB 用 DataSource (BbsLocalDataSource)
 */
@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    /**
     * ネットワークから BBS メニューを取得する DataSource 実装を提供
     *
     * @param client HTTP リクエスト実行に使用する OkHttpClient
     * @return BbsMenuDataSourceImpl のインスタンス
     */
    @Provides
    fun provideBbsMenuDataSource(
        client: OkHttpClient
    ): BbsMenuDataSource = BbsMenuDataSourceImpl(client)

    /**
     * Room データベース操作用のローカル DataSource 実装を提供
     *
     * @param database AppDatabase のインスタンス
     * @param serviceDao BBS サービス情報を操作する DAO
     * @param categoryDao カテゴリ情報を操作する DAO
     * @param boardDao ボード情報を操作する DAO
     * @return BbsLocalDataSourceImpl のシングルトンインスタンス
     */
    @Provides
    @Singleton
    fun provideBbsLocalDataSource(
        database: AppDatabase,
        serviceDao: BbsServiceDao,
        categoryDao: CategoryDao,
        boardDao: BoardDao
    ): BbsLocalDataSource = BbsLocalDataSourceImpl(
        database    = database,
        serviceDao  = serviceDao,
        categoryDao = categoryDao,
        boardDao    = boardDao
    )
}
