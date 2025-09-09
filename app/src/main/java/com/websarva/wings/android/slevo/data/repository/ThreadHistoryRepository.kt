package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.ThreadHistoryAccessEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.ThreadHistoryEntity
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.data.model.threadKey
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadHistoryRepository @Inject constructor(
    private val dao: ThreadHistoryDao
) {
    fun observeHistories(): Flow<List<ThreadHistoryDao.HistoryWithLastAccess>> =
        dao.observeHistories()

    fun observeHistoryMap(boardUrl: String): Flow<Map<String, Int>> =
        dao.observeByBoard(boardUrl).map { list ->
            list.associate { it.threadId.threadKey to it.resCount }
        }

    suspend fun getHistoryMap(boardUrl: String): Map<String, Int> {
        return dao.findByBoard(boardUrl).associate { it.threadId.threadKey to it.resCount }
    }

    suspend fun recordHistory(
        boardInfo: BoardInfo,
        threadInfo: ThreadInfo,
        resCount: Int
    ): Long {
        val (host, board) = parseBoardUrl(boardInfo.url) ?: return 0
        val threadId = ThreadId.of(host, board, threadInfo.key)
        val existing = dao.find(threadId)
        val history = ThreadHistoryEntity(
            id = existing?.id ?: 0,
            threadId = threadId,
            boardUrl = boardInfo.url,
            boardId = boardInfo.boardId,
            boardName = boardInfo.name,
            title = threadInfo.title,
            resCount = resCount
        )
        val id = if (existing == null) {
            dao.insert(history)
        } else {
            dao.update(history)
            existing.id
        }

        val now = System.currentTimeMillis()
        val last = dao.getLastAccessEntity(id)
        val dayNow = now / 86_400_000L
        val dayLast = last?.accessedAt?.div(86_400_000L)
        if (last == null) {
            dao.insertAccess(
                ThreadHistoryAccessEntity(
                    threadHistoryId = id,
                    accessedAt = now
                )
            )
        } else if (dayNow == dayLast) {
            dao.updateAccess(last.copy(accessedAt = now))
        } else {
            dao.insertAccess(
                ThreadHistoryAccessEntity(
                    threadHistoryId = id,
                    accessedAt = now
                )
            )
        }
        return id
    }
}
