package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.datasource.local.dao.notification.ReplyNotificationDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationStatus
import com.websarva.wings.android.slevo.data.model.ThreadId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** [ReplyNotificationRepository] の一意登録と競合時status更新を検証する。 */
class ReplyNotificationRepositoryTest {
    /** 受け取った候補のうち、新規複合主キーだけが呼び出し元へ返ることを確認する。 */
    @Test
    fun insertNew_returnsOnlyRowsInsertedByThisCall() = runTest {
        val dao = InMemoryReplyNotificationDao()
        val repository = ReplyNotificationRepository(dao, DatabaseWriteGate())
        val first = notification(replyResNo = 3)

        assertEquals(listOf(first), repository.insertNew(listOf(first, first)))
        assertEquals(emptyList<ReplyNotificationEntity>(), repository.insertNew(listOf(first)))
    }

    /** 同じ候補を並行登録しても、write gateと複合主キーで一行に収束することを確認する。 */
    @Test
    fun concurrentInsertNew_keepsSingleCompositeKeyRow() = runBlocking {
        val dao = InMemoryReplyNotificationDao()
        val repository = ReplyNotificationRepository(dao, DatabaseWriteGate())
        val notification = notification(replyResNo = 4)

        val results = listOf(
            async(Dispatchers.Default) { repository.insertNew(listOf(notification)) },
            async(Dispatchers.Default) { repository.insertNew(listOf(notification)) },
        ).awaitAll()

        assertEquals(1, results.flatten().size)
        assertEquals(1, dao.rows.size)
    }

    /** 現在statusが一致する更新だけが成功し、二重遷移を拒否することを確認する。 */
    @Test
    fun updateStatus_onlyChangesWhenCurrentStatusMatches() = runTest {
        val dao = InMemoryReplyNotificationDao()
        val repository = ReplyNotificationRepository(dao, DatabaseWriteGate())
        val notification = notification(replyResNo = 5)
        repository.insertNew(listOf(notification))

        assertEquals(
            true,
            repository.updateStatus(
                threadId = notification.threadId,
                replyResNo = notification.replyResNo,
                currentStatus = ReplyNotificationStatus.DETECTED,
                nextStatus = ReplyNotificationStatus.DELIVERED,
            ),
        )
        assertEquals(
            false,
            repository.updateStatus(
                threadId = notification.threadId,
                replyResNo = notification.replyResNo,
                currentStatus = ReplyNotificationStatus.DETECTED,
                nextStatus = ReplyNotificationStatus.SUPPRESSED,
            ),
        )
    }

    private fun notification(replyResNo: Int) = ReplyNotificationEntity(
        threadId = THREAD_ID,
        replyResNo = replyResNo,
        targetOwnResNumbers = "2",
        boardUrl = "https://example.com/test/",
        threadKey = "123",
        threadTitle = "Thread",
        messagePreview = ">>2 reply",
        detectedAt = replyResNo.toLong(),
    )

    /** Repositoryテスト用にRoom DAOの一意登録とstatus更新を再現するインメモリDAO。 */
    private class InMemoryReplyNotificationDao : ReplyNotificationDao {
        val rows = mutableMapOf<Pair<ThreadId, Int>, ReplyNotificationEntity>()

        override suspend fun insertIgnore(entity: ReplyNotificationEntity): Long {
            val key = entity.threadId to entity.replyResNo
            if (rows.putIfAbsent(key, entity) != null) return -1L
            return 1L
        }

        override suspend fun findByThreadAndStatus(
            threadId: ThreadId,
            status: String,
        ): List<ReplyNotificationEntity> = rows.values
            .filter { it.threadId == threadId && it.status == status }
            .sortedWith(compareBy<ReplyNotificationEntity> { it.detectedAt }.thenBy { it.replyResNo })

        override suspend fun updateStatus(
            threadId: ThreadId,
            replyResNo: Int,
            currentStatus: String,
            nextStatus: String,
        ): Int {
            val key = threadId to replyResNo
            val current = rows[key] ?: return 0
            if (current.status != currentStatus) return 0
            rows[key] = current.copy(status = nextStatus)
            return 1
        }
    }

    private companion object {
        val THREAD_ID = ThreadId.of("example.com", "test", "123")
    }
}
