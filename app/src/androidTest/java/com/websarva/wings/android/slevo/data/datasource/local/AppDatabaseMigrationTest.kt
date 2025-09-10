package com.websarva.wings.android.slevo.data.datasource.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_addsColumns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)

        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "CREATE TABLE IF NOT EXISTS open_thread_tabs (" +
                    "threadKey TEXT NOT NULL PRIMARY KEY, " +
                    "boardUrl TEXT NOT NULL, " +
                    "boardId INTEGER NOT NULL, " +
                    "boardName TEXT NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "resCount INTEGER NOT NULL, " +
                    "sortOrder INTEGER NOT NULL, " +
                    "firstVisibleItemIndex INTEGER NOT NULL, " +
                    "firstVisibleItemScrollOffset INTEGER NOT NULL" +
                    ")"
            )
            close()
        }

        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build().apply {
                openHelper.writableDatabase
                val cursor = query("PRAGMA table_info('open_thread_tabs')")
                val columns = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                cursor.close()
                close()
                assertTrue(columns.contains("lastReadResNo"))
                assertTrue(columns.contains("firstNewResNo"))
                assertTrue(columns.contains("prevResCount"))
            }
    }

    @Test
    fun migrate2To3_replacesThreadKey() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)

        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "CREATE TABLE IF NOT EXISTS open_thread_tabs (" +
                    "threadKey TEXT NOT NULL PRIMARY KEY, " +
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
                    "firstVisibleItemScrollOffset INTEGER NOT NULL" +
                    ")"
            )
            execSQL(
                "CREATE TABLE IF NOT EXISTS thread_histories (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "threadKey TEXT NOT NULL, " +
                    "boardUrl TEXT NOT NULL, " +
                    "boardId INTEGER NOT NULL, " +
                    "boardName TEXT NOT NULL, " +
                    "title TEXT NOT NULL, " +
                    "resCount INTEGER NOT NULL" +
                    ")"
            )
            execSQL(
                "CREATE TABLE IF NOT EXISTS thread_history_accesses (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "threadHistoryId INTEGER NOT NULL, " +
                    "accessedAt INTEGER NOT NULL" +
                    ")"
            )
            execSQL(
                "INSERT INTO open_thread_tabs (threadKey, boardUrl, boardId, boardName, title, resCount, prevResCount, lastReadResNo, firstNewResNo, sortOrder, firstVisibleItemIndex, firstVisibleItemScrollOffset) VALUES ('123', 'https://example.com/board', 1, 'board', 'title', 10, 0, 0, NULL, 0, 0, 0)"
            )
            execSQL(
                "INSERT INTO thread_histories (threadKey, boardUrl, boardId, boardName, title, resCount) VALUES ('123', 'https://example.com/board', 1, 'board', 'title', 10)"
            )
            execSQL(
                "INSERT INTO thread_history_accesses (threadHistoryId, accessedAt) VALUES (1, 0)"
            )
            close()
        }

        Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .build().apply {
                openHelper.writableDatabase
                val cursor = query("SELECT threadId FROM open_thread_tabs")
                cursor.moveToFirst()
                val threadId = cursor.getString(0)
                cursor.close()
                assertEquals("example.com/board/123", threadId)

                val pragma = query("PRAGMA table_info('open_thread_tabs')")
                val columns = mutableListOf<String>()
                while (pragma.moveToNext()) {
                    columns.add(pragma.getString(pragma.getColumnIndexOrThrow("name")))
                }
                pragma.close()
                close()
                assertFalse(columns.contains("threadKey"))
            }
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
