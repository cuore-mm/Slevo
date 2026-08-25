package com.websarva.wings.android.slevo.data.datasource.local.dao.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PendingOwnPostEntity

/**
 * 未確定の自分の投稿を、対象スレッドと状態を条件に操作するDAO。
 *
 * PENDING行の取得は必ずprovider、板、スレッドの3キーを受け取り、別スレッドの投稿を
 * 照合層へ返さない。
 */
@Dao
interface PendingOwnPostDao {
    /** 未確定投稿を保存し、生成された主キーを返す。 */
    @Insert
    suspend fun insert(entity: PendingOwnPostEntity): Long

    /** 指定スレッドに属する未確定投稿を投稿時刻順で取得する。 */
    @Query(
        """
        SELECT * FROM pending_own_posts
        WHERE providerId = :providerId
          AND boardKey = :boardKey
          AND threadKey = :threadKey
          AND status = :pendingStatus
        ORDER BY submittedAt ASC, id ASC
        """,
    )
    suspend fun findPending(
        providerId: String,
        boardKey: String,
        threadKey: String,
        pendingStatus: String,
    ): List<PendingOwnPostEntity>

    /** 指定した未確定投稿の確認済み末尾番号を進める。 */
    @Query(
        """
        UPDATE pending_own_posts
        SET lastCheckedResNum = :lastCheckedResNum
        WHERE id = :id
          AND status = :pendingStatus
          AND lastCheckedResNum <= :lastCheckedResNum
        """,
    )
    suspend fun updateLastCheckedResNum(
        id: Long,
        lastCheckedResNum: Int,
        pendingStatus: String,
    ): Int

    /** 指定スレッドで期限を迎えた未確定投稿をEXPIREDへ遷移する。 */
    @Query(
        """
        UPDATE pending_own_posts
        SET status = :expiredStatus
        WHERE providerId = :providerId
          AND boardKey = :boardKey
          AND threadKey = :threadKey
          AND status = :pendingStatus
          AND expiresAt <= :nowMillis
        """,
    )
    suspend fun expirePending(
        providerId: String,
        boardKey: String,
        threadKey: String,
        nowMillis: Long,
        pendingStatus: String,
        expiredStatus: String,
    ): Int

    /** 未確定投稿をMATCHEDへ遷移し、確定したレス番号を保存する。 */
    @Query(
        """
        UPDATE pending_own_posts
        SET status = :matchedStatus,
            matchedResNum = :matchedResNum,
            lastCheckedResNum = :matchedResNum
        WHERE id = :id
          AND status = :pendingStatus
        """,
    )
    suspend fun markMatched(
        id: Long,
        matchedResNum: Int,
        pendingStatus: String,
        matchedStatus: String,
    ): Int

    /** 保存期間を過ぎたterminal状態の未確定投稿を削除する。 */
    @Query(
        """
        DELETE FROM pending_own_posts
        WHERE status IN (:matchedStatus, :expiredStatus)
          AND submittedAt < :submittedBefore
        """,
    )
    suspend fun deleteOldTerminal(
        submittedBefore: Long,
        matchedStatus: String,
        expiredStatus: String,
    ): Int
}
