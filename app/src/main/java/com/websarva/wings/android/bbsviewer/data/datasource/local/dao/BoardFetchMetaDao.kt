package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardFetchMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardFetchMetaDao {
    @Query("SELECT * FROM board_fetch_meta WHERE boardId = :boardId")
    suspend fun get(boardId: Long): BoardFetchMetaEntity?

    @Query("SELECT * FROM board_fetch_meta WHERE boardId = :boardId")
    fun observe(boardId: Long): Flow<BoardFetchMetaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BoardFetchMetaEntity)
}
