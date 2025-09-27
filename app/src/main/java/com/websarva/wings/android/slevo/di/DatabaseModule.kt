package com.websarva.wings.android.slevo.di

import android.content.Context
import androidx.room.Room
import com.websarva.wings.android.slevo.BuildConfig
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.local.dao.NgDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenBoardTabDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenThreadTabDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BbsServiceDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BoardCategoryCrossRefDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BoardDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.CategoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bookmark.BoardBookmarkGroupDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bookmark.BookmarkBoardDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bookmark.BookmarkThreadDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bookmark.ThreadBookmarkGroupDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.cache.BoardFetchMetaDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.cache.BoardVisitDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.cache.ThreadSummaryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostIdentityHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt モジュール：Room データベースおよび DAO を提供する
 *
 * - AppDatabase の生成
 * - 各種 DAO の依存性注入
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Room の AppDatabase インスタンスをシングルトンとして提供
     *
     * @param context アプリケーションコンテキスト
     * @return AppDatabase のシングルトンインスタンス
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        callback: DatabaseCallback
    ): AppDatabase {
        val name = if (BuildConfig.DEBUG) "slevo_dev_database" else "slevo_database"
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            name
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2, // v.1.1.0 で追加
                AppDatabase.MIGRATION_2_3, // v.1.1.3 で追加
                AppDatabase.MIGRATION_3_4 // v.?.?.? で追加
            )
            .addCallback(callback)
            .apply {
                if (BuildConfig.DEBUG) {
                    fallbackToDestructiveMigrationOnDowngrade(true)
                }
            }
            .build()
    }

    /**
     * BookmarkThreadDao を提供
     *
     * @param db AppDatabase のインスタンス
     * @return BookmarkThreadDao
     */
    @Provides
    fun provideBookmarkThreadDao(
        db: AppDatabase
    ): BookmarkThreadDao = db.bookmarkThreadDao()

    /**
     * BbsServiceDao を提供
     *
     * @param db AppDatabase のインスタンス
     * @return BbsServiceDao
     */
    @Provides
    fun provideBbsServiceDao(
        db: AppDatabase
    ): BbsServiceDao = db.bbsServiceDao()

    /**
     * CategoryDao を提供
     *
     * @param db AppDatabase のインスタンス
     * @return CategoryDao
     */
    @Provides
    fun provideCategoryDao(
        db: AppDatabase
    ): CategoryDao = db.categoryDao()

    /**
     * BoardDao を提供
     *
     * @param db AppDatabase のインスタンス
     * @return BoardDao
     */
    @Provides
    fun provideBoardDao(
        db: AppDatabase
    ): BoardDao = db.boardDao()

    @Provides
    fun provideBookmarkBoardDao(db: AppDatabase): BookmarkBoardDao =
        db.bookmarkBoardDao()

    @Provides
    fun provideBoardCategoryCrossRefDao(db: AppDatabase): BoardCategoryCrossRefDao =
        db.boardCategoryCrossRefDao()

    @Provides
    fun provideBoardGroupDao(db: AppDatabase): BoardBookmarkGroupDao =
        db.boardGroupDao()

    @Provides
    fun provideThreadBookmarkGroupDao(db: AppDatabase): ThreadBookmarkGroupDao =
        db.threadBookmarkGroupDao()

    @Provides
    fun provideOpenBoardTabDao(db: AppDatabase): OpenBoardTabDao =
        db.openBoardTabDao()

    @Provides
    fun provideOpenThreadTabDao(db: AppDatabase): OpenThreadTabDao =
        db.openThreadTabDao()

    @Provides
    fun provideThreadHistoryDao(db: AppDatabase): ThreadHistoryDao =
        db.threadHistoryDao()

    @Provides
    fun provideNgDao(db: AppDatabase): NgDao =
        db.ngDao()

    @Provides
    fun provideThreadSummaryDao(db: AppDatabase): ThreadSummaryDao =
        db.threadSummaryDao()

    @Provides
    fun provideBoardVisitDao(db: AppDatabase): BoardVisitDao =
        db.boardVisitDao()

    @Provides
    fun provideBoardFetchMetaDao(db: AppDatabase): BoardFetchMetaDao =
        db.boardFetchMetaDao()

    @Provides
    fun providePostHistoryDao(db: AppDatabase): PostHistoryDao =
        db.postHistoryDao()

    @Provides
    fun providePostIdentityHistoryDao(db: AppDatabase): PostIdentityHistoryDao =
        db.postIdentityHistoryDao()
}
