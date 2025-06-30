package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardCategoryCrossRef
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

    /** 指定サービスで板名に一致するカテゴリID一覧 */
    @Query(
        """
        SELECT DISTINCT bc.categoryId
          FROM board_category_cross_ref AS bc
          INNER JOIN boards AS b ON bc.boardId = b.boardId
         WHERE b.serviceId = :serviceId AND b.name LIKE '%' || :query || '%'
        """
    )
    fun findCategoryIdsForBoardName(serviceId: Long, query: String): Flow<List<Long>>
}
