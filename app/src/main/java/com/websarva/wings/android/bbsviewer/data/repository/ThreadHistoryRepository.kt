package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ThreadHistoryDao
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.ThreadHistoryAccessEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.ThreadHistoryEntity
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThreadHistoryRepository @Inject constructor(
    private val dao: ThreadHistoryDao
) {
    fun observeHistories(): Flow<List<ThreadHistoryDao.HistoryWithLastAccess>> =
        dao.observeHistories()

    suspend fun recordHistory(
        boardInfo: BoardInfo,
        threadInfo: ThreadInfo,
        resCount: Int
    ) {
        val existing = dao.find(threadInfo.key, boardInfo.url)
        val history = ThreadHistoryEntity(
            id = existing?.id ?: 0,
            threadKey = threadInfo.key,
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
        val lastAccess = dao.getLastAccess(id)
        val day1 = now / 86_400_000L
        val day2 = lastAccess?.div(86_400_000L)
        if (lastAccess == null || day1 != day2) {
            dao.insertAccess(
                ThreadHistoryAccessEntity(
                    threadHistoryId = id,
                    accessedAt = now
                )
            )
        }
    }
}
