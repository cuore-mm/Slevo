package com.websarva.wings.android.slevo.data.notification

import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationEntity

/**
 * 返信通知をOSへ配信する抽象化。
 *
 * Android APIの状態確認や通知投稿を呼び出し元から分離し、通知判定をJVM上でテスト可能にする。
 */
interface ReplyNotificationPublisher {
    /** 指定通知をOSへ投稿し、再試行可否を含む結果を返す。 */
    fun publish(notification: ReplyNotificationEntity): ReplyNotificationPublishResult
}

/**
 * 返信通知のOS投稿結果。
 */
enum class ReplyNotificationPublishResult {
    DELIVERED,
    SUPPRESSED,
    RETRY,
}
