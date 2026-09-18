package com.websarva.wings.android.slevo.ui.common.transition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * TabsカードrootとBoard / Threadページrootを接続するpage Shared BoundsをCompose環境で検証する。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class BbsPageSharedBoundsTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 小さいカードrootと大きいページrootが同じBoard keyでmatchする。 */
    @Test
    fun boardCardAndPageRoot_areMatched() {
        assertPageMatch(
            sourceKey = BbsPageSharedBoundsKey.Board("board"),
            targetKey = BbsPageSharedBoundsKey.Board("board"),
        )
    }

    /** Threadでも同じidentityのカードrootとページrootがmatchする。 */
    @Test
    fun threadCardAndPageRoot_areMatchedInBothDirections() {
        val matchReports = mutableListOf<Boolean>()
        lateinit var toggle: () -> Unit
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PageSharedBoundsHarness(
                sourceKey = BbsPageSharedBoundsKey.Thread("thread"),
                targetKey = BbsPageSharedBoundsKey.Thread("thread"),
                onToggleRequested = { toggle = it },
                onMatchReported = { matchReports += it },
            )
        }

        composeRule.runOnIdle { toggle() }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.runOnIdle { toggle() }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.runOnIdle { }

        assertTrue(matchReports.any { it })
    }

    /** 異なるidentity、異なる種別、無効状態ではmatchしない。 */
    @Test
    fun mismatchedOrDisabledPage_doesNotMatch() {
        assertPageDoesNotMatch(
            sourceKey = BbsPageSharedBoundsKey.Board("board-a"),
            targetKey = BbsPageSharedBoundsKey.Board("board-b"),
        )
        assertPageDoesNotMatch(
            sourceKey = BbsPageSharedBoundsKey.Board("same"),
            targetKey = BbsPageSharedBoundsKey.Thread("same"),
        )
        assertPageDoesNotMatch(
            sourceKey = BbsPageSharedBoundsKey.Board("disabled"),
            targetKey = BbsPageSharedBoundsKey.Board("disabled"),
            enabled = false,
        )
    }

    /** Shared Boundsのmatch結果を取得し、Compose時計を遷移完了まで進める。 */
    private fun assertPageMatch(
        sourceKey: BbsPageSharedBoundsKey,
        targetKey: BbsPageSharedBoundsKey,
    ) {
        val reports = mutableListOf<Boolean>()
        lateinit var toggle: () -> Unit
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PageSharedBoundsHarness(
                sourceKey = sourceKey,
                targetKey = targetKey,
                onToggleRequested = { toggle = it },
                onMatchReported = { reports += it },
            )
        }
        composeRule.runOnIdle { toggle() }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.runOnIdle { }
        assertTrue(reports.any { it })
    }

    /** Shared Boundsの不成立を検証する。 */
    private fun assertPageDoesNotMatch(
        sourceKey: BbsPageSharedBoundsKey,
        targetKey: BbsPageSharedBoundsKey,
        enabled: Boolean = true,
    ) {
        val reports = mutableListOf<Boolean>()
        lateinit var toggle: () -> Unit
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PageSharedBoundsHarness(
                sourceKey = sourceKey,
                targetKey = targetKey,
                enabled = enabled,
                onToggleRequested = { toggle = it },
                onMatchReported = { reports += it },
            )
        }
        composeRule.runOnIdle { toggle() }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.runOnIdle { }
        assertFalse(reports.any { it })
    }
}

/**
 * 同一SharedTransitionLayout内で、カードrootとページrootを異なるサイズで共有するテストUI。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PageSharedBoundsHarness(
    sourceKey: BbsPageSharedBoundsKey,
    targetKey: BbsPageSharedBoundsKey,
    enabled: Boolean = true,
    onToggleRequested: (() -> Unit) -> Unit,
    onMatchReported: (Boolean) -> Unit,
) {
    var showTarget by remember { mutableStateOf(false) }
    onToggleRequested { showTarget = !showTarget }
    SharedTransitionLayout {
        val sharedScope = this
        AnimatedContent(
            targetState = showTarget,
            label = "BbsPageSharedBoundsHarness",
        ) { targetVisible ->
            val key = if (targetVisible) targetKey else sourceKey
            val sharedState = with(sharedScope) { rememberSharedContentState(key) }
            SideEffect { onMatchReported(sharedState.isMatchFound) }
            Box(
                modifier = Modifier
                    .size(if (targetVisible) 320.dp else 96.dp)
                    .bbsPageSharedBounds(
                        sharedTransitionScope = sharedScope,
                        animatedVisibilityScope = this,
                        key = key,
                        enabled = enabled,
                    ),
            )
        }
    }
}
