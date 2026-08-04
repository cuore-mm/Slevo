package com.websarva.wings.android.slevo.data.repository

import androidx.room.withTransaction
import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.local.dao.history.PendingOwnPostDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PendingOwnPostEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PendingOwnPostStatus
import com.websarva.wings.android.slevo.data.model.OwnPostThreadScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 未確定の自分の投稿を永続化し、取得レスとの照合結果を履歴へ確定するRepository。
 *
 * PENDING行の検索は必ずスレッドスコープに限定し、MATCHED遷移と既存投稿履歴の保存は
 * 一つのwrite gateとRoom transactionの中で実行する。
 */
@Singleton
class PendingOwnPostRepository @Inject constructor(
    private val dao: PendingOwnPostDao,
    private val database: AppDatabase,
    private val postHistoryRepository: PostHistoryRepository,
    private val gate: DatabaseWriteGate,
) {
    /** 投稿成功情報をPENDINGとして保存し、期限切れterminal行を保守する。 */
    suspend fun createPending(
        scope: OwnPostThreadScope,
        content: String,
        name: String,
        email: String,
        baseResCount: Int,
        submittedAt: Long,
        expiresAt: Long = submittedAt + PENDING_LIFETIME_MILLIS,
    ): Long = gate.withWritePermit {
        dao.deleteOldTerminal(
            submittedBefore = submittedAt - TERMINAL_RETENTION_MILLIS,
            matchedStatus = PendingOwnPostStatus.MATCHED.name,
            expiredStatus = PendingOwnPostStatus.EXPIRED.name,
        )
        dao.insert(
            PendingOwnPostEntity(
                providerId = scope.providerId,
                boardKey = scope.boardKey,
                threadKey = scope.threadKey,
                content = content,
                name = name,
                email = email,
                baseResCount = baseResCount.coerceAtLeast(0),
                lastCheckedResNum = baseResCount.coerceAtLeast(0),
                submittedAt = submittedAt,
                expiresAt = expiresAt,
            )
        )
    }

    /** 指定スレッドのPENDING投稿だけを投稿時刻順で取得する。 */
    suspend fun findPending(scope: OwnPostThreadScope): List<PendingOwnPostEntity> =
        dao.findPending(
            providerId = scope.providerId,
            boardKey = scope.boardKey,
            threadKey = scope.threadKey,
            pendingStatus = PendingOwnPostStatus.PENDING.name,
        )

    /** 指定スレッドで期限を迎えたPENDING投稿をEXPIREDへ遷移する。 */
    suspend fun expirePending(scope: OwnPostThreadScope, nowMillis: Long): Int =
        gate.withWritePermit {
            dao.expirePending(
                providerId = scope.providerId,
                boardKey = scope.boardKey,
                threadKey = scope.threadKey,
                nowMillis = nowMillis,
                pendingStatus = PendingOwnPostStatus.PENDING.name,
                expiredStatus = PendingOwnPostStatus.EXPIRED.name,
            )
        }

    /** 照合済みレスが無かった範囲を、同じPENDING投稿の次回照合から除外する。 */
    suspend fun updateLastCheckedResNum(id: Long, lastCheckedResNum: Int): Int =
        gate.withWritePermit {
            dao.updateLastCheckedResNum(
                id = id,
                lastCheckedResNum = lastCheckedResNum,
                pendingStatus = PendingOwnPostStatus.PENDING.name,
            )
        }

    /**
     * 一意に一致したレスを投稿履歴へ確定し、PENDINGをMATCHEDへ遷移する。
     *
     * 条件付きUPDATEが0件の場合は別処理で解決済みとみなし、履歴を重複保存しない。
     */
    suspend fun completeMatch(
        pending: PendingOwnPostEntity,
        matchedResNum: Int,
        date: Long,
        historyId: Long,
        boardId: Long,
        name: String,
        email: String,
        postId: String,
    ): Boolean = gate.withWritePermit {
        database.withTransaction {
            val updated = dao.markMatched(
                id = pending.id,
                matchedResNum = matchedResNum,
                pendingStatus = PendingOwnPostStatus.PENDING.name,
                matchedStatus = PendingOwnPostStatus.MATCHED.name,
            )
            if (updated != 1) {
                // 別のロードが先に確定した場合は履歴を重複追加しない。
                false
            } else {
                postHistoryRepository.recordPostUngated(
                    content = pending.content,
                    date = date,
                    threadHistoryId = historyId,
                    boardId = boardId,
                    resNum = matchedResNum,
                    name = name,
                    email = email,
                    postId = postId,
                )
                true
            }
        }
    }

    /** terminal状態の古い行を低頻度保守として削除する。 */
    suspend fun cleanupTerminal(submittedBefore: Long): Int = gate.withWritePermit {
        dao.deleteOldTerminal(
            submittedBefore = submittedBefore,
            matchedStatus = PendingOwnPostStatus.MATCHED.name,
            expiredStatus = PendingOwnPostStatus.EXPIRED.name,
        )
    }

    companion object {
        /** 投稿成功から照合を続ける期間。 */
        const val PENDING_LIFETIME_MILLIS = 24L * 60L * 60L * 1000L

        /** terminal状態を保持する期間。 */
        const val TERMINAL_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L
    }
}
