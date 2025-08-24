package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.BoardEntity
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.bbs.BoardWithCategories
import kotlinx.coroutines.flow.Flow

/**
 * ボード(板)データを管理するDAOインターフェース
 */
@Dao
interface BoardDao {
    /** URL 一意制約を利用して、重複挿入時は何もしない */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBoard(board: BoardEntity): Long

    /** URL から boardId を取得 */
    @Query("SELECT boardId FROM boards WHERE url = :url LIMIT 1")
    suspend fun findBoardIdByUrl(url: String): Long

    @Query("DELETE FROM boards WHERE serviceId = :serviceId")
    suspend fun clearForService(serviceId: Long)

    @Query("SELECT * FROM boards WHERE serviceId = :serviceId")
    fun getBoardsForService(serviceId: Long): Flow<List<BoardEntity>>

    @Transaction
    @Query("SELECT * FROM boards WHERE boardId = :boardId")
    fun getBoardWithCategories(boardId: Long): Flow<BoardWithCategories>

    @Query("SELECT * FROM boards WHERE boardId IN (:ids)")
    fun getBoardsByIds(ids: List<Long>): Flow<List<BoardEntity>>

    @Query("SELECT * FROM boards")
    fun getAllBoards(): Flow<List<BoardEntity>>
}
