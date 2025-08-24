package com.websarva.wings.android.slevo.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.local.dao.bookmark.BookmarkThreadDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BbsServiceDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BoardCategoryCrossRefDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.BoardDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bookmark.BoardBookmarkGroupDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bookmark.BookmarkBoardDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bbs.CategoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.bookmark.ThreadBookmarkGroupDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenBoardTabDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.OpenThreadTabDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.NgDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.cache.ThreadSummaryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.cache.BoardVisitDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.cache.BoardFetchMetaDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS post_histories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    content TEXT NOT NULL,
                    date TEXT NOT NULL,
                    threadHistoryId INTEGER NOT NULL,
                    boardId INTEGER NOT NULL,
                    resNum INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    email TEXT NOT NULL,
                    postId TEXT NOT NULL,
                    FOREIGN KEY(threadHistoryId) REFERENCES thread_histories(id) ON DELETE CASCADE,
                    FOREIGN KEY(boardId) REFERENCES boards(boardId) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL("CREATE INDEX IF NOT EXISTS index_post_histories_threadHistoryId ON post_histories(threadHistoryId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_post_histories_boardId ON post_histories(boardId)")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN).apply {
                timeZone = TimeZone.getTimeZone("Asia/Tokyo")
            }
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS post_histories_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    content TEXT NOT NULL,
                    date INTEGER NOT NULL,
                    threadHistoryId INTEGER NOT NULL,
                    boardId INTEGER NOT NULL,
                    resNum INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    email TEXT NOT NULL,
                    postId TEXT NOT NULL,
                    FOREIGN KEY(threadHistoryId) REFERENCES thread_histories(id) ON DELETE CASCADE,
                    FOREIGN KEY(boardId) REFERENCES boards(boardId) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            val cursor = database.query("SELECT id, content, date, threadHistoryId, boardId, resNum, name, email, postId FROM post_histories")
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val content = cursor.getString(1)
                val dateStr = cursor.getString(2)
                val threadHistoryId = cursor.getLong(3)
                val boardId = cursor.getLong(4)
                val resNum = cursor.getInt(5)
                val name = cursor.getString(6)
                val email = cursor.getString(7)
                val postId = cursor.getString(8)
                val sanitized = dateStr
                    .replace(Regex("""\([^)]*\)"""), "")
                    .replace(Regex("""\.\d+"""), "")
                    .trim()
                val timestamp = try {
                    sdf.parse(sanitized)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
                database.execSQL(
                    "INSERT INTO post_histories_new(id, content, date, threadHistoryId, boardId, resNum, name, email, postId) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(id, content, timestamp, threadHistoryId, boardId, resNum, name, email, postId)
                )
            }
            cursor.close()
            database.execSQL("DROP TABLE post_histories")
            database.execSQL("ALTER TABLE post_histories_new RENAME TO post_histories")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_post_histories_threadHistoryId ON post_histories(threadHistoryId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_post_histories_boardId ON post_histories(boardId)")
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
            "slevo_database"
        )
            // マイグレーション未定義時は既存データを破棄し再生成
            .fallbackToDestructiveMigration(false)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
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

    @Provides
    fun providePostHistoryDao(db: AppDatabase): PostHistoryDao =
        db.postHistoryDao()
}
