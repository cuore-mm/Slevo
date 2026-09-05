package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.fetchSemanticsNode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.ui.board.components.BoardTabTitleCard
import com.websarva.wings.android.slevo.ui.board.components.BoardToolBar
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

/** 固定画面種別ボタンの表示ラベルとTalkBack向け意味論を検証する。 */
class TabToolBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 遷移先が未解決の場合、ラベルを保ったままボタンを無効化する。 */
    @Test
    fun destinationButton_exposesDescriptionAndDisabledState() {
        composeRule.setContent {
            MaterialTheme {
                TabDestinationButton(
                    labelRes = R.string.open_thread_screen,
                    contentDescriptionRes = R.string.open_thread_screen_description,
                    enabled = false,
                    onClick = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("スレッドタブに移動")
            .assertTextEquals("スレ")
            .assertIsNotEnabled()
    }

    /** 展開時は固定高108dpへタイトル行と下段アクション行を収めることを確認する。 */
    @Test
    fun expandedToolbar_displaysTitleAndActionRowsWithinHeight() {
        composeRule.setContent {
            MaterialTheme {
                TabToolBar(
                    modifier = Modifier.testTag("tab-toolbar"),
                    actions = emptyList(),
                    onTabListClick = {},
                    onPostClick = {},
                    tabIconContentDescriptionRes = R.string.open_tablist,
                    postIconContentDescriptionRes = R.string.post,
                    actionsProgress = 1f,
                    titleContent = { modifier -> Box(modifier.fillMaxSize()) },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tab-toolbar").assertHeightIsEqualTo(108.dp)
    }

    /** 縮退時は下段アクションを構成せず、タイトル行を56dp内へ収めることを確認する。 */
    @Test
    fun collapsedToolbar_fitsWithinHeight() {
        composeRule.setContent {
            MaterialTheme {
                TabToolBar(
                    modifier = Modifier.testTag("tab-toolbar"),
                    actions = emptyList(),
                    onTabListClick = {},
                    onPostClick = {},
                    tabIconContentDescriptionRes = R.string.open_tablist,
                    postIconContentDescriptionRes = R.string.post,
                    actionsProgress = 0f,
                    titleContent = { modifier -> Box(modifier.fillMaxSize()) },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tab-toolbar").assertHeightIsEqualTo(56.dp)
    }

    /** Boardのタイトルカードと画面種別ボタンが同じタイトル行高になることを確認する。 */
    @Test
    fun expandedBoardToolbar_matchesTitleCardAndDestinationButtonHeight() {
        val tab = BoardTabInfo(
            boardId = 1L,
            boardName = "板のタイトル",
            boardUrl = "https://example.com/board/",
            serviceName = "example",
        )
        val uiState = BoardUiState(
            boardInfo = BoardInfo(
                boardId = tab.boardId,
                name = tab.boardName,
                url = tab.boardUrl,
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                BoardToolBar(
                    onSortClick = {},
                    onPostClick = {},
                    onTabListClick = {},
                    onSearchClick = {},
                    canOpenThread = true,
                    onOpenThreadClick = {},
                    titleContent = { modifier ->
                        BoardTabTitleCard(
                            modifier = modifier.testTag("title-card"),
                            tab = tab,
                            uiState = uiState,
                            actionProgress = 1f,
                            onTitleClick = {},
                            onBookmarkClick = {},
                            onRefreshClick = {},
                        )
                    },
                )
            }
        }
        composeRule.waitForIdle()

        val cardHeight = composeRule
            .onNodeWithTag("title-card")
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        val buttonHeight = composeRule
            .onNodeWithContentDescription("スレッドタブに移動")
            .fetchSemanticsNode()
            .boundsInRoot
            .height

        assertEquals(cardHeight, buttonHeight, 0.5f)
    }
}
