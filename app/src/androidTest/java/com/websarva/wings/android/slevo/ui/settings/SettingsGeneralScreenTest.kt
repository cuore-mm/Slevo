package com.websarva.wings.android.slevo.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasRole
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.Role
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThemeMode
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 一般設定画面の返信通知文言とSwitchのchecked semanticsを検証する。 */
@RunWith(AndroidJUnit4::class)
class SettingsGeneralScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 返信通知項目が表示状態をUiStateから正しく投影することを確認する。 */
    @Test
    fun replyNotification_settingAndSwitchReflectState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var selectedValue: Boolean? = null
        composeRule.setContent {
            SlevoTheme {
                SettingsGeneralScreen(
                    themeMode = ThemeMode.SYSTEM,
                    isRedirect5chNetToIoEnabled = false,
                    isReplyNotificationEnabled = true,
                    onSelectThemeMode = {},
                    onToggleRedirect5chNetToIoEnabled = {},
                    onToggleReplyNotification = { selectedValue = it },
                    onNavigateUp = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.reply_notification_setting_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.reply_notification_setting_description))
            .assertIsDisplayed()
        composeRule.onAllNodes(hasRole(Role.Switch))[0].assertIsOn()
        composeRule.onAllNodes(hasRole(Role.Switch))[1].assertIsOff()
        assertEquals(null, selectedValue)
    }

    /** 通知不可かつ設定有効時に警告文を表示し、スイッチは有効状態を保つことを確認する。 */
    @Test
    fun replyNotification_permissionWarningUsesWarningText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.setContent {
            SlevoTheme {
                SettingsGeneralScreen(
                    themeMode = ThemeMode.SYSTEM,
                    isRedirect5chNetToIoEnabled = false,
                    isReplyNotificationEnabled = true,
                    isNotificationAllowed = false,
                    onSelectThemeMode = {},
                    onToggleRedirect5chNetToIoEnabled = {},
                    onToggleReplyNotification = {},
                    onNavigateUp = {},
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.reply_notification_permission_warning))
            .assertIsDisplayed()
        composeRule.onAllNodes(hasRole(Role.Switch))[0].assertIsOn()
    }
}
