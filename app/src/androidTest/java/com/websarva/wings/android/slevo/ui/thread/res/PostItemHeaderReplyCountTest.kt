package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 投稿番号横の返信件数とクリック対象が同じ表示用一覧を使うことを検証する。 */
@RunWith(AndroidJUnit4::class)
class PostItemHeaderReplyCountTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 返信可能なレスが二件なら「(2)」を表示し、その二番号を同じ順で開く。 */
    @Test
    fun replyCountAndPopupTargetsUseTheSameVisiblePostNumbers() {
        // --- Fixture ---
        val visibleReplyNumbers = listOf(2, 4)
        val clickedReplyNumbers = mutableListOf<List<Int>>()

        // --- Header composition ---
        composeRule.setContent {
            SlevoTheme {
                val scope = rememberCoroutineScope()
                PostItemHeader(
                    uiModel = PostHeaderUiModel(
                        header = ThreadPostUiModel.Header("name", "", "", "id"),
                        postNum = 1,
                        idIndex = 1,
                        idTotal = 1,
                        replyFromNumbers = visibleReplyNumbers,
                    ),
                    headerTextStyle = MaterialTheme.typography.bodyMedium,
                    lineHeightEm = 1.2f,
                    pressedHeaderPart = null,
                    scope = scope,
                    onPressedHeaderPartChange = {},
                    onContentPressedChange = {},
                    onRequestMenu = {},
                    onReplyFromClick = { clickedReplyNumbers += it },
                    onIdClick = {},
                    onHeaderClick = null,
                    onShowTextMenu = { _, _ -> },
                )
            }
        }

        // --- Count and target action ---
        composeRule.onNodeWithText("1 (2)", substring = true).performClick()

        assertEquals(listOf(visibleReplyNumbers), clickedReplyNumbers)
    }

    /** 本文プレビュー挿入に伴うヘッダー変更後もIDタップ対象を維持する。 */
    @Test
    fun tappingHeaderIdStillOpensPostsWithTheSameId() {
        // --- Fixture and callback ---
        val clickedIds = mutableListOf<String>()

        // --- Header composition ---
        composeRule.setContent {
            SlevoTheme {
                val scope = rememberCoroutineScope()
                PostItemHeader(
                    uiModel = PostHeaderUiModel(
                        header = ThreadPostUiModel.Header("", "", "", "sample-id"),
                        postNum = 1,
                        idIndex = 1,
                        idTotal = 1,
                        replyFromNumbers = emptyList(),
                    ),
                    headerTextStyle = MaterialTheme.typography.bodyMedium,
                    lineHeightEm = 1.2f,
                    pressedHeaderPart = null,
                    scope = scope,
                    onPressedHeaderPartChange = {},
                    onContentPressedChange = {},
                    onRequestMenu = {},
                    onReplyFromClick = {},
                    onIdClick = { clickedIds += it },
                    onHeaderClick = null,
                    onShowTextMenu = { _, _ -> },
                )
            }
        }

        // --- ID action ---
        val id = composeRule.onNodeWithText("sample-id", useUnmergedTree = true)
        val idBounds = id.fetchSemanticsNode().boundsInRoot
        id.performTouchInput {
            click(Offset(composeRule.density.density * 10f, idBounds.height / 2f))
        }

        assertEquals(listOf("sample-id"), clickedIds)
    }
}
