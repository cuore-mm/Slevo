package com.websarva.wings.android.bbsviewer.data.datasource.local.dao.cache

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.cache.BoardVisitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardVisitDao {
    @Query("SELECT baselineAt FROM board_visits WHERE boardId = :boardId")
    fun observeBaseline(boardId: Long): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BoardVisitEntity)
}
