package com.websarva.wings.android.slevo.data.datasource.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

//    @Test
//    fun migrate1To2_addsColumns_andPreservesData() {
//        val context = InstrumentationRegistry.getInstrumentation().targetContext
//        context.deleteDatabase(TEST_DB)
//
//        // v1 スキーマでテーブル作成 & データ投入
//        helper.createDatabase(TEST_DB, 1).apply {
//            execSQL("""
//                CREATE TABLE IF NOT EXISTS open_thread_tabs (
//                    threadKey TEXT NOT NULL PRIMARY KEY,
//                    boardUrl TEXT NOT NULL,
//                    boardId INTEGER NOT NULL,
//                    boardName TEXT NOT NULL,
//                    title TEXT NOT NULL,
//                    resCount INTEGER NOT NULL,
//                    sortOrder INTEGER NOT NULL,
//                    firstVisibleItemIndex INTEGER NOT NULL,
//                    firstVisibleItemScrollOffset INTEGER NOT NULL
//                )
//            """.trimIndent())
//            execSQL("""
//                INSERT INTO open_thread_tabs
//                  (threadKey, boardUrl, boardId, boardName, title, resCount, sortOrder, firstVisibleItemIndex, firstVisibleItemScrollOffset)
//                VALUES
//                  ('123','https://example.com/board',1,'board','title',10,0,0,0)
//            """.trimIndent())
//            close()
//        }
//
//        // v2 までマイグレーション＋スキーマ検証
//        helper.runMigrationsAndValidate(
//            TEST_DB,
//            2,
//            /* validateDroppedTables = */ true,
//            AppDatabase.MIGRATION_1_2
//        )
//
//        // 実DBを Room で開いて新カラムの存在と初期値を確認
//        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
//            .addMigrations(AppDatabase.MIGRATION_1_2)
//            .build()
//
//        db.openHelper.writableDatabase.query("PRAGMA table_info('open_thread_tabs')").use { c ->
//            val cols = mutableListOf<String>()
//            while (c.moveToNext()) cols += c.getString(c.getColumnIndexOrThrow("name"))
//            assertTrue(cols.containsAll(listOf("lastReadResNo","firstNewResNo","prevResCount")))
//        }
//        db.openHelper.writableDatabase.query(
//            "SELECT lastReadResNo, firstNewResNo, prevResCount FROM open_thread_tabs WHERE threadKey='123'"
//        ).use { c ->
//            assertTrue(c.moveToFirst())
//            // ここはマイグレSQLの DEFAULT に合わせて期待値を設定
//            assertEquals(0, c.getInt(0))       // lastReadResNo
//            assertTrue(c.isNull(1))            // firstNewResNo (NULL許容なら)
//            assertEquals(0, c.getInt(2))       // prevResCount
//        }
//
//        db.close()
//    }

    @Test
    fun migrate2To3_replacesThreadKey_everywhere() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)

        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("""
                CREATE TABLE IF NOT EXISTS open_thread_tabs (
                    threadKey TEXT NOT NULL PRIMARY KEY,
                    boardUrl TEXT NOT NULL,
                    boardId INTEGER NOT NULL,
                    boardName TEXT NOT NULL,
                    title TEXT NOT NULL,
                    resCount INTEGER NOT NULL,
                    prevResCount INTEGER NOT NULL DEFAULT 0,
                    lastReadResNo INTEGER NOT NULL DEFAULT 0,
                    firstNewResNo INTEGER,
                    sortOrder INTEGER NOT NULL,
                    firstVisibleItemIndex INTEGER NOT NULL,
                    firstVisibleItemScrollOffset INTEGER NOT NULL
                )
            """.trimIndent())
            execSQL("""
                CREATE TABLE IF NOT EXISTS thread_histories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    threadKey TEXT NOT NULL,
                    boardUrl TEXT NOT NULL,
                    boardId INTEGER NOT NULL,
                    boardName TEXT NOT NULL,
                    title TEXT NOT NULL,
                    resCount INTEGER NOT NULL
                )
            """.trimIndent())
            execSQL("""
                CREATE TABLE IF NOT EXISTS thread_history_accesses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    threadHistoryId INTEGER NOT NULL,
                    accessedAt INTEGER NOT NULL
                )
            """.trimIndent())

            execSQL("""
                INSERT INTO open_thread_tabs
                  (threadKey, boardUrl, boardId, boardName, title, resCount, prevResCount, lastReadResNo, firstNewResNo, sortOrder, firstVisibleItemIndex, firstVisibleItemScrollOffset)
                VALUES
                  ('123','https://example.com/board',1,'board','title',10,0,0,NULL,0,0,0)
            """.trimIndent())
            execSQL("""
                INSERT INTO thread_histories
                  (threadKey, boardUrl, boardId, boardName, title, resCount)
                VALUES
                  ('123','https://example.com/board',1,'board','title',10)
            """.trimIndent())
            execSQL("INSERT INTO thread_history_accesses (threadHistoryId, accessedAt) VALUES (1, 0)")
            close()
        }

        // v3 までマイグレ＋検証
        helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_2_3
        )

        // 変換結果を確認
        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build()

        db.openHelper.writableDatabase.query("SELECT threadId FROM open_thread_tabs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("example.com/board/123", c.getString(0)) // 期待仕様に合わせる
        }
        db.openHelper.writableDatabase.query("PRAGMA table_info('open_thread_tabs')").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols += c.getString(c.getColumnIndexOrThrow("name"))
            assertFalse(cols.contains("threadKey"))
            assertTrue(cols.contains("threadId"))
        }

        // （任意）thread_histories 側の置換や関連の追随もチェック
        // 例: thread_histories にも threadId 追加/置換したなら同様に PRAGMA / SELECT 検証

        db.close()
    }

    @Test
    fun migrate3To4_createsPostIdentityHistories() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)

        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS services (
                    serviceId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    domain TEXT NOT NULL,
                    displayName TEXT,
                    menuUrl TEXT
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS boards (
                    boardId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    serviceId INTEGER NOT NULL,
                    url TEXT NOT NULL,
                    name TEXT NOT NULL,
                    FOREIGN KEY(serviceId) REFERENCES services(serviceId) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL("INSERT INTO services (serviceId, domain, displayName, menuUrl) VALUES (1, 'example.com', 'Example', NULL)")
            execSQL("INSERT INTO boards (boardId, serviceId, url, name) VALUES (1, 1, 'https://example.com/board', 'Example Board')")
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            AppDatabase.MIGRATION_3_4
        )

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .build()

        val writable = db.openHelper.writableDatabase
        writable.execSQL("PRAGMA foreign_keys=ON")

        writable.query("PRAGMA table_info('post_identity_histories')").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertTrue(columns.containsAll(listOf("id", "boardId", "type", "value", "lastUsedAt")))
        }

        writable.execSQL(
            "INSERT INTO post_identity_histories (boardId, type, value, lastUsedAt) VALUES (1, 'name', 'Alice', 1000)"
        )
        writable.execSQL(
            "INSERT INTO post_identity_histories (boardId, type, value, lastUsedAt) VALUES (1, 'mail', 'alice@example.com', 1001)"
        )

        assertThrows(SQLiteConstraintException::class.java) {
            writable.execSQL(
                "INSERT INTO post_identity_histories (boardId, type, value, lastUsedAt) VALUES (1, 'name', 'Alice', 2000)"
            )
        }

        assertThrows(SQLiteConstraintException::class.java) {
            writable.execSQL(
                "INSERT INTO post_identity_histories (boardId, type, value, lastUsedAt) VALUES (99, 'name', 'Ghost', 3000)"
            )
        }

        db.close()
    }

    @Test
    fun migrate4To5_createsPostLastIdentities() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)

        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS services (
                    serviceId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    domain TEXT NOT NULL,
                    displayName TEXT,
                    menuUrl TEXT
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS boards (
                    boardId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    serviceId INTEGER NOT NULL,
                    url TEXT NOT NULL,
                    name TEXT NOT NULL,
                    FOREIGN KEY(serviceId) REFERENCES services(serviceId) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS post_identity_histories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    boardId INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    value TEXT NOT NULL,
                    lastUsedAt INTEGER NOT NULL,
                    FOREIGN KEY(boardId) REFERENCES boards(boardId) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            execSQL("INSERT INTO services (serviceId, domain, displayName, menuUrl) VALUES (1, 'example.com', 'Example', NULL)")
            execSQL("INSERT INTO boards (boardId, serviceId, url, name) VALUES (1, 1, 'https://example.com/board', 'Example Board')")
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            AppDatabase.MIGRATION_4_5
        )

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .build()

        val writable = db.openHelper.writableDatabase
        writable.execSQL("PRAGMA foreign_keys=ON")

        writable.query("PRAGMA table_info('post_last_identities')").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertTrue(columns.containsAll(listOf("boardId", "name", "email", "updatedAt")))
        }

        writable.execSQL(
            "INSERT INTO post_last_identities (boardId, name, email, updatedAt) VALUES (1, 'Alice', 'alice@example.com', 1000)"
        )

        writable.query("SELECT name, email, updatedAt FROM post_last_identities WHERE boardId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Alice", cursor.getString(0))
            assertEquals("alice@example.com", cursor.getString(1))
            assertEquals(1000, cursor.getLong(2))
        }

        assertThrows(SQLiteConstraintException::class.java) {
            writable.execSQL(
                "INSERT INTO post_last_identities (boardId, name, email, updatedAt) VALUES (1, 'Bob', 'bob@example.com', 2000)"
            )
        }

        assertThrows(SQLiteConstraintException::class.java) {
            writable.execSQL(
                "INSERT INTO post_last_identities (boardId, name, email, updatedAt) VALUES (99, 'Ghost', 'ghost@example.com', 3000)"
            )
        }

        db.close()
    }

    /**
     * v5 のタブ・履歴・板キャッシュから `thread_states` を作成し、同一スレッドを最大レス数で統合する。
     */
    @Test
    fun migrate5To6_createsThreadStates_andMergesExistingSources() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)

        helper.createDatabase(TEST_DB, 5).apply {
            execSQL("INSERT INTO services (serviceId, domain, displayName, menuUrl) VALUES (1, 'example.com', 'Example', NULL)")
            execSQL("INSERT INTO boards (boardId, serviceId, url, name) VALUES (1, 1, 'https://example.com/test/', 'Test Board')")
            execSQL(
                "INSERT INTO open_thread_tabs (" +
                    "threadId, boardUrl, boardId, boardName, title, resCount, prevResCount, " +
                    "lastReadResNo, firstNewResNo, sortOrder, firstVisibleItemIndex, firstVisibleItemScrollOffset" +
                    ") VALUES ('example.com/test/123', 'https://example.com/test/', 1, " +
                    "'Test Board', 'Tab title', 10, 0, 0, NULL, 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO thread_histories (" +
                    "threadId, boardUrl, boardId, boardName, title, resCount, prevResCount, " +
                    "lastReadResNo, firstNewResNo" +
                    ") VALUES ('example.com/test/123', 'https://example.com/test/', 1, " +
                    "'Test Board', 'History title', 14, 10, 14, NULL)"
            )
            execSQL(
                "INSERT INTO thread_histories (" +
                    "threadId, boardUrl, boardId, boardName, title, resCount, prevResCount, " +
                    "lastReadResNo, firstNewResNo" +
                    ") VALUES ('example.com/test/456', 'https://example.com/test/', 1, " +
                    "'Test Board', 'History only', 5, 0, 0, NULL)"
            )
            execSQL(
                "INSERT INTO thread_summaries (boardId, threadId, title, resCount, firstSeenAt, isArchived, subjectRank) " +
                    "VALUES (1, '123', 'Summary title', 20, 0, 0, 0)"
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            AppDatabase.MIGRATION_5_6
        )

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7)
            .build()

        db.openHelper.writableDatabase.query(
            "SELECT threadKey, title, latestResCount FROM thread_states WHERE threadId = 'example.com/test/123'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("123", cursor.getString(0))
            assertEquals("Summary title", cursor.getString(1))
            assertEquals(20, cursor.getInt(2))
        }
        db.openHelper.writableDatabase.query(
            "SELECT threadKey, title, latestResCount FROM thread_states WHERE threadId = 'example.com/test/456'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("456", cursor.getString(0))
            assertEquals("History only", cursor.getString(1))
            assertEquals(5, cursor.getInt(2))
        }

        db.close()
    }

    /**
     * `ThreadStateRepository` が `threadId` 由来の `threadKey` を保存し、レス数を小さく戻さないことを検証する。
     */
    @Test
    fun threadStateRepository_keepsMaxResCount_andDerivesThreadKey() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = ThreadStateRepository(db.threadStateDao())
        val threadId = ThreadId.of("example.com", "test", "123")

        repository.saveThreadState(
            ThreadStateRepository.ThreadStateUpdate(
                threadId = threadId,
                boardId = 1,
                boardUrl = "https://example.com/test/",
                boardName = "Test Board",
                title = "Initial",
                latestResCount = 20,
                updatedAt = 1000,
            )
        )
        repository.saveThreadState(
            ThreadStateRepository.ThreadStateUpdate(
                threadId = threadId,
                boardId = 1,
                boardUrl = "https://example.com/test/",
                boardName = "Test Board",
                title = "Renamed",
                latestResCount = 10,
                updatedAt = 2000,
            )
        )

        val state = db.threadStateDao().find(threadId)
        assertEquals("123", state?.threadKey)
        assertEquals("Renamed", state?.title)
        assertEquals(20, state?.latestResCount)
        assertEquals(2000, state?.updatedAt)

        db.close()
    }

    /**
     * v6 の `open_thread_tabs` から表示情報と既読状態カラムを削除し、タブ固有状態だけを保持する。
     */
    @Test
    fun migrate6To7_keepsOnlyThreadTabSpecificColumns() {
        // --- Setup ---
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)

        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO open_thread_tabs (" +
                    "threadId, boardUrl, boardId, boardName, title, resCount, prevResCount, " +
                    "lastReadResNo, firstNewResNo, sortOrder, firstVisibleItemIndex, firstVisibleItemScrollOffset" +
                    ") VALUES ('example.com/test/123', 'https://example.com/test/', 1, " +
                    "'Test Board', 'Tab title', 10, 8, 9, 10, 3, 4, 5)"
            )
            close()
        }

        // --- Migration ---
        helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            AppDatabase.MIGRATION_6_7
        )

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_6_7)
            .build()

        // --- Validation ---
        db.openHelper.writableDatabase.query("PRAGMA table_info('open_thread_tabs')").use { cursor ->
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertEquals(
                listOf(
                    "threadId",
                    "sortOrder",
                    "firstVisibleItemIndex",
                    "firstVisibleItemScrollOffset",
                ),
                columns,
            )
        }
        db.openHelper.writableDatabase.query(
            "SELECT threadId, sortOrder, firstVisibleItemIndex, firstVisibleItemScrollOffset FROM open_thread_tabs"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("example.com/test/123", cursor.getString(0))
            assertEquals(3, cursor.getInt(1))
            assertEquals(4, cursor.getInt(2))
            assertEquals(5, cursor.getInt(3))
        }

        db.close()
    }

    /**
     * `thread_states` の GC が古い孤立行だけを削除し、各参照元がある行を保持することを検証する。
     */
    @Test
    fun threadStateRepository_collectGarbage_deletesOnlyOldOrphans() = runBlocking {
        // --- Setup ---
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = ThreadStateRepository(db.threadStateDao())
        val writable = db.openHelper.writableDatabase
        writable.execSQL("INSERT INTO services (serviceId, domain, displayName, menuUrl) VALUES (1, 'example.com', 'Example', NULL)")
        writable.execSQL("INSERT INTO boards (boardId, serviceId, url, name) VALUES (1, 1, 'https://example.com/test/', 'Test Board')")
        writable.execSQL("INSERT INTO thread_bookmark_groups (groupId, name, colorName, sortOrder) VALUES (1, 'Bookmarks', 'yellow', 0)")

        val now = 40L * 24 * 60 * 60 * 1000
        val oldUpdatedAt = 0L
        val recentUpdatedAt = now - 1_000
        val threadIds = listOf("orphan", "recent", "tab", "history", "bookmark", "summary")
        threadIds.forEach { key ->
            repository.saveThreadState(
                ThreadStateRepository.ThreadStateUpdate(
                    threadId = ThreadId.of("example.com", "test", key),
                    boardId = 1,
                    boardUrl = "https://example.com/test/",
                    boardName = "Test Board",
                    title = key,
                    latestResCount = 1,
                    updatedAt = if (key == "recent") recentUpdatedAt else oldUpdatedAt,
                )
            )
        }
        writable.execSQL("INSERT INTO open_thread_tabs (threadId, sortOrder, firstVisibleItemIndex, firstVisibleItemScrollOffset) VALUES ('example.com/test/tab', 0, 0, 0)")
        writable.execSQL("INSERT INTO thread_histories (threadId, boardUrl, boardId, boardName, title, resCount, prevResCount, lastReadResNo, firstNewResNo) VALUES ('example.com/test/history', 'https://example.com/test/', 1, 'Test Board', 'history', 1, 0, 0, NULL)")
        writable.execSQL("INSERT INTO bookmark_threads (threadKey, boardUrl, boardId, groupId, title, boardName, resCount) VALUES ('bookmark', 'https://example.com/test/', 1, 1, 'bookmark', 'Test Board', 1)")
        writable.execSQL("INSERT INTO thread_summaries (boardId, threadId, title, resCount, firstSeenAt, isArchived, subjectRank) VALUES (1, 'summary', 'summary', 1, 0, 0, 0)")

        // --- GC ---
        val deleted = repository.collectGarbage(nowMillis = now, limit = 10)

        // --- Validation ---
        assertEquals(1, deleted)
        assertEquals(null, db.threadStateDao().find(ThreadId.of("example.com", "test", "orphan")))
        listOf("recent", "tab", "history", "bookmark", "summary").forEach { key ->
            assertTrue(db.threadStateDao().find(ThreadId.of("example.com", "test", key)) != null)
        }

        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
