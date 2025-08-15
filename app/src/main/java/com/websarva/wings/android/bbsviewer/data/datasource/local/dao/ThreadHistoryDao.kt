package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.ThreadHistoryAccessEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.ThreadHistoryEntity
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

    @Query("SELECT * FROM thread_histories WHERE threadKey = :threadKey AND boardUrl = :boardUrl LIMIT 1")
    suspend fun find(threadKey: String, boardUrl: String): ThreadHistoryEntity?
    @Query("SELECT threadKey, resCount FROM thread_histories WHERE boardUrl = :boardUrl")
    suspend fun findByBoard(boardUrl: String): List<HistorySimple>

    @Query("SELECT threadKey, resCount FROM thread_histories WHERE boardUrl = :boardUrl")
    fun observeByBoard(boardUrl: String): Flow<List<HistorySimple>>


    @Insert
    suspend fun insert(history: ThreadHistoryEntity): Long

    @Update
    suspend fun update(history: ThreadHistoryEntity)

    @Insert
    suspend fun insertAccess(access: ThreadHistoryAccessEntity)

    @Query(
        "SELECT accessedAt FROM thread_history_accesses WHERE threadHistoryId = :threadId " +
            "ORDER BY accessedAt DESC LIMIT 1"
    )
    suspend fun getLastAccess(threadId: Long): Long?

    @Query(
        "SELECT * FROM thread_history_accesses WHERE threadHistoryId = :threadId " +
            "ORDER BY accessedAt DESC LIMIT 1"
    )
    suspend fun getLastAccessEntity(threadId: Long): ThreadHistoryAccessEntity?

    @Update
    suspend fun updateAccess(access: ThreadHistoryAccessEntity)

    @Query("DELETE FROM thread_histories WHERE threadKey = :threadKey AND boardUrl = :boardUrl")
    suspend fun delete(threadKey: String, boardUrl: String)
}
