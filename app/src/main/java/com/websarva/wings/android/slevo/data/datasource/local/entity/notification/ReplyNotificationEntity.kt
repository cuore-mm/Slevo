package com.websarva.wings.android.slevo.data.datasource.local.entity.notification

import androidx.room.Entity
import androidx.room.Index
import com.websarva.wings.android.slevo.data.model.ThreadId

/**
 * 検出した返信とAndroid通知の配信状態を保持するRoom Entity。
 *
 * 同一スレッドの同一レスを複合主キーで一意にし、取得経路をまたいだ重複通知を防ぐ。
 */
@Entity(
    tableName = "reply_notifications",
    primaryKeys = ["threadId", "replyResNo"],
    indices = [
        Index(value = ["threadId", "status"]),
        Index(value = ["detectedAt"]),
    ],
)
data class ReplyNotificationEntity(
    val threadId: ThreadId,
    val replyResNo: Int,
    val targetOwnResNumbers: String,
    val boardUrl: String,
    val threadKey: String,
    val threadTitle: String,
    val messagePreview: String,
    val detectedAt: Long,
    val status: String = ReplyNotificationStatus.DETECTED.name,
)

/**
 * 返信通知レコードの配信状態。
 *
 * `DETECTED` は未配信または一時失敗、`DELIVERED` はOS通知投稿済み、`SUPPRESSED` は
 * 設定またはOS権限により今後再試行しない状態を表す。
 */
enum class ReplyNotificationStatus {
    DETECTED,
    DELIVERED,
    SUPPRESSED,
}
