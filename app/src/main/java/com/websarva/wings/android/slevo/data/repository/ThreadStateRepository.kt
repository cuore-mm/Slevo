package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.local.dao.state.ThreadStateDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.state.ThreadStateEntity
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.threadKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * スレッドの客観状態を保存する Repository。
 * 板更新、タブ一覧更新、スレッド閲覧で判明した最新レス数とタイトルを `thread_states` に集約し、
 * 既読位置は扱わない。
 */
@Singleton
class ThreadStateRepository @Inject constructor(
    private val dao: ThreadStateDao,
) {
    /**
     * 指定板のスレッド客観状態を板内 thread key で引ける Map として監視する。
     * 板一覧キャッシュと合成するときは `boardId + threadKey` の対応関係を保つ。
     */
    fun observeThreadStateMapByBoard(boardId: Long): Flow<Map<String, ThreadStateEntity>> =
        dao.observeByBoard(boardId).map { states -> states.associateBy { it.threadKey } }

    /**
     * 1件のスレッド客観状態を保存する。
     * `threadKey` は必ず `threadId` から導出し、冗長カラムと主キー内のキーを一致させる。
     */
    suspend fun saveThreadState(update: ThreadStateUpdate) {
        dao.upsertKeepingMaxResCount(update.toEntity())
    }

    /**
     * 複数のスレッド客観状態をまとめて保存する。
     * 一覧更新で得たスレッド群を同じ更新時刻で保存し、レス数は既存値より小さく戻さない。
     */
    suspend fun saveThreadStates(updates: List<ThreadStateUpdate>) {
        if (updates.isEmpty()) {
            // Guard: 空の一覧更新では DB 書き込みを行わない。
            return
        }
        dao.upsertAllKeepingMaxResCount(updates.map { update -> update.toEntity() })
    }

    /**
     * Repository 間で受け渡すスレッド客観状態の更新内容。
     * `threadKey` は保持せず、Entity 変換時に `threadId` から必ず導出する。
     */
    data class ThreadStateUpdate(
        val threadId: ThreadId,
        val boardId: Long,
        val boardUrl: String,
        val boardName: String,
        val title: String,
        val latestResCount: Int,
        val updatedAt: Long = System.currentTimeMillis(),
    ) {
        /**
         * DAO に渡す Room Entity へ変換する。
         * 出力 Entity の `threadKey` は `threadId` 末尾の板内キーと常に一致する。
         */
        fun toEntity(): ThreadStateEntity = ThreadStateEntity(
            threadId = threadId,
            boardId = boardId,
            boardUrl = boardUrl,
            boardName = boardName,
            threadKey = threadId.threadKey,
            title = title,
            latestResCount = latestResCount,
            updatedAt = updatedAt,
        )
    }
}
