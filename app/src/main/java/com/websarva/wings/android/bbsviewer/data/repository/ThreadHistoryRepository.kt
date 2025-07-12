package com.websarva.wings.android.bbsviewer.data.repository

import com.websarva.wings.android.bbsviewer.data.datasource.local.dao.ThreadHistoryDao
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
    fun observeHistories(): Flow<List<ThreadHistoryEntity>> = dao.observeHistories()

    suspend fun recordHistory(boardInfo: BoardInfo, threadInfo: ThreadInfo) {
        val entity = ThreadHistoryEntity(
            threadKey = threadInfo.key,
            boardUrl = boardInfo.url,
            boardId = boardInfo.boardId,
            boardName = boardInfo.name,
            title = threadInfo.title,
            resCount = threadInfo.resCount,
            lastAccess = System.currentTimeMillis()
        )
        dao.upsert(entity)
    }
}
