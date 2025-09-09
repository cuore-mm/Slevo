package com.websarva.wings.android.slevo.data.datasource.local.dao.history

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.ThreadHistoryAccessEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.ThreadHistoryEntity
import com.websarva.wings.android.slevo.data.model.ThreadId
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadHistoryDao {
    data class HistoryWithLastAccess(
        @Embedded val history: ThreadHistoryEntity,
        val lastAccess: Long?
    )
    data class HistorySimple(
        val threadKey: String,
        val resCount: Int,
    )


    @Transaction
    @Query(
        "SELECT h.*, MAX(a.accessedAt) AS lastAccess FROM thread_histories h " +
            "LEFT JOIN thread_history_accesses a ON h.id = a.threadHistoryId " +
            "GROUP BY h.id ORDER BY lastAccess DESC"
    )
    fun observeHistories(): Flow<List<HistoryWithLastAccess>>

    @Query("SELECT * FROM thread_histories WHERE threadId = :threadId LIMIT 1")
    suspend fun find(threadId: ThreadId): ThreadHistoryEntity?
    @Query("SELECT threadKey, resCount FROM thread_histories WHERE boardUrl = :boardUrl")
    suspend fun findByBoard(boardUrl: String): List<HistorySimple>

    @Query("SELECT threadKey, resCount FROM thread_histories WHERE boardUrl = :boardUrl")
    fun observeByBoard(boardUrl: String): Flow<List<HistorySimple>>

    @Upsert
    suspend fun upsert(history: ThreadHistoryEntity): Long

    @Insert
    suspend fun insertAccess(access: ThreadHistoryAccessEntity)

    @Query(
        "SELECT accessedAt FROM thread_history_accesses WHERE threadHistoryId = :historyId " +
            "ORDER BY accessedAt DESC LIMIT 1"
    )
    suspend fun getLastAccess(historyId: Long): Long?

    @Query(
        "SELECT * FROM thread_history_accesses WHERE threadHistoryId = :historyId " +
            "ORDER BY accessedAt DESC LIMIT 1"
    )
    suspend fun getLastAccessEntity(historyId: Long): ThreadHistoryAccessEntity?

    @Update
    suspend fun updateAccess(access: ThreadHistoryAccessEntity)

    @Query("DELETE FROM thread_histories WHERE threadId = :threadId")
    suspend fun delete(threadId: ThreadId)
}
