package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.datasource.local.dao.notification.ReplyNotificationDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationEntity
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationStatus
import com.websarva.wings.android.slevo.data.model.ThreadId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 返信通知レコードの永続化を仲介するRepository。
 *
 * 登録とstatus更新をDatabaseWriteGateで直列化し、同じ返信が複数取得経路から到着しても
 * 新規登録結果を一度だけ返す。
 */
@Singleton
class ReplyNotificationRepository @Inject constructor(
    private val dao: ReplyNotificationDao,
    private val gate: DatabaseWriteGate,
) {
    /** 返信候補を一意登録し、新規登録された候補だけを返す。 */
    suspend fun insertNew(candidates: List<ReplyNotificationEntity>): List<ReplyNotificationEntity> =
        gate.withWritePermit {
            candidates.filter { candidate -> dao.insertIgnore(candidate) != -1L }
        }

    /** 指定スレッドの一時失敗通知を配信再試行対象として取得する。 */
    suspend fun findDetected(threadId: ThreadId): List<ReplyNotificationEntity> =
        dao.findByThreadAndStatus(threadId, ReplyNotificationStatus.DETECTED.name)

    /** 配信状態を条件付きで更新し、更新に成功したかを返す。 */
    suspend fun updateStatus(
        threadId: ThreadId,
        replyResNo: Int,
        currentStatus: ReplyNotificationStatus,
        nextStatus: ReplyNotificationStatus,
    ): Boolean = gate.withWritePermit {
        dao.updateStatus(
            threadId = threadId,
            replyResNo = replyResNo,
            currentStatus = currentStatus.name,
            nextStatus = nextStatus.name,
        ) == 1
    }
}
