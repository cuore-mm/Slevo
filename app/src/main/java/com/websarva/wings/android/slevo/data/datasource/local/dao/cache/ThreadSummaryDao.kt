package com.websarva.wings.android.slevo.data.datasource.local.dao.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.slevo.data.datasource.local.entity.cache.ThreadSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadSummaryDao {
    @Query("SELECT * FROM thread_summaries WHERE boardId = :boardId AND isArchived = 0 ORDER BY subjectRank ASC LIMIT :limit")
    fun observeThreadSummaries(boardId: Long, limit: Int = Int.MAX_VALUE): Flow<List<ThreadSummaryEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<ThreadSummaryEntity>)

    @Query("UPDATE thread_summaries SET title = :title, resCount = :resCount, isArchived = 0, subjectRank = :rank WHERE boardId = :boardId AND threadId = :threadId")
    suspend fun updateExisting(boardId: Long, threadId: String, title: String, resCount: Int, rank: Int)

    @Query("UPDATE thread_summaries SET isArchived = 1 WHERE boardId = :boardId AND threadId IN (:threadIds)")
    suspend fun markArchived(boardId: Long, threadIds: List<String>)

    @Query("SELECT threadId FROM thread_summaries WHERE boardId = :boardId")
    suspend fun getThreadIds(boardId: Long): List<String>
}
