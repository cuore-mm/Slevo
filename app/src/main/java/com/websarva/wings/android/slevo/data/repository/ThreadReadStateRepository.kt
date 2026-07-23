package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.data.model.ThreadId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * スレッドの既読位置を閲覧履歴へ保存する Repository。
 * Phase 2 以降は `open_thread_tabs` を更新せず、履歴に紐づく `ThreadReadState` だけを正本として扱う。
 */
@Singleton
class ThreadReadStateRepository @Inject constructor(
    private val threadHistoryDao: ThreadHistoryDao,
    private val gate: DatabaseWriteGate,
) {
    /**
     * 指定スレッドの既読状態を履歴テーブルへ保存する。
     * 履歴がない場合は更新対象がないため、呼び出し元の履歴作成フローに委ねる。
     */
    suspend fun saveReadState(threadId: ThreadId, readState: ThreadReadState) {
        gate.withWritePermit {
            threadHistoryDao.updateReadState(
                threadId,
                readState.prevResCount,
                readState.lastReadResNo,
                readState.firstNewResNo,
            )
        }
    }
}
