package com.websarva.wings.android.slevo.ui.bbsroute

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.waitForIdle
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * 共有 selection decision と Pager 同期の受入シナリオを検証する。
 * 実際の route content と同じ decision helper を使用し、state update の境界を Compose rule で制御する。
 */
class BbsRouteScaffoldTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** 非 0 index の Selected key が対応する page を表示し、初期 callback を発火しない。 */
    @Test
    fun selectedKey_displaysMatchingPageWithoutSelectionCallback() {
        var callbackCount = 0
        composeRule.setContent {
            PresentationHarness(
                initialState = TabPresentationState(
                    listOf("zero", "selected"),
                    TabSelectionResolution.Selected("selected"),
                ),
                onTabSelected = { callbackCount++ },
            )
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag("current-selected").assertExists()
        assertEquals(0, callbackCount)
    }

    /** 非 0 index の表示中に pending missing へ遷移しても現在 page と callback を維持する。 */
    @Test
    fun pendingMissing_preservesCurrentPageAndSuppressesCallback() {
        lateinit var update: (TabPresentationState<String, String>) -> Unit
        var callbackCount = 0
        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    TabPresentationState(
                        listOf("zero", "current"),
                        TabSelectionResolution.Selected("current"),
                    )
                )
            }
            update = { state = it }
            PresentationHarness(state, onTabSelected = { callbackCount++ })
        }
        composeRule.waitForIdle()
        update(TabPresentationState(listOf("zero", "current"), TabSelectionResolution.PendingMissing("target")))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("current-current").assertExists()
        composeRule.onNodeWithTag("current-zero").assertDoesNotExist()
        assertEquals(0, callbackCount)
    }

    /** pending target の Selected 確認後は target page へ移動し、Empty では content を構成しない。 */
    @Test
    fun confirmationMovesToTargetAndEmptyRemovesContent() {
        lateinit var update: (TabPresentationState<String, String>) -> Unit
        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    TabPresentationState(listOf("zero", "current"), TabSelectionResolution.PendingMissing("target"))
                )
            }
            update = { state = it }
            PresentationHarness(state, onTabSelected = {})
        }
        composeRule.waitForIdle()
        update(
            TabPresentationState(
                listOf("zero", "current", "target"),
                TabSelectionResolution.Selected("target"),
            )
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("current-target").assertExists()

        update(TabPresentationState(emptyList(), TabSelectionResolution.Empty))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("target").assertDoesNotExist()
    }

    /** selection state を最小の Pager content へ投影するテスト用 composable。 */
    @Composable
    private fun PresentationHarness(
        initialState: TabPresentationState<String, String>,
        onTabSelected: (String) -> Unit,
    ) {
        val decision = deriveTabDisplayDecision(initialState) { it }
        val tabs = initialState.tabs
        if (tabs.isEmpty()) return
        val selectedPage = (decision as? TabDisplayDecision.Selected)?.index ?: -1
        val pagerState = rememberPagerState(
            initialPage = selectedPage.takeIf { it in tabs.indices } ?: 0,
            pageCount = { tabs.size },
        )
        LaunchedEffect(decision, tabs.size) {
            if (decision is TabDisplayDecision.Selected && pagerState.currentPage != selectedPage) {
                pagerState.scrollToPage(selectedPage)
            }
        }
        LaunchedEffect(pagerState.currentPage, decision) {
            if (decision is TabDisplayDecision.Selected && pagerState.currentPage != selectedPage) {
                onTabSelected(tabs[pagerState.currentPage])
            }
        }
        HorizontalPager(state = pagerState, key = { tabs[it] }) { page ->
            Box(Modifier.fillMaxSize().testTag("page-${tabs[page]}"))
        }
        Text(
            text = tabs[pagerState.currentPage],
            modifier = Modifier.testTag("current-${tabs[pagerState.currentPage]}"),
        )
    }
}
