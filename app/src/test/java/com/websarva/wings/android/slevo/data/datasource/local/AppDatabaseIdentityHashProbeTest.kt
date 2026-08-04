package com.websarva.wings.android.slevo.data.datasource.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Room v10の生成identity hashを確定するための一時probe。値確定後に削除する。 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseIdentityHashProbeTest {
    @Test
    fun generatedIdentityHash_matchesExpectedValue() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val hash = database.openHelper.writableDatabase.query(
            "SELECT identity_hash FROM room_master_table WHERE id = 42"
        ).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }
        database.close()

        assertEquals("f87f9edff16faf278567dbb60497a466", hash)
    }
}
