package com.websarva.wings.android.slevo.ui.bbsroute

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * atomic presentation state から Pager 表示判断を導出する規則を検証する。
 * 画面種別ではなく resolution の種類だけを入力として扱う。
 */
class BbsRouteScaffoldSelectionTest {

    /** 初回 canonical snapshot 前は loading decision になることを確認する。 */
    @Test
    fun loading_returnsLoadingDecision() {
        val result = deriveTabDisplayDecision(
            TabPresentationState(listOf("a"), TabSelectionResolution.Loading),
            getKey = { it },
        )

        assertEquals(TabDisplayDecision.Loading, result)
    }

    /** 有効な key は一覧内の各 index をそのまま返すことを確認する。 */
    @Test
    fun selected_returnsEveryMatchingIndex() {
        listOf("a", "b", "c").forEachIndexed { index, key ->
            val result = deriveTabDisplayDecision(
                TabPresentationState(listOf("a", "b", "c"), TabSelectionResolution.Selected(key)),
                getKey = { it },
            )

            assertEquals(TabDisplayDecision.Selected(index), result)
        }
    }

    /** pending missing は programmatic scroll を発行せず現在 page を保持することを確認する。 */
    @Test
    fun pendingMissing_preservesCurrentPage() {
        val result = deriveTabDisplayDecision(
            TabPresentationState(listOf("a", "b"), TabSelectionResolution.PendingMissing("target")),
            getKey = { it },
        )

        assertEquals(TabDisplayDecision.PreserveCurrent, result)
    }

    /** loaded empty は loading と区別された empty decision になることを確認する。 */
    @Test
    fun empty_returnsEmptyDecision() {
        val result = deriveTabDisplayDecision(
            TabPresentationState(emptyList<String>(), TabSelectionResolution.Empty),
            getKey = { it },
        )

        assertEquals(TabDisplayDecision.Empty, result)
    }

    /** Selected key の invariant 違反は page 0 fallback ではなく失敗することを確認する。 */
    @Test(expected = IllegalStateException::class)
    fun selectedMissingFromTabs_failsInvariant() {
        deriveTabDisplayDecision(
            TabPresentationState(listOf("a"), TabSelectionResolution.Selected("missing")),
            getKey = { it },
        )
    }
}
