package com.websarva.wings.android.slevo.ui.settings.backup

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [RestoreConfirmDialog] の metadata 表示、semantics、既存 action 条件を検証する。 */
@RunWith(AndroidJUnit4::class)
class RestoreConfirmDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 作成日時と source app version を読み上げ可能な一体の Text として表示する。 */
    @Test
    fun metadata_isDisplayedWithoutDatabaseVersion() {
        composeRule.setContent {
            SlevoTheme {
                RestoreConfirmDialog(
                    includeCookies = false,
                    containsCookies = true,
                    createdAt = "not-an-iso-date",
                    appVersionName = "1.5.2",
                    appVersionCode = 14,
                    onCookiesToggle = {},
                    onCancel = {},
                    onRestore = {},
                )
            }
        }

        composeRule.onNodeWithText("作成日時: not-an-iso-date").assertIsDisplayed()
        composeRule.onNodeWithText(
            "作成元アプリのバージョン: 1.5.2（バージョンコード 14）",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("クッキーを復元する").assertIsDisplayed()
        composeRule.onNodeWithText("db v", substring = true).assertDoesNotExist()
    }

    /** Cookie 無しでは checkbox を省略し、キャンセルと復元 callback を維持する。 */
    @Test
    fun callbacks_andCookieVisibility_arePreserved() {
        var cancelled = false
        var restored = false
        composeRule.setContent {
            MaterialTheme {
                RestoreConfirmDialog(
                    includeCookies = false,
                    containsCookies = false,
                    createdAt = "not-an-iso-date",
                    appVersionName = "1.5.2",
                    appVersionCode = 14,
                    onCookiesToggle = {},
                    onCancel = { cancelled = true },
                    onRestore = { restored = true },
                )
            }
        }

        composeRule.onNodeWithText("クッキーを復元する").assertDoesNotExist()
        composeRule.onNodeWithText("キャンセル").performClick()
        assertTrue(cancelled)

        composeRule.onNodeWithText("復元").performClick()
        assertTrue(restored)
    }
}
