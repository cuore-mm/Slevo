package com.websarva.wings.android.slevo.data.datasource.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
