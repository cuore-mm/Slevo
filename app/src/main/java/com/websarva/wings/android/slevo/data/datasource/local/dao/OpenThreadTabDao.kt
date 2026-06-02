package com.websarva.wings.android.slevo.data.datasource.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.websarva.wings.android.slevo.data.datasource.local.entity.OpenThreadTabEntity
import com.websarva.wings.android.slevo.data.model.ThreadId
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenThreadTabDao {
    /**
     * タブ固有状態、スレッド客観状態、履歴既読状態を合成して返す投影モデル。
     * タブ一覧表示では `open_thread_tabs` の並び順とスクロール位置に、共通状態と履歴を JOIN して使う。
     */
    data class OpenThreadTabWithState(
        val threadId: ThreadId,
        val boardUrl: String,
        val boardId: Long,
        val boardName: String,
        val title: String,
        val latestResCount: Int,
        val sortOrder: Int,
        val isPinned: Boolean,
        val firstVisibleItemIndex: Int,
        val firstVisibleItemScrollOffset: Int,
        val historyPrevResCount: Int?,
        val historyLastReadResNo: Int?,
        val historyFirstNewResNo: Int?,
        val hasHistory: Boolean,
    )

    @Query("SELECT * FROM open_thread_tabs ORDER BY sortOrder ASC")
    fun observeOpenThreadTabs(): Flow<List<OpenThreadTabEntity>>

    @Query(
        "SELECT " +
            "t.threadId AS threadId, " +
            "s.boardUrl AS boardUrl, " +
            "s.boardId AS boardId, " +
            "s.boardName AS boardName, " +
            "s.title AS title, " +
            "s.latestResCount AS latestResCount, " +
            "t.sortOrder AS sortOrder, " +
            "t.isPinned AS isPinned, " +
            "t.firstVisibleItemIndex AS firstVisibleItemIndex, " +
            "t.firstVisibleItemScrollOffset AS firstVisibleItemScrollOffset, " +
            "h.prevResCount AS historyPrevResCount, " +
            "h.lastReadResNo AS historyLastReadResNo, " +
            "h.firstNewResNo AS historyFirstNewResNo, " +
            "CASE WHEN h.threadId IS NULL THEN 0 ELSE 1 END AS hasHistory " +
            "FROM open_thread_tabs t " +
            "INNER JOIN thread_states s ON s.threadId = t.threadId " +
            "LEFT JOIN thread_histories h ON h.threadId = t.threadId " +
            "ORDER BY t.sortOrder ASC"
    )
    fun observeOpenThreadTabsWithState(): Flow<List<OpenThreadTabWithState>>

    @Query("SELECT * FROM open_thread_tabs")
    suspend fun getAll(): List<OpenThreadTabEntity>

    @Upsert
    suspend fun upsertAll(tabs: List<OpenThreadTabEntity>)

    @Query("DELETE FROM open_thread_tabs WHERE threadId NOT IN (:ids)")
    suspend fun deleteNotIn(ids: List<String>)

    @Query("DELETE FROM open_thread_tabs")
    suspend fun deleteAll()

    /**
     * 指定 threadId のタブ固有スクロール位置だけを更新する。
     * 対象タブが存在しない場合は 0 を返し、no-op として扱う。
     */
    @Query(
        "UPDATE open_thread_tabs SET " +
            "firstVisibleItemIndex = :firstVisibleItemIndex, " +
            "firstVisibleItemScrollOffset = :firstVisibleItemScrollOffset " +
            "WHERE threadId = :threadId"
    )
    suspend fun updateThreadScrollPosition(
        threadId: ThreadId,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ): Int

}
