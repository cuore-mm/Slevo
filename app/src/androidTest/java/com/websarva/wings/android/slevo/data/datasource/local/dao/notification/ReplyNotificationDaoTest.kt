package com.websarva.wings.android.slevo.data.datasource.local.dao.notification

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationStatus
import com.websarva.wings.android.slevo.data.model.ThreadId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room生成DAOのinsert-ignore、抽出順、条件付きstatus更新を検証する。 */
@RunWith(AndroidJUnit4::class)
class ReplyNotificationDaoTest {
    private lateinit var database: AppDatabase

    /** 各テストが独立したRoomデータベースを使うよう準備する。 */
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    /** テスト用データベースを閉じてリソースを解放する。 */
    @After
    fun tearDown() {
        database.close()
    }

    /** insert-ignoreと条件付きstatus更新が再実行可能なDAO契約であることを確認する。 */
    @Test
    fun insertIgnore_andConditionalStatusUpdate_areIdempotent() = runBlocking {
        val dao = database.replyNotificationDao()
        val entity = ReplyNotificationEntity(
            threadId = ThreadId.of("example.com", "test", "123"),
            replyResNo = 3,
            targetOwnResNumbers = "2",
            boardUrl = "https://example.com/test/",
            threadKey = "123",
            threadTitle = "Thread",
            messagePreview = ">>2 reply",
            detectedAt = 100L,
        )

        assertEquals(1L, dao.insertIgnore(entity))
        assertEquals(-1L, dao.insertIgnore(entity))
        assertEquals(
            listOf(entity),
            dao.findByThreadAndStatus(entity.threadId, ReplyNotificationStatus.DETECTED.name),
        )
        assertEquals(
            1,
            dao.updateStatus(
                entity.threadId,
                entity.replyResNo,
                ReplyNotificationStatus.DETECTED.name,
                ReplyNotificationStatus.DELIVERED.name,
            ),
        )
        assertEquals(
            0,
            dao.updateStatus(
                entity.threadId,
                entity.replyResNo,
                ReplyNotificationStatus.DETECTED.name,
                ReplyNotificationStatus.SUPPRESSED.name,
            ),
        )
    }
}
