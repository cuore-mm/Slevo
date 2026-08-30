package com.websarva.wings.android.slevo.notification

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/** API 26以上の返信通知チャネルが冪等に高重要度で登録されることを検証する。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class NotificationChannelsTest {
    /** チャネル作成を複数回行っても高重要度の一チャネルだけが残ることを確認する。 */
    @Test
    fun createReplyNotificationChannel_isIdempotentAndHighImportance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(manager).setNotificationsEnabled(true)

        NotificationChannels.createReplyNotificationChannel(context)
        NotificationChannels.createReplyNotificationChannel(context)

        val channel = manager.getNotificationChannel(NotificationChannels.REPLY_NOTIFICATION_CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel?.importance)
        assertEquals(1, shadowOf(manager).notificationChannels.size)
    }
}
