package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostIdentityHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PostLastIdentityDao
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import com.websarva.wings.android.slevo.data.model.ThreadId
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 返信検出が利用する履歴と確定済み自レス番号の一回取得を検証する。 */
class HistoryRepositoryReplyNotificationTest {
    /** 履歴がないスレッドを返信検出対象から除外できることを確認する。 */
    @Test
    fun threadHistory_missingHistory_returnsNull() = runTest {
        val dao = mockk<ThreadHistoryDao>(relaxed = true)
        val threadId = ThreadId.of("example.com", "test", "123")
        coEvery { dao.find(threadId) } returns null
        val repository = ThreadHistoryRepository(
            dao = dao,
            threadStateRepository = mockk(relaxed = true),
            gate = DatabaseWriteGate(),
        )

        assertNull(repository.getHistory(threadId))
    }

    /** 自レス番号の重複を除いた集合を一度の取得結果として返すことを確認する。 */
    @Test
    fun postHistory_returnsUniqueConfirmedResponseNumbers() = runTest {
        val postDao = mockk<PostHistoryDao>(relaxed = true)
        coEvery { postDao.findResNums(7L) } returns listOf(4, 2, 4)
        val repository = PostHistoryRepository(
            dao = postDao,
            identityDao = mockk<PostIdentityHistoryDao>(relaxed = true),
            lastIdentityDao = mockk<PostLastIdentityDao>(relaxed = true),
            gate = DatabaseWriteGate(),
        )

        assertEquals(setOf(2, 4), repository.getMyPostNumbers(7L))
    }
}
