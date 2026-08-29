package com.websarva.wings.android.slevo.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.websarva.wings.android.slevo.MainActivity
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.datasource.local.entity.notification.ReplyNotificationEntity
import com.websarva.wings.android.slevo.data.notification.ReplyNotificationPublishResult
import com.websarva.wings.android.slevo.data.notification.NotificationPermissionChecker
import com.websarva.wings.android.slevo.data.notification.ReplyNotificationPublisher
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android NotificationManagerCompatを使って返信通知を投稿するPublisher。
 *
 * 通知権限とシステム通知設定を確認し、対象スレッドのDeep Linkをcontent intentへ設定する。
 */
@Singleton
class AndroidReplyNotificationPublisher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationPermissionChecker: NotificationPermissionChecker,
) : ReplyNotificationPublisher {
    /** 通知を投稿できる状態を確認して、返信通知を一件投稿する。 */
    override fun publish(notification: ReplyNotificationEntity): ReplyNotificationPublishResult {
        if (!notificationPermissionChecker.isNotificationAllowed()) {
            return ReplyNotificationPublishResult.SUPPRESSED
        }

        return try {
            val manager = NotificationManagerCompat.from(context)
            val pendingIntent = createThreadPendingIntent(notification)
            val preview = notification.messagePreview.ifBlank {
                context.getString(R.string.reply_notification_empty_preview)
            }
            val builder = NotificationCompat.Builder(
                context,
                NotificationChannels.REPLY_NOTIFICATION_CHANNEL_ID,
            )
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(notification.threadTitle.ifBlank {
                    context.getString(R.string.reply_notification_default_title)
                })
                .setContentText(preview)
                .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
            manager.notify(notificationId(notification), builder.build())
            ReplyNotificationPublishResult.DELIVERED
        } catch (_: SecurityException) {
            // APIや端末設定による通知禁止は取得処理から切り離して消化する。
            ReplyNotificationPublishResult.SUPPRESSED
        } catch (_: Exception) {
            ReplyNotificationPublishResult.RETRY
        }
    }

    /** 対象スレッドのURLを既存Deep Link処理へ渡すPendingIntentを作成する。 */
    private fun createThreadPendingIntent(notification: ReplyNotificationEntity): PendingIntent {
        val (host, board) = parseBoardUrl(notification.boardUrl)
            ?: error("Invalid board URL: ${notification.boardUrl}")
        val threadUrl = "https://$host/test/read.cgi/$board/${notification.threadKey}/"
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = threadUrl.toUri()
        }
        return PendingIntent.getActivity(
            context,
            notificationId(notification),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** スレッドとレス番号から再取得しても変わらない通知IDを生成する。 */
    private fun notificationId(notification: ReplyNotificationEntity): Int =
        31 * notification.threadId.value.hashCode() + notification.replyResNo
}
