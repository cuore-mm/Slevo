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
}
