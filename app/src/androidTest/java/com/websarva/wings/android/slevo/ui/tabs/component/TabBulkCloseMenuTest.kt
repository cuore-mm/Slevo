package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitForIdle
import androidx.compose.ui.unit.IntRect
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * タブ一覧の一括クローズメニューと上部アクションのアクセシビリティを検証する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalHazeMaterialsApi::class)
class TabBulkCloseMenuTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** 通常表示では検索ボタンの右側にその他操作が公開されることを確認する。 */
    @Test
    fun topSearchArea_exposesSearchAndMoreActions() {
        var moreButtonBounds: IntRect? = null
        composeRule.setContent {
            SlevoTheme {
                TabListTopSearchArea(
                    hazeState = HazeState(),
                    isSearchMode = false,
                    searchInputValue = androidx.compose.ui.text.input.TextFieldValue(""),
                    searchFocusRequestId = null,
                    onSearchClick = {},
                    onMoreClick = { moreButtonBounds = it },
                    onSearchInputChange = {},
                    onSearchFocusRequestConsumed = {},
                    onCloseSearch = {},
                )
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("検索").assertExists()
        composeRule.onNodeWithContentDescription("その他").assertExists()
        composeRule.onNodeWithContentDescription("その他").performClick()
        composeRule.waitForIdle()
        assertNotNull(moreButtonBounds)
    }

    /** 検索モードでは通常表示用の検索・その他ボタンを表示しないことを確認する。 */
    @Test
    fun topSearchArea_hidesActionsInSearchMode() {
        composeRule.setContent {
            SlevoTheme {
                TabListTopSearchArea(
                    hazeState = HazeState(),
                    isSearchMode = true,
                    searchInputValue = androidx.compose.ui.text.input.TextFieldValue(""),
                    searchFocusRequestId = null,
                    onSearchClick = {},
                    onMoreClick = {},
                    onSearchInputChange = {},
                    onSearchFocusRequestConsumed = {},
                    onCloseSearch = {},
                )
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("検索").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("その他").assertDoesNotExist()
    }

    /** その他メニューが承認済みの一括クローズ項目だけを表示することを確認する。 */
    @Test
    fun bulkMenu_showsOnlyCloseAllAction() {
        var expanded by mutableStateOf(true)
        composeRule.setContent {
            SlevoTheme {
                AnchoredTabActionMenu(
                    expanded = expanded,
                    anchorBoundsInWindow = IntRect(0, 0, 100, 100),
                    hazeState = null,
                    onDismissRequest = { expanded = false },
                    onCloseAllClick = { expanded = false },
                )
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithText("全てのタブを閉じる").assertExists()
        composeRule.onNodeWithText("詳細").assertDoesNotExist()
        composeRule.onNodeWithText("タブを固定").assertDoesNotExist()
        composeRule.onNodeWithText("タブを閉じる").assertDoesNotExist()
    }

    /** 一括クローズ項目のクリックが一度だけ通知されることを確認する。 */
    @Test
    fun bulkMenu_clickingCloseAllNotifiesOnce() {
        var expanded by mutableStateOf(true)
        var closeAllClickCount = 0
        composeRule.setContent {
            SlevoTheme {
                AnchoredTabActionMenu(
                    expanded = expanded,
                    anchorBoundsInWindow = IntRect(0, 0, 100, 100),
                    hazeState = null,
                    onDismissRequest = { expanded = false },
                    onCloseAllClick = {
                        closeAllClickCount += 1
                        expanded = false
                    },
                )
            }
        }

        composeRule.onNodeWithText("全てのタブを閉じる").performClick()
        composeRule.waitForIdle()

        assertEquals(1, closeAllClickCount)
        composeRule.onNodeWithText("全てのタブを閉じる").assertDoesNotExist()
    }

    /** Back操作で一括クローズを実行せずメニューだけを閉じることを確認する。 */
    @Test
    fun bulkMenu_backDismissesWithoutClosingTabs() {
        var expanded by mutableStateOf(true)
        var closeAllClickCount = 0
        var dismissCount = 0
        composeRule.setContent {
            SlevoTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnchoredTabActionMenu(
                        expanded = expanded,
                        anchorBoundsInWindow = IntRect(0, 0, 100, 100),
                        hazeState = null,
                        onDismissRequest = {
                            dismissCount += 1
                            expanded = false
                        },
                        onCloseAllClick = { closeAllClickCount += 1 },
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()

        assertEquals(0, closeAllClickCount)
        assertEquals(1, dismissCount)
        composeRule.onNodeWithText("全てのタブを閉じる").assertDoesNotExist()
    }
}
