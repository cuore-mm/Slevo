package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.BoardCategoryCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface BoardCategoryCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(ref: BoardCategoryCrossRef)

    @Query("DELETE FROM board_category_cross_ref WHERE boardId = :boardId")
    suspend fun clearForBoard(boardId: Long)

    @Query("SELECT * FROM board_category_cross_ref WHERE categoryId = :categoryId")
    fun getCrossRefsForCategory(categoryId: Long): Flow<List<BoardCategoryCrossRef>>

    @Query("SELECT boardId FROM board_category_cross_ref WHERE categoryId = :categoryId")
    fun getBoardIdsForCategory(categoryId: Long): Flow<List<Long>>
}
