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
     * Phase 2 では旧タブ列を fallback として残し、`thread_states` が欠けてもタブを表示できる。
     */
    data class OpenThreadTabWithState(
        val threadId: ThreadId,
        val boardUrl: String,
        val boardId: Long,
        val boardName: String,
        val title: String,
        val latestResCount: Int,
        val sortOrder: Int,
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
            "COALESCE(s.boardUrl, t.boardUrl) AS boardUrl, " +
            "COALESCE(s.boardId, t.boardId) AS boardId, " +
            "COALESCE(s.boardName, t.boardName) AS boardName, " +
            "COALESCE(s.title, t.title) AS title, " +
            "COALESCE(s.latestResCount, t.resCount) AS latestResCount, " +
            "t.sortOrder AS sortOrder, " +
            "t.firstVisibleItemIndex AS firstVisibleItemIndex, " +
            "t.firstVisibleItemScrollOffset AS firstVisibleItemScrollOffset, " +
            "h.prevResCount AS historyPrevResCount, " +
            "h.lastReadResNo AS historyLastReadResNo, " +
            "h.firstNewResNo AS historyFirstNewResNo, " +
            "CASE WHEN h.threadId IS NULL THEN 0 ELSE 1 END AS hasHistory " +
            "FROM open_thread_tabs t " +
            "LEFT JOIN thread_states s ON s.threadId = t.threadId " +
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

    @Query(
        "UPDATE open_thread_tabs SET prevResCount = :prevResCount, lastReadResNo = :lastReadResNo, " +
            "firstNewResNo = :firstNewResNo WHERE threadId = :threadId"
    )
    suspend fun updateReadState(
        threadId: ThreadId,
        prevResCount: Int,
        lastReadResNo: Int,
        firstNewResNo: Int?,
    )
}
