package com.websarva.wings.android.slevo.data.datasource.local.dao.notification

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationEntity
import com.websarva.wings.android.slevo.data.model.ThreadId

/**
 * 返信通知の一意登録と配信状態更新を行うDAO。
 *
 * 複合主キーへのIGNORE登録結果を使い、新規に検出されたレスだけを通知処理へ渡す。
 */
@Dao
interface ReplyNotificationDao {
    /** 同一返信を上書きせずに登録し、新規登録時だけrow id相当の値を返す。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: ReplyNotificationEntity): Long

    /** 指定スレッドの未処理通知を検出時刻順に取得する。 */
    @Query(
        "SELECT * FROM reply_notifications " +
            "WHERE threadId = :threadId AND status = :status ORDER BY detectedAt ASC, replyResNo ASC",
    )
    suspend fun findByThreadAndStatus(threadId: ThreadId, status: String): List<ReplyNotificationEntity>

    /** 現在のstatusが一致する場合だけ、競合せずに次のstatusへ遷移する。 */
    @Query(
        "UPDATE reply_notifications SET status = :nextStatus " +
            "WHERE threadId = :threadId AND replyResNo = :replyResNo AND status = :currentStatus",
    )
    suspend fun updateStatus(
        threadId: ThreadId,
        replyResNo: Int,
        currentStatus: String,
        nextStatus: String,
    ): Int
}
