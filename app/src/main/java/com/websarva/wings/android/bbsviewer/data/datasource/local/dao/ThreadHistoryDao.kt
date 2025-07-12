package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.ThreadHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadHistoryDao {
    @Query("SELECT * FROM thread_histories ORDER BY lastAccess DESC")
    fun observeHistories(): Flow<List<ThreadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(history: ThreadHistoryEntity)

    @Query("DELETE FROM thread_histories WHERE threadKey = :threadKey AND boardUrl = :boardUrl")
    suspend fun delete(threadKey: String, boardUrl: String)
}
