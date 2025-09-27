package com.websarva.wings.android.slevo.data.datasource.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
        PostIdentityHistoryEntity::class
    ],
    version = 4,
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

    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE open_thread_tabs ADD COLUMN lastReadResNo INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    "ALTER TABLE open_thread_tabs ADD COLUMN firstNewResNo INTEGER"
                )
                database.execSQL(
                    "ALTER TABLE open_thread_tabs ADD COLUMN prevResCount INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
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
                database.execSQL(
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
                database.execSQL("DROP TABLE open_thread_tabs")
                database.execSQL("ALTER TABLE open_thread_tabs_new RENAME TO open_thread_tabs")

                database.execSQL(
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
                database.execSQL(
                    "INSERT INTO thread_histories_new (" +
                        "id, threadId, boardUrl, boardId, boardName, title, resCount" +
                        ") SELECT " +
                        "id, trim(replace(replace(boardUrl, 'https://', ''), 'http://', ''), '/') || '/' || threadKey, " +
                        "boardUrl, boardId, boardName, title, resCount FROM thread_histories"
                )
                database.execSQL("DROP TABLE thread_histories")
                database.execSQL("ALTER TABLE thread_histories_new RENAME TO thread_histories")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_thread_histories_threadId ON thread_histories(threadId)"
                )

                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS thread_history_accesses_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "threadHistoryId INTEGER NOT NULL, " +
                        "accessedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(threadHistoryId) REFERENCES thread_histories(id) ON DELETE CASCADE)"
                )
                database.execSQL(
                    "INSERT INTO thread_history_accesses_new (threadHistoryId, accessedAt) " +
                        "SELECT threadHistoryId, accessedAt FROM thread_history_accesses"
                )
                database.execSQL("DROP TABLE thread_history_accesses")
                database.execSQL("ALTER TABLE thread_history_accesses_new RENAME TO thread_history_accesses")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_thread_history_accesses_threadHistoryId ON thread_history_accesses(threadHistoryId)"
                )
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS post_identity_histories (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "boardId INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, " +
                        "value TEXT NOT NULL, " +
                        "lastUsedAt INTEGER NOT NULL, " +
                        "FOREIGN KEY(boardId) REFERENCES boards(boardId) ON DELETE CASCADE" +
                        ")"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_post_identity_histories_boardId ON post_identity_histories(boardId)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_post_identity_histories_boardId_type_value " +
                        "ON post_identity_histories(boardId, type, value)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_post_identity_histories_lastUsedAt ON post_identity_histories(lastUsedAt)"
                )
            }
        }
    }
}
