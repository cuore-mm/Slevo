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
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ThreadHistoryDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.NgDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ThreadSummaryDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardVisitDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.BoardFetchMetaDao
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

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS thread_histories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    threadKey TEXT NOT NULL,
                    boardUrl TEXT NOT NULL,
                    boardId INTEGER NOT NULL,
                    boardName TEXT NOT NULL,
                    title TEXT NOT NULL,
                    resCount INTEGER NOT NULL,
                    lastAccess INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS thread_histories_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    threadKey TEXT NOT NULL,
                    boardUrl TEXT NOT NULL,
                    boardId INTEGER NOT NULL,
                    boardName TEXT NOT NULL,
                    title TEXT NOT NULL,
                    resCount INTEGER NOT NULL,
                    UNIQUE(threadKey, boardUrl)
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS thread_history_accesses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    threadHistoryId INTEGER NOT NULL,
                    accessedAt INTEGER NOT NULL,
                    FOREIGN KEY(threadHistoryId) REFERENCES thread_histories_new(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            database.execSQL(
                """
                INSERT INTO thread_histories_new (id, threadKey, boardUrl, boardId, boardName, title, resCount)
                SELECT id, threadKey, boardUrl, boardId, boardName, title, resCount
                FROM thread_histories
                """.trimIndent()
            )

            database.execSQL(
                """
                INSERT INTO thread_history_accesses (threadHistoryId, accessedAt)
                SELECT id, lastAccess FROM thread_histories
                """.trimIndent()
            )

            database.execSQL("DROP TABLE thread_histories")
            database.execSQL("ALTER TABLE thread_histories_new RENAME TO thread_histories")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ng_ids (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    pattern TEXT NOT NULL,
                    isRegex INTEGER NOT NULL,
                    boardId INTEGER,
                    FOREIGN KEY(boardId) REFERENCES boards(boardId) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ng_ids_boardId ON ng_ids(boardId)"
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ng_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    pattern TEXT NOT NULL,
                    isRegex INTEGER NOT NULL,
                    boardId INTEGER,
                    type TEXT NOT NULL,
                    FOREIGN KEY(boardId) REFERENCES boards(boardId) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "INSERT INTO ng_entries (id, pattern, isRegex, boardId, type) SELECT id, pattern, isRegex, boardId, 'USER_ID' FROM ng_ids"
            )
            database.execSQL("DROP TABLE ng_ids")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_ng_entries_boardId ON ng_entries(boardId)")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS thread_summaries (
                    boardId INTEGER NOT NULL,
                    threadId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    resCount INTEGER NOT NULL,
                    firstSeenAt INTEGER NOT NULL,
                    isArchived INTEGER NOT NULL,
                    subjectRank INTEGER NOT NULL,
                    PRIMARY KEY(boardId, threadId),
                    FOREIGN KEY(boardId) REFERENCES boards(boardId) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_thread_summaries_boardId_isArchived_subjectRank ON thread_summaries(boardId, isArchived, subjectRank)"
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS board_visits (
                    boardId INTEGER NOT NULL PRIMARY KEY,
                    baselineAt INTEGER NOT NULL,
                    FOREIGN KEY(boardId) REFERENCES boards(boardId) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS board_fetch_meta (
                    boardId INTEGER NOT NULL PRIMARY KEY,
                    etag TEXT,
                    lastModified TEXT,
                    lastFetchedAt INTEGER,
                    FOREIGN KEY(boardId) REFERENCES boards(boardId) ON DELETE CASCADE
                )
                """.trimIndent()
            )
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
}
