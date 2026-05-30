package com.websarva.wings.android.slevo.data.datasource.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostIdentityHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostLastIdentityDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.state.ThreadStateDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BbsServiceEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardCategoryCrossRef
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.BoardBookmarkGroupEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.BookmarkBoardEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.BookmarkThreadEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bbs.CategoryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.bookmark.ThreadBookmarkGroupEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenBoardTabEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenThreadTabEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.ThreadHistoryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.ThreadHistoryAccessEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.cache.ThreadSummaryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.cache.BoardVisitEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.cache.BoardFetchMetaEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostHistoryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostIdentityHistoryEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostLastIdentityEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.state.ThreadStateEntity

@TypeConverters(NgTypeConverter::class)
@Database(
    entities = [
        BbsServiceEntity::class,
        CategoryEntity::class,
        BoardEntity::class,
        BoardCategoryCrossRef::class,
        BoardBookmarkGroupEntity::class,
        BookmarkBoardEntity::class,
        BookmarkThreadEntity::class,
        ThreadBookmarkGroupEntity::class,
        OpenBoardTabEntity::class,
        OpenThreadTabEntity::class,
        ThreadHistoryEntity::class,
        ThreadHistoryAccessEntity::class,
        NgEntity::class,
        ThreadSummaryEntity::class,
        BoardVisitEntity::class,
        BoardFetchMetaEntity::class,
        PostHistoryEntity::class,
        PostIdentityHistoryEntity::class,
        PostLastIdentityEntity::class,
        ThreadStateEntity::class
    ],
    version = 9,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bbsServiceDao(): BbsServiceDao
    abstract fun categoryDao(): CategoryDao
    abstract fun boardDao(): BoardDao
    abstract fun boardCategoryCrossRefDao(): BoardCategoryCrossRefDao
    abstract fun bookmarkBoardDao(): BookmarkBoardDao
    abstract fun bookmarkThreadDao(): BookmarkThreadDao
    abstract fun boardGroupDao(): BoardBookmarkGroupDao
    abstract fun threadBookmarkGroupDao(): ThreadBookmarkGroupDao
    abstract fun openBoardTabDao(): OpenBoardTabDao
    abstract fun openThreadTabDao(): OpenThreadTabDao
    abstract fun threadHistoryDao(): ThreadHistoryDao
    abstract fun ngDao(): NgDao
    abstract fun threadSummaryDao(): ThreadSummaryDao
    abstract fun boardVisitDao(): BoardVisitDao
    abstract fun boardFetchMetaDao(): BoardFetchMetaDao
    abstract fun postHistoryDao(): PostHistoryDao
    abstract fun postIdentityHistoryDao(): PostIdentityHistoryDao
    abstract fun postLastIdentityDao(): PostLastIdentityDao
    abstract fun threadStateDao(): ThreadStateDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE open_thread_tabs ADD COLUMN lastReadResNo INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE open_thread_tabs ADD COLUMN firstNewResNo INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE open_thread_tabs ADD COLUMN prevResCount INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS open_thread_tabs_new (" +
                        "threadId TEXT NOT NULL, " +
                        "boardUrl TEXT NOT NULL, " +
                        "boardId INTEGER NOT NULL, " +
                        "boardName TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "resCount INTEGER NOT NULL, " +
                        "prevResCount INTEGER NOT NULL DEFAULT 0, " +
                        "lastReadResNo INTEGER NOT NULL DEFAULT 0, " +
                        "firstNewResNo INTEGER, " +
                        "sortOrder INTEGER NOT NULL, " +
                        "firstVisibleItemIndex INTEGER NOT NULL, " +
                        "firstVisibleItemScrollOffset INTEGER NOT NULL, " +
                        "PRIMARY KEY(threadId))"
                )
                db.execSQL(
                    "INSERT INTO open_thread_tabs_new (" +
                        "threadId, boardUrl, boardId, boardName, title, resCount, " +
                        "prevResCount, lastReadResNo, firstNewResNo, sortOrder, " +
                        "firstVisibleItemIndex, firstVisibleItemScrollOffset" +
                        ") SELECT " +
                        "trim(replace(replace(boardUrl, 'https://', ''), 'http://', ''), '/') || '/' || threadKey, " +
                        "boardUrl, boardId, boardName, title, resCount, " +
                        "prevResCount, lastReadResNo, firstNewResNo, sortOrder, " +
                        "firstVisibleItemIndex, firstVisibleItemScrollOffset FROM open_thread_tabs"
                )
                db.execSQL("DROP TABLE open_thread_tabs")
                db.execSQL("ALTER TABLE open_thread_tabs_new RENAME TO open_thread_tabs")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS thread_histories_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "threadId TEXT NOT NULL, " +
                        "boardUrl TEXT NOT NULL, " +
                        "boardId INTEGER NOT NULL, " +
                        "boardName TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "resCount INTEGER NOT NULL, " +
                        "prevResCount INTEGER NOT NULL DEFAULT 0, " +
                        "lastReadResNo INTEGER NOT NULL DEFAULT 0, " +
                        "firstNewResNo INTEGER" +
                        ")"
                )
                db.execSQL(
                    "INSERT INTO thread_histories_new (" +
                        "id, threadId, boardUrl, boardId, boardName, title, resCount" +
                        ") SELECT " +
                        "id, trim(replace(replace(boardUrl, 'https://', ''), 'http://', ''), '/') || '/' || threadKey, " +
                        "boardUrl, boardId, boardName, title, resCount FROM thread_histories"
                )
                db.execSQL("DROP TABLE thread_histories")
                db.execSQL("ALTER TABLE thread_histories_new RENAME TO thread_histories")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_thread_histories_threadId ON thread_histories(threadId)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS thread_history_accesses_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "threadHistoryId INTEGER NOT NULL, " +
                        "accessedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(threadHistoryId) REFERENCES thread_histories(id) ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO thread_history_accesses_new (threadHistoryId, accessedAt) " +
                        "SELECT threadHistoryId, accessedAt FROM thread_history_accesses"
                )
                db.execSQL("DROP TABLE thread_history_accesses")
                db.execSQL("ALTER TABLE thread_history_accesses_new RENAME TO thread_history_accesses")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_thread_history_accesses_threadHistoryId ON thread_history_accesses(threadHistoryId)"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS post_identity_histories (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "boardId INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "value TEXT NOT NULL, " +
                        "lastUsedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(boardId) REFERENCES boards(boardId) ON DELETE CASCADE" +
                        ")"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_post_identity_histories_boardId ON post_identity_histories(boardId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_post_identity_histories_boardId_type_value " +
                        "ON post_identity_histories(boardId, type, value)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_post_identity_histories_lastUsedAt ON post_identity_histories(lastUsedAt)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS post_last_identities (" +
                        "boardId INTEGER NOT NULL, " +
                        "name TEXT NOT NULL, " +
                        "email TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(boardId), " +
                        "FOREIGN KEY(boardId) REFERENCES boards(boardId) ON DELETE CASCADE" +
                        ")"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_post_last_identities_boardId ON post_last_identities(boardId)"
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS thread_states (" +
                        "threadId TEXT NOT NULL, " +
                        "boardId INTEGER NOT NULL, " +
                        "boardUrl TEXT NOT NULL, " +
                        "boardName TEXT NOT NULL, " +
                        "threadKey TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "latestResCount INTEGER NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(threadId))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_thread_states_boardId_threadKey " +
                        "ON thread_states(boardId, threadKey)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_thread_states_boardId " +
                        "ON thread_states(boardId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_thread_states_boardUrl " +
                        "ON thread_states(boardUrl)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_thread_states_updatedAt " +
                        "ON thread_states(updatedAt)"
                )

                val now = System.currentTimeMillis()
                migrateThreadStatesFromTabs(db, now)
                migrateThreadStatesFromHistories(db, now)
                migrateThreadStatesFromSummaries(db, now)
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS open_thread_tabs_new (" +
                        "threadId TEXT NOT NULL, " +
                        "sortOrder INTEGER NOT NULL, " +
                        "firstVisibleItemIndex INTEGER NOT NULL, " +
                        "firstVisibleItemScrollOffset INTEGER NOT NULL, " +
                        "PRIMARY KEY(threadId))"
                )
                db.execSQL(
                    "INSERT INTO open_thread_tabs_new (" +
                        "threadId, sortOrder, firstVisibleItemIndex, firstVisibleItemScrollOffset" +
                        ") SELECT threadId, sortOrder, firstVisibleItemIndex, " +
                        "firstVisibleItemScrollOffset FROM open_thread_tabs"
                )
                db.execSQL("DROP TABLE open_thread_tabs")
                db.execSQL("ALTER TABLE open_thread_tabs_new RENAME TO open_thread_tabs")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS thread_summaries_new (" +
                        "boardId INTEGER NOT NULL, " +
                        "threadId TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "resCount INTEGER NOT NULL, " +
                        "firstSeenAt INTEGER NOT NULL, " +
                        "subjectRank INTEGER NOT NULL, " +
                        "PRIMARY KEY(boardId, threadId), " +
                        "FOREIGN KEY(boardId) REFERENCES boards(boardId) ON DELETE CASCADE)"
                )
                db.execSQL(
                    "INSERT INTO thread_summaries_new (" +
                        "boardId, threadId, title, resCount, firstSeenAt, subjectRank" +
                        ") SELECT boardId, threadId, title, resCount, firstSeenAt, subjectRank " +
                        "FROM thread_summaries WHERE isArchived = 0"
                )
                db.execSQL("DROP TABLE thread_summaries")
                db.execSQL("ALTER TABLE thread_summaries_new RENAME TO thread_summaries")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_thread_summaries_boardId ON thread_summaries(boardId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_thread_summaries_boardId_subjectRank ON thread_summaries(boardId, subjectRank)"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE open_board_tabs ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE open_thread_tabs ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 既存の開いているスレッドタブから共通客観状態を作成する。
         * タブ由来の行はグローバル `threadId` があるため、新規 `thread_states` の初期値に使う。
         */
        private fun migrateThreadStatesFromTabs(db: SupportSQLiteDatabase, updatedAt: Long) {
            db.query(
                "SELECT threadId, boardId, boardUrl, boardName, title, resCount FROM open_thread_tabs"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getString(0)
                    upsertMigratedThreadState(
                        db = db,
                        threadId = threadId,
                        boardId = cursor.getLong(1),
                        boardUrl = cursor.getString(2),
                        boardName = cursor.getString(3),
                        threadKey = threadId.substringAfterLast('/'),
                        title = cursor.getString(4),
                        latestResCount = cursor.getInt(5),
                        updatedAt = updatedAt,
                    )
                }
            }
        }

        /**
         * 既存の閲覧履歴から共通客観状態を作成または補完する。
         * 同じ `threadId` がすでにある場合は、履歴レス数を含めた最大レス数を保持する。
         */
        private fun migrateThreadStatesFromHistories(db: SupportSQLiteDatabase, updatedAt: Long) {
            db.query(
                "SELECT threadId, boardId, boardUrl, boardName, title, resCount FROM thread_histories"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getString(0)
                    upsertMigratedThreadState(
                        db = db,
                        threadId = threadId,
                        boardId = cursor.getLong(1),
                        boardUrl = cursor.getString(2),
                        boardName = cursor.getString(3),
                        threadKey = threadId.substringAfterLast('/'),
                        title = cursor.getString(4),
                        latestResCount = cursor.getInt(5),
                        updatedAt = updatedAt,
                    )
                }
            }
        }

        /**
         * 既存の板一覧キャッシュで、作成済みの共通客観状態を補完する。
         * `thread_summaries` 単独では安全なグローバル `threadId` を作れないため、既存行だけ更新する。
         */
        private fun migrateThreadStatesFromSummaries(db: SupportSQLiteDatabase, updatedAt: Long) {
            db.query(
                "SELECT boardId, threadId, title, resCount FROM thread_summaries"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    db.execSQL(
                        "UPDATE thread_states SET " +
                            "title = ?, " +
                            "latestResCount = CASE " +
                            "WHEN latestResCount < ? THEN ? ELSE latestResCount END, " +
                            "updatedAt = ? " +
                            "WHERE boardId = ? AND threadKey = ?",
                        arrayOf(
                            cursor.getString(2),
                            cursor.getInt(3),
                            cursor.getInt(3),
                            updatedAt,
                            cursor.getLong(0),
                            cursor.getString(1),
                        )
                    )
                }
            }
        }

        /**
         * マイグレーション元の1行を `thread_states` へ統合する。
         * 先に挿入を試し、既存行はレス数を最大値に保ちながら最新の板情報とタイトルで補完する。
         */
        private fun upsertMigratedThreadState(
            db: SupportSQLiteDatabase,
            threadId: String,
            boardId: Long,
            boardUrl: String,
            boardName: String,
            threadKey: String,
            title: String,
            latestResCount: Int,
            updatedAt: Long,
        ) {
            db.execSQL(
                "INSERT OR IGNORE INTO thread_states (" +
                    "threadId, boardId, boardUrl, boardName, threadKey, title, latestResCount, updatedAt" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(threadId, boardId, boardUrl, boardName, threadKey, title, latestResCount, updatedAt)
            )
            db.execSQL(
                "UPDATE thread_states SET " +
                    "boardId = ?, boardUrl = ?, boardName = ?, threadKey = ?, title = ?, " +
                    "latestResCount = CASE " +
                    "WHEN latestResCount < ? THEN ? ELSE latestResCount END, " +
                    "updatedAt = ? WHERE threadId = ?",
                arrayOf(
                    boardId,
                    boardUrl,
                    boardName,
                    threadKey,
                    title,
                    latestResCount,
                    latestResCount,
                    updatedAt,
                    threadId,
                )
            )
        }
    }
}
