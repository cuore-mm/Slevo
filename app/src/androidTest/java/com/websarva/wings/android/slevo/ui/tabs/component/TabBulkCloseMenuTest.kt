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
import androidx.compose.ui.test.assertHasNoClickAction
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
import org.junit.Assert.assertTrue
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

    /** Preview表示では項目を描画したままclick、ripple、accessibility操作を無効化することを確認する。 */
    @Test
    fun tabMenu_previewIsVisibleButDisabled() {
        var detailClickCount = 0
        var pinClickCount = 0
        var closeClickCount = 0
        composeRule.setContent {
            SlevoTheme {
                AnchoredTabActionMenu(
                    expanded = true,
                    anchorBoundsInWindow = IntRect(0, 0, 100, 100),
                    hazeState = null,
                    isPinned = false,
                    interactive = false,
                    onDismissRequest = {},
                    onDetailClick = { detailClickCount += 1 },
                    onPinClick = { pinClickCount += 1 },
                    onCloseClick = { closeClickCount += 1 },
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("詳細").assertHasNoClickAction()
        composeRule.onNodeWithText("タブを固定").assertHasNoClickAction()
        composeRule.onNodeWithText("タブを閉じる").assertHasNoClickAction()
        assertEquals(0, detailClickCount + pinClickCount + closeClickCount)
    }

    /** close buttonはContentAreaのclickやlong-press経路から分離されていることを確認する。 */
    @Test
    fun tabCard_closeButtonDoesNotTriggerCardClick() {
        var cardClickCount = 0
        var closeClickCount = 0
        composeRule.setContent {
            SlevoTheme {
                TabListCard(
                    bookmarkColor = null,
                    onClick = { cardClickCount += 1 },
                    headerTitle = "example.com",
                    bodyTitle = "Thread title",
                    onCloseClick = { closeClickCount += 1 },
                )
            }
        }

        composeRule.onNodeWithText("Thread title").performClick()
        composeRule.onNodeWithContentDescription("閉じる").performClick()
        composeRule.waitForIdle()

        assertEquals(1, cardClickCount)
        assertEquals(1, closeClickCount)
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

    /** アンカーを破棄して閉じた後も、再表示時に開始アニメーションを再生することを確認する。 */
    @Test
    fun bulkMenu_replaysEnterAnimationAfterAnchorIsClearedOnDismiss() {
        var expanded by mutableStateOf(true)
        var anchorBounds by mutableStateOf<IntRect?>(IntRect(0, 0, 100, 100))
        composeRule.setContent {
            SlevoTheme {
                AnchoredTabActionMenu(
                    expanded = expanded,
                    anchorBoundsInWindow = anchorBounds,
                    hazeState = null,
                    onDismissRequest = {
                        expanded = false
                        anchorBounds = null
                    },
                    onCloseAllClick = {
                        expanded = false
                        anchorBounds = null
                    },
                )
            }
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.waitForIdle()
        val settledWidth = composeRule.onNodeWithText("全てのタブを閉じる")
            .fetchSemanticsNode()
            .boundsInRoot
            .width

        expanded = false
        anchorBounds = null
        composeRule.waitForIdle()
        composeRule.onNodeWithText("全てのタブを閉じる").assertExists()
        composeRule.mainClock.advanceTimeBy(140)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("全てのタブを閉じる").assertDoesNotExist()

        anchorBounds = IntRect(0, 0, 100, 100)
        expanded = true
        composeRule.waitForIdle()
        val openingWidth = composeRule.onNodeWithText("全てのタブを閉じる")
            .fetchSemanticsNode()
            .boundsInRoot
            .width

        assertTrue(openingWidth < settledWidth)
    }

    /** 固定状態が解除されても、退出中は固定解除アクションを表示し続けることを確認する。 */
    @Test
    fun pinnedTabMenu_keepsUnpinActionDuringExit() {
        var expanded by mutableStateOf(true)
        var isPinned by mutableStateOf(true)
        composeRule.setContent {
            SlevoTheme {
                AnchoredTabActionMenu(
                    expanded = expanded,
                    anchorBoundsInWindow = IntRect(0, 0, 100, 100),
                    hazeState = null,
                    isPinned = isPinned,
                    onDismissRequest = {
                        expanded = false
                        isPinned = false
                    },
                    onDetailClick = {},
                    onPinClick = {},
                    onCloseClick = {},
                )
            }
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("タブの固定を解除").assertExists()

        expanded = false
        isPinned = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("タブの固定を解除").assertExists()
        composeRule.onNodeWithText("タブを固定").assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(140)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("タブの固定を解除").assertDoesNotExist()
    }

    /** 削除対象行が縮小し、残存行が通常レイアウトで詰まることを確認する。 */
    @Test
    fun removableTabList_collapsesRemovalRowWithoutPlacementAnimation() {
        var removingKeys by mutableStateOf(emptySet<String>())
        var tabs by mutableStateOf(listOf("remove", "keep"))
        composeRule.setContent {
            SlevoTheme {
                RemovableTabList(
                    tabItems = tabs,
                    keyOf = { it },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                    removingKeys = removingKeys,
                    onRemoveConfirmed = {},
                    itemContent = { item, _, _, _, _, _, _ ->
                        androidx.compose.material3.Text(item)
                    },
                )
            }
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.waitForIdle()
        val keepBeforeRemoval = composeRule.onNodeWithText("keep").fetchSemanticsNode().boundsInRoot.top
        removingKeys = setOf("remove")
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("remove").assertExists()
        composeRule.onNodeWithText("keep").assertExists()
        val keepDuringRemoval = composeRule.onNodeWithText("keep").fetchSemanticsNode().boundsInRoot.top
        assertTrue(keepDuringRemoval < keepBeforeRemoval)

        tabs = listOf("keep")
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("remove").assertDoesNotExist()
        composeRule.onNodeWithText("keep").assertExists()
    }
}
