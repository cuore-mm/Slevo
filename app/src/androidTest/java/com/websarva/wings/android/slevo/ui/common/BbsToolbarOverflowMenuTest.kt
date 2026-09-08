package com.websarva.wings.android.slevo.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** BBS画面のその他メニューが画面種別ごとの項目を表示することを検証する。 */
class BbsToolbarOverflowMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 板画面では共通4項目だけを表示し、項目選択をcallbackへ通知する。 */
    @Test
    fun boardMenu_displaysFourCommonItemsWithoutDisplaySettings() {
        var settingsClickCount = 0
        composeRule.setContent {
            MaterialTheme {
                BbsToolbarMenuContent(
                    onBookmarkClick = {},
                    onBoardListClick = {},
                    onHistoryClick = {},
                    onSettingsClick = { settingsClickCount++ },
                )
            }
        }

        composeRule.onNodeWithText("ブックマーク").assertExists()
        composeRule.onNodeWithText("板一覧").assertExists()
        composeRule.onNodeWithText("履歴").assertExists()
        composeRule.onNodeWithText("設定").assertExists().performClick()
        composeRule.onNodeWithText("表示設定").assertDoesNotExist()

        assertEquals(1, settingsClickCount)
    }

    /** スレ画面向けでは表示設定を先頭に加えた5項目を表示する。 */
    @Test
    fun threadMenu_displaysDisplaySettingsWithCommonItems() {
        composeRule.setContent {
            MaterialTheme {
                BbsToolbarMenuContent(
                    onBookmarkClick = {},
                    onBoardListClick = {},
                    onHistoryClick = {},
                    onSettingsClick = {},
                    onDisplaySettingsClick = {},
                )
            }
        }

        composeRule.onNodeWithText("表示設定").assertExists()
        composeRule.onNodeWithText("ブックマーク").assertExists()
        composeRule.onNodeWithText("板一覧").assertExists()
        composeRule.onNodeWithText("履歴").assertExists()
        composeRule.onNodeWithText("設定").assertExists()
    }
}
