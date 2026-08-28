package com.websarva.wings.android.slevo.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.websarva.wings.android.slevo.R

/**
 * アプリで利用する返信通知チャネルの定義を管理するobject。
 */
object NotificationChannels {
    /** 返信通知チャネルの安定したID。 */
    const val REPLY_NOTIFICATION_CHANNEL_ID = "reply_notifications"

    /** API 26以上で返信通知チャネルを冪等に作成する。 */
    fun createReplyNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val channel = NotificationChannel(
            REPLY_NOTIFICATION_CHANNEL_ID,
            context.getString(R.string.reply_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reply_notification_channel_description)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
