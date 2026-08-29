package com.websarva.wings.android.slevo.ui.settings

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 返信通知警告から開くOS設定IntentのAPI互換分岐を検証する。 */
@RunWith(RobolectricTestRunner::class)
class SettingsGeneralScreenUnitTest {
    /** Android 8以上ではアプリ通知設定画面を対象にする。 */
    @Test
    @Config(sdk = [Build.VERSION_CODES.O])
    fun notificationSettingsIntent_usesAppNotificationSettingsFromOreo() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = notificationSettingsIntent(context)

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    /** Android 7.1以下では通知専用画面の代わりにアプリ詳細設定を対象にする。 */
    @Test
    @Config(sdk = [Build.VERSION_CODES.N_MR1])
    fun notificationSettingsIntent_usesApplicationDetailsBeforeOreo() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = notificationSettingsIntent(context)

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }
}
