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
 * Board/ThreadコントローラーShared Boundsのkey照合をCompose環境で検証する。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class BbsControllerSharedBoundsTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 同じBoard identityを持つAnimatedContent間でShared Boundsのmatchが成立する。 */
    @Test
    fun sameBoardKey_isMatchedDuringAnimatedContentTransition() {
        val matchReports = mutableListOf<Boolean>()
        lateinit var showLargeCard: () -> Unit
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SharedBoundsHarness(
                sourceKey = BbsControllerSharedBoundsKey.Board("board"),
                targetKey = BbsControllerSharedBoundsKey.Board("board"),
                onTargetRequested = { showLargeCard = it },
                onMatchReported = { matchReports += it },
            )
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { showLargeCard() }
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.runOnIdle { }

        assertTrue(matchReports.any { it })
    }

    /** 同じThread identityを持つAnimatedContent間でもShared Boundsのmatchが成立する。 */
    @Test
    fun sameThreadKey_isMatchedDuringAnimatedContentTransition() {
        val matchReports = mutableListOf<Boolean>()
        lateinit var showLargeCard: () -> Unit
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SharedBoundsHarness(
                sourceKey = BbsControllerSharedBoundsKey.Thread("example.com/board/1"),
                targetKey = BbsControllerSharedBoundsKey.Thread("example.com/board/1"),
                onTargetRequested = { showLargeCard = it },
                onMatchReported = { matchReports += it },
            )
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { showLargeCard() }
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.runOnIdle { }

        assertTrue(matchReports.any { it })
    }

    /** Board/Threadで同じ固定ActionsRow keyを使う行全体がShared Boundsへ接続される。 */
    @Test
    fun actionsRowKey_isMatchedDuringAnimatedContentTransition() {
        val matchReports = mutableListOf<Boolean>()
        lateinit var showLargeRow: () -> Unit
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SharedBoundsHarness(
                sourceKey = BbsControllerSharedBoundsKey.ActionsRow,
                targetKey = BbsControllerSharedBoundsKey.ActionsRow,
                useActionsRowModifier = true,
                onTargetRequested = { showLargeRow = it },
                onMatchReported = { matchReports += it },
            )
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle { showLargeRow() }
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.runOnIdle { }

        assertTrue(matchReports.any { it })
    }

    /** 異なる種別・identity・無効状態ではShared Boundsのmatchが成立しない。 */
    @Test
    fun mismatchedOrDisabledKey_doesNotMatch() {
        val mismatchedReports = mutableListOf<Boolean>()
        val differentIdentityReports = mutableListOf<Boolean>()
        val disabledReports = mutableListOf<Boolean>()
        lateinit var showMismatchedCard: () -> Unit
        lateinit var showDifferentIdentityCard: () -> Unit
        lateinit var showDisabledCard: () -> Unit
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SharedBoundsHarness(
                sourceKey = BbsControllerSharedBoundsKey.Board("board"),
                targetKey = BbsControllerSharedBoundsKey.Thread("board"),
                onTargetRequested = { showMismatchedCard = it },
                onMatchReported = { mismatchedReports += it },
            )
            SharedBoundsHarness(
                sourceKey = BbsControllerSharedBoundsKey.Thread("thread-a"),
                targetKey = BbsControllerSharedBoundsKey.Thread("thread-b"),
                onTargetRequested = { showDifferentIdentityCard = it },
                onMatchReported = { differentIdentityReports += it },
            )
            SharedBoundsHarness(
                sourceKey = BbsControllerSharedBoundsKey.Board("disabled"),
                targetKey = BbsControllerSharedBoundsKey.Board("disabled"),
                enabled = false,
                onTargetRequested = { showDisabledCard = it },
                onMatchReported = { disabledReports += it },
            )
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.runOnIdle {
            showMismatchedCard()
            showDifferentIdentityCard()
            showDisabledCard()
        }
        composeRule.mainClock.advanceTimeBy(150)
        composeRule.runOnIdle { }

        assertFalse(mismatchedReports.any { it })
        assertFalse(differentIdentityReports.any { it })
        assertFalse(disabledReports.any { it })
    }
}

/**
 * 同一SharedTransitionLayout内で、異なる内容のroot BoxをShared Boundsへ接続するテスト用UI。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedBoundsHarness(
    sourceKey: BbsControllerSharedBoundsKey,
    targetKey: BbsControllerSharedBoundsKey,
    enabled: Boolean = true,
    useActionsRowModifier: Boolean = false,
    onTargetRequested: (() -> Unit) -> Unit,
    onMatchReported: (Boolean) -> Unit,
) {
    var showTarget by remember { mutableStateOf(false) }
    onTargetRequested { showTarget = true }
    SharedTransitionLayout {
        val sharedScope = this
        AnimatedContent(
            targetState = showTarget,
            label = "BbsControllerSharedBoundsHarness",
        ) { targetVisible ->
            val key = if (targetVisible) targetKey else sourceKey
            val sharedState = with(sharedScope) { rememberSharedContentState(key) }
            SideEffect { onMatchReported(sharedState.isMatchFound) }
            val sharedModifier = if (useActionsRowModifier) {
                Modifier.bbsControllerActionsSharedBounds(
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = this,
                )
            } else {
                Modifier.bbsControllerSharedBounds(
                    sharedTransitionScope = sharedScope,
                    animatedVisibilityScope = this,
                    key = key,
                    enabled = enabled,
                )
            }
            Box(
                modifier = Modifier
                    .size(if (targetVisible) 160.dp else 64.dp)
                    .then(sharedModifier),
            )
        }
    }
}
