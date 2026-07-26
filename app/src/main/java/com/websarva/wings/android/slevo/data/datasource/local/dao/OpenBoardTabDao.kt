package com.websarva.wings.android.slevo.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenBoardTabEntity
import kotlinx.coroutines.flow.Flow

/**
 * `open_board_tabs` の一覧と対象一行を操作する DAO。
 * 通常 command は対象行 API を使い、全件 API は明示 bulk／restore 経路だけが利用する。
 */
@Dao
interface OpenBoardTabDao {
    @Query("SELECT * FROM open_board_tabs ORDER BY sortOrder ASC")
    fun observeOpenBoardTabs(): Flow<List<OpenBoardTabEntity>>

    @Query("SELECT * FROM open_board_tabs")
    suspend fun getAll(): List<OpenBoardTabEntity>

    /** 指定 URL の板タブ一行を取得する。 */
    @Query("SELECT * FROM open_board_tabs WHERE boardUrl = :boardUrl")
    suspend fun getByBoardUrl(boardUrl: String): OpenBoardTabEntity?

    /** 新規板タブの安定した末尾順序を得る。 */
    @Query("SELECT MAX(sortOrder) FROM open_board_tabs")
    suspend fun getMaxSortOrder(): Int?

    /** 対象板タブ一行だけを追加または更新する。 */
    @Upsert
    suspend fun upsert(tab: OpenBoardTabEntity)

    /** 指定 URL の板タブ一行だけを削除する。 */
    @Query("DELETE FROM open_board_tabs WHERE boardUrl = :boardUrl")
    suspend fun deleteByBoardUrl(boardUrl: String): Int

    /** 指定板タブの pin 列だけを更新する。 */
    @Query("UPDATE open_board_tabs SET isPinned = :isPinned WHERE boardUrl = :boardUrl")
    suspend fun updatePinned(boardUrl: String, isPinned: Boolean): Int

    /** 指定板タブのスクロール列だけを更新する。 */
    @Query(
        "UPDATE open_board_tabs SET firstVisibleItemIndex = :firstVisibleItemIndex, " +
            "firstVisibleItemScrollOffset = :firstVisibleItemScrollOffset WHERE boardUrl = :boardUrl"
    )
    suspend fun updateScrollPosition(
        boardUrl: String,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ): Int

    @Upsert
    suspend fun upsertAll(tabs: List<OpenBoardTabEntity>)

    @Query("DELETE FROM open_board_tabs WHERE boardUrl NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("DELETE FROM open_board_tabs")
    suspend fun deleteAll()
}
