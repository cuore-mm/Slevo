package com.websarva.wings.android.bbsviewer.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.websarva.wings.android.bbsviewer.data.datasource.local.AppDatabase
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkThreadDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BbsServiceDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardCategoryCrossRefDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardBookmarkGroupDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BookmarkBoardDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.CategoryDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ThreadBookmarkGroupDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.OpenBoardTabDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.OpenThreadTabDao
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

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS open_board_tabs (
                    boardUrl TEXT NOT NULL PRIMARY KEY,
                    boardId INTEGER NOT NULL,
                    boardName TEXT NOT NULL,
                    serviceName TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    firstVisibleItemIndex INTEGER NOT NULL,
                    firstVisibleItemScrollOffset INTEGER NOT NULL
                )
            """.trimIndent())
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS open_thread_tabs (
                    threadKey TEXT NOT NULL,
                    boardUrl TEXT NOT NULL,
                    boardId INTEGER NOT NULL,
                    boardName TEXT NOT NULL,
                    title TEXT NOT NULL,
                    resCount INTEGER NOT NULL,
                    sortOrder INTEGER NOT NULL,
                    firstVisibleItemIndex INTEGER NOT NULL,
                    firstVisibleItemScrollOffset INTEGER NOT NULL,
                    PRIMARY KEY(threadKey, boardUrl)
                )
            """.trimIndent())
        }
    }

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
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "bbsviewer_database"
        )
            // マイグレーション未定義時は既存データを破棄し再生成
            .fallbackToDestructiveMigration(false)
            .addMigrations(MIGRATION_1_2)
            .addCallback(callback)
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
}
