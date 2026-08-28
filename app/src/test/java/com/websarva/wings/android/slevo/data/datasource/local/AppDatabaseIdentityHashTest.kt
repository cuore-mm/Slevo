package com.websarva.wings.android.slevo.data.datasource.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.websarva.wings.android.slevo.data.backup.restore.RealBackupDatabaseValidator
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Room が生成する現在 schema の identity hash と復元検証値の一致を確認する。 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseIdentityHashTest {
    @Test
    fun generatedIdentityHash_matchesBackupValidatorExpectation() {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        val actualHash = database.openHelper.writableDatabase.query(
            "SELECT identity_hash FROM room_master_table WHERE id = 42",
        ).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }
        database.close()

        assertEquals(RealBackupDatabaseValidator.EXPECTED_IDENTITY_HASH, actualHash)
    }
}
