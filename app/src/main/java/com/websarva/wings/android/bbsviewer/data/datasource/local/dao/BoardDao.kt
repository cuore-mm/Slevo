package com.websarva.wings.android.bbsviewer.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import kotlinx.coroutines.flow.Flow

/**
 * ボード(板)データを管理するDAOインターフェース
 */
@Dao
interface BoardDao {
    /**
     * 指定サービスの指定カテゴリに属するボード一覧を取得
     * @param domain サービスドメイン
     * @param categoryName カテゴリ名
     * @return BoardEntity のリストを Flow で返す
     */
    @Query("""
    SELECT *
      FROM boards
     WHERE domain         = :domain
       AND categoryName   = :categoryName
  ORDER BY id ASC   -- ← 挿入順に並び替え
""")
    fun getBoards(
        domain: String,
        categoryName: String
    ): Flow<List<BoardEntity>>

    /**
     * ボードリストを一括で挿入または更新 (同一 URL は置換)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoards(boards: List<BoardEntity>)

    /**
     * 指定サービスのすべてのボードを削除
     * @param domain サービスドメイン
     */
    @Query("DELETE FROM boards WHERE domain = :domain")
    suspend fun clearBoardsForService(domain: String)

    /**
     * 指定のボードを削除
     */
    @Delete
    suspend fun deleteBoard(board: BoardEntity)
}
