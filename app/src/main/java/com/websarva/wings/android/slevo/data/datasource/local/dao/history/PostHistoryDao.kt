package com.websarva.wings.android.slevo.data.datasource.local.dao.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PostHistoryEntity

@Dao
interface PostHistoryDao {
    @Insert
    suspend fun insert(history: PostHistoryEntity)

    @Query("SELECT resNum FROM post_histories WHERE threadHistoryId = :threadHistoryId")
    fun observeResNums(threadHistoryId: Long): Flow<List<Int>>
}
