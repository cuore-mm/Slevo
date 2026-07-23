package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
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
 *
 * Phase 2 (add-database-write-gate) の移行パターンとして、
 * public 書き込み method は外側で `DatabaseWriteGate.withWritePermit { ... }` を取得し、
 * 内側 helper として `*Ungated` を呼ぶ形にする。
 */
@Singleton
class ThreadStateRepository @Inject constructor(
    private val dao: ThreadStateDao,
    private val gate: DatabaseWriteGate,
) {
    /**
     * GC の保持期間と削除件数上限をまとめる定数置き場。
     */
    companion object {
        private const val GARBAGE_TTL_MILLIS = 30L * 24 * 60 * 60 * 1000
        private const val DEFAULT_GARBAGE_LIMIT = 100
        private const val STARTUP_GARBAGE_LIMIT = 20
    }

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
        gate.withWritePermit { saveThreadStateUngated(update) }
    }

    /**
     * 複数のスレッド客観状態をまとめて保存する。
     * 一覧更新で得たスレッド群を同じ更新時刻で保存し、レス数は既存値より小さく戻さない。
     */
    suspend fun saveThreadStates(updates: List<ThreadStateUpdate>) {
        gate.withWritePermit { saveThreadStatesUngated(updates) }
    }

    /**
     * 参照がなく、30日以上更新されていないスレッド客観状態を削除する。
     * タブ・履歴・ブックマーク・保持中の板一覧キャッシュのいずれかから参照される行は残す。
     */
    suspend fun collectGarbage(
        nowMillis: Long = System.currentTimeMillis(),
        limit: Int = DEFAULT_GARBAGE_LIMIT,
    ): Int = gate.withWritePermit { collectGarbageUngated(nowMillis, limit) }

    /**
     * 起動時用の少量削除。`DatabaseCallback` から呼ばれる。
     */
    suspend fun collectStartupGarbage(): Int =
        gate.withWritePermit { collectStartupGarbageUngated() }

    // --- ungated helpers (Phase 2 で分離。Phase 3 で外側 gate + 内側 helper パターンに使う) ---

    /**
     * `saveThreadState` の内側実装。gate を取得せずに DAO を直接呼ぶ。
     * 外側 orchestration が `DatabaseWriteGate.withWritePermit { ... }` 内で利用すること。
     */
    internal suspend fun saveThreadStateUngated(update: ThreadStateUpdate) {
        dao.upsertKeepingMaxResCount(update.toEntity())
    }

    /**
     * `saveThreadStates` の内側実装。gate を取得せずに DAO を直接呼ぶ。
     * 外側 orchestration が `DatabaseWriteGate.withWritePermit { ... }` 内で利用すること。
     */
    internal suspend fun saveThreadStatesUngated(updates: List<ThreadStateUpdate>) {
        if (updates.isEmpty()) {
            // Guard: 空の一覧更新では DB 書き込みを行わない。
            return
        }
        dao.upsertAllKeepingMaxResCount(updates.map { update -> update.toEntity() })
    }

    /**
     * `collectGarbage` の内側実装。gate を取得せずに DAO を直接呼ぶ。
     * 外側 orchestration が `DatabaseWriteGate.withWritePermit { ... }` 内で利用すること。
     */
    internal suspend fun collectGarbageUngated(
        nowMillis: Long = System.currentTimeMillis(),
        limit: Int = DEFAULT_GARBAGE_LIMIT,
    ): Int {
        val normalizedLimit = limit.coerceAtLeast(0)
        if (normalizedLimit == 0) {
            // Guard: 上限 0 の呼び出しでは削除処理を行わない。
            return 0
        }
        return dao.deleteGarbage(
            updatedBefore = nowMillis - GARBAGE_TTL_MILLIS,
            limit = normalizedLimit,
        )
    }

    /**
     * 起動時用の少量 GC を内側で実行する。
     * `DatabaseCallback.onOpen` から `Provider<ThreadStateRepository>` 経由で呼ばれる。
     */
    internal suspend fun collectStartupGarbageUngated(): Int =
        collectGarbageUngated(limit = STARTUP_GARBAGE_LIMIT)

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
