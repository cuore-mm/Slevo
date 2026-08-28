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
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostLastIdentityDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PendingOwnPostDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.notification.ReplyNotificationDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.state.ThreadStateDao
import com.squareup.moshi.Moshi
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreMigrationAttemptRecorder
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreMigrationWrapper
import com.websarva.wings.android.slevo.data.backup.pending.RealPendingRestoreFileStore
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
        callback: DatabaseCallback,
        migrationAttemptRecorder: PendingRestoreMigrationAttemptRecorder,
    ): AppDatabase {
        val name = if (BuildConfig.DEBUG) "slevo_dev_database" else "slevo_database"
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            name
        )
            .addMigrations(
                *AppDatabase.ALL_REGISTERED_MIGRATIONS
                    .map { migration ->
                        PendingRestoreMigrationWrapper(migration, migrationAttemptRecorder)
                    }
                    .toTypedArray(),
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
     * pre-Hilt pending restore marker を Room migration provider から共有する recorder を作る。
     */
    @Provides
    @Singleton
    fun providePendingRestoreMigrationAttemptRecorder(
        @ApplicationContext context: Context,
        moshi: Moshi,
    ): PendingRestoreMigrationAttemptRecorder =
        PendingRestoreMigrationAttemptRecorder(RealPendingRestoreFileStore(context, moshi))

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

    @Provides
    fun providePostLastIdentityDao(db: AppDatabase): PostLastIdentityDao =
        db.postLastIdentityDao()

    /** 未確定自分投稿のDAOを提供する。 */
    @Provides
    fun providePendingOwnPostDao(db: AppDatabase): PendingOwnPostDao =
        db.pendingOwnPostDao()

    @Provides
    fun provideThreadStateDao(db: AppDatabase): ThreadStateDao =
        db.threadStateDao()

    /** 返信通知の永続化DAOを提供する。 */
    @Provides
    fun provideReplyNotificationDao(db: AppDatabase): ReplyNotificationDao =
        db.replyNotificationDao()
}
