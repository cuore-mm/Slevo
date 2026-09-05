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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
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

    /** 現在ページを往復するdrag cancelでは隣接tabを選択通知しないことを確認する。 */
    @Test
    fun dragAwayAndBack_doesNotNotifyIntermediateSelection() {
        var callbackCount = 0
        composeRule.setContent {
            PresentationHarness(
                initialState = TabPresentationState(
                    listOf("zero", "current", "last"),
                    TabSelectionResolution.Selected("current"),
                ),
                onTabSelected = { callbackCount++ },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("pager").performTouchInput {
            val start = center
            down(start)
            moveTo(start + Offset(-size.width * 0.35f, 0f), durationMillis = 500)
            moveTo(start, durationMillis = 500)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("current-current").assertExists()
        assertEquals(0, callbackCount)
    }

    /** 選択中tabの削除中は先頭tabへ暗黙にfallbackせず、例外なく再構成できることを確認する。 */
    @Test
    fun removingSelectedTab_doesNotFallbackToFirstPage() {
        lateinit var update: (TabPresentationState<String, String>) -> Unit
        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    TabPresentationState(
                        listOf("zero", "current", "removed"),
                        TabSelectionResolution.Selected("removed"),
                    )
                )
            }
            update = { state = it }
            PresentationHarness(state, onTabSelected = {})
        }
        composeRule.waitForIdle()

        update(
            TabPresentationState(
                listOf("zero", "current"),
                TabSelectionResolution.PendingMissing("removed"),
            )
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("current-zero").assertDoesNotExist()
    }

    /** tabを並べ替えた後も選択keyに対応するページを維持することを確認する。 */
    @Test
    fun reorderingTabs_preservesSelectedKey() {
        lateinit var update: (TabPresentationState<String, String>) -> Unit
        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    TabPresentationState(
                        listOf("first", "selected", "last"),
                        TabSelectionResolution.Selected("selected"),
                    )
                )
            }
            update = { state = it }
            PresentationHarness(state, onTabSelected = {})
        }
        composeRule.waitForIdle()

        update(
            TabPresentationState(
                listOf("last", "selected", "first"),
                TabSelectionResolution.Selected("selected"),
            )
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("current-selected").assertExists()
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
        val selectedKey = (initialState.selection as? TabSelectionResolution.Selected)?.key
        var lastSynchronizedSelectedKey by remember { mutableStateOf(selectedKey) }
        val pagerState = rememberPagerState(
            initialPage = selectedPage.takeIf { it in tabs.indices } ?: 0,
            pageCount = { tabs.size },
        )
        LaunchedEffect(decision, tabs.size) {
            if (decision is TabDisplayDecision.Selected && pagerState.currentPage != selectedPage) {
                pagerState.scrollToPage(selectedPage)
            }
        }
        LaunchedEffect(pagerState, decision, selectedPage, selectedKey) {
            androidx.compose.runtime.snapshotFlow { pagerState.settledPage }
                .collect {
                    if (decision !is TabDisplayDecision.Selected) return@collect
                    if (selectedKey != lastSynchronizedSelectedKey) {
                        if (pagerState.settledPage == selectedPage) {
                            lastSynchronizedSelectedKey = selectedKey
                        }
                        return@collect
                    }
                    if (pagerState.settledPage != selectedPage) onTabSelected(tabs[pagerState.settledPage])
                }
        }
        HorizontalPager(
            modifier = Modifier.fillMaxSize().testTag("pager"),
            state = pagerState,
            key = { tabs.getOrNull(it) ?: "out-of-range-$it" },
        ) { page ->
            tabs.getOrNull(page)?.let { tab ->
                Box(Modifier.fillMaxSize().testTag("page-$tab"))
            }
        }
        val currentPage = pagerState.currentPage.takeIf { it in tabs.indices }
        if (currentPage != null) {
            Text(
                text = tabs[currentPage],
                modifier = Modifier.testTag("current-${tabs[currentPage]}"),
            )
        }
    }
}
