package com.websarva.wings.android.slevo.data.datasource.local.dao.state

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.websarva.wings.android.slevo.data.datasource.local.entity.state.ThreadStateEntity
import com.websarva.wings.android.slevo.data.model.ThreadId
import kotlinx.coroutines.flow.Flow

/**
 * スレッド客観状態を読み書きする DAO。
 * `thread_states` を `threadId`、板、開いているタブ単位で取得し、既存行がある場合は
 * 最新レス数を小さく戻さない形で更新する。
 */
@Dao
abstract class ThreadStateDao {
    @Query("SELECT * FROM thread_states WHERE threadId = :threadId LIMIT 1")
    abstract suspend fun find(threadId: ThreadId): ThreadStateEntity?

    @Query("SELECT * FROM thread_states WHERE threadId = :threadId LIMIT 1")
    abstract fun observe(threadId: ThreadId): Flow<ThreadStateEntity?>

    @Query("SELECT * FROM thread_states WHERE boardId = :boardId ORDER BY updatedAt DESC")
    abstract fun observeByBoard(boardId: Long): Flow<List<ThreadStateEntity>>

    @Query("SELECT * FROM thread_states WHERE boardId = :boardId ORDER BY updatedAt DESC")
    abstract suspend fun findByBoard(boardId: Long): List<ThreadStateEntity>

    @Query(
        "SELECT s.threadId FROM thread_states s " +
            "LEFT JOIN open_thread_tabs t ON t.threadId = s.threadId " +
            "LEFT JOIN thread_histories h ON h.threadId = s.threadId " +
            "LEFT JOIN bookmark_threads b ON b.boardUrl = s.boardUrl AND b.threadKey = s.threadKey " +
            "LEFT JOIN thread_summaries ts ON ts.boardId = s.boardId " +
            "AND ts.threadId = s.threadKey " +
            "WHERE s.updatedAt < :updatedBefore " +
            "AND t.threadId IS NULL " +
            "AND h.threadId IS NULL " +
            "AND b.threadKey IS NULL " +
            "AND ts.threadId IS NULL " +
            "ORDER BY s.updatedAt ASC LIMIT :limit"
    )
    abstract suspend fun findGarbageCandidates(updatedBefore: Long, limit: Int): List<ThreadId>

    @Query("DELETE FROM thread_states WHERE threadId IN (:threadIds)")
    abstract suspend fun deleteByThreadIds(threadIds: List<ThreadId>)

    @Query(
        "SELECT s.* FROM thread_states s " +
            "INNER JOIN open_thread_tabs t ON t.threadId = s.threadId " +
            "ORDER BY t.sortOrder ASC"
    )
    abstract fun observeForOpenTabs(): Flow<List<ThreadStateEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIgnore(entity: ThreadStateEntity): Long

    @Query(
        "UPDATE thread_states SET " +
            "boardId = :boardId, " +
            "boardUrl = :boardUrl, " +
            "boardName = :boardName, " +
            "threadKey = :threadKey, " +
            "title = :title, " +
            "latestResCount = CASE " +
            "WHEN latestResCount < :latestResCount THEN :latestResCount ELSE latestResCount END, " +
            "updatedAt = :updatedAt " +
            "WHERE threadId = :threadId"
    )
    abstract suspend fun updateKeepingMaxResCount(
        threadId: ThreadId,
        boardId: Long,
        boardUrl: String,
        boardName: String,
        threadKey: String,
        title: String,
        latestResCount: Int,
        updatedAt: Long,
    )

    /**
     * 1件のスレッド客観状態を挿入または更新する。
     * 既存行がある場合はレス数を最大値に保ち、タイトルと板情報は直近の入力で更新する。
     */
    @Transaction
    open suspend fun upsertKeepingMaxResCount(entity: ThreadStateEntity) {
        insertIgnore(entity)
        updateKeepingMaxResCount(
            threadId = entity.threadId,
            boardId = entity.boardId,
            boardUrl = entity.boardUrl,
            boardName = entity.boardName,
            threadKey = entity.threadKey,
            title = entity.title,
            latestResCount = entity.latestResCount,
            updatedAt = entity.updatedAt,
        )
    }

    /**
     * 複数のスレッド客観状態を順に挿入または更新する。
     * Room のトランザクション内で処理し、一覧更新時の中間状態を外部へ見せない。
     */
    @Transaction
    open suspend fun upsertAllKeepingMaxResCount(entities: List<ThreadStateEntity>) {
        entities.forEach { entity -> upsertKeepingMaxResCount(entity) }
    }

    /**
     * 古い孤立スレッド客観状態を削除する。
     * 削除候補を先に上限件数分だけ取得し、同じトランザクション内でまとめて削除する。
     */
    @Transaction
    open suspend fun deleteGarbage(updatedBefore: Long, limit: Int): Int {
        val targets = findGarbageCandidates(updatedBefore, limit)
        if (targets.isEmpty()) {
            // Guard: 候補が無い場合は DELETE を発行しない。
            return 0
        }
        deleteByThreadIds(targets)
        return targets.size
    }
}
