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

    /** タブ順が変わっても stable key から再解決した index を使用することを確認する。 */
    @Test
    fun reorderedTabs_resolvesSelectedKeyToNewIndex() {
        val result = deriveTabDisplayDecision(
            TabPresentationState(
                listOf("last", "selected", "first"),
                TabSelectionResolution.Selected("first"),
            ),
            getKey = { it },
        )

        assertEquals(TabDisplayDecision.Selected(2), result)
    }

    /** 1タブ時はそのページだけを描画範囲として返すことを確認する。 */
    @Test
    fun pagerTitlePageRange_withSingleTab_returnsSinglePage() {
        assertEquals(0..0, pagerTitlePageRange(currentPage = 0, pageCount = 1))
    }

    /** 最初と最後のページでは範囲外の隣接ページを返さないことを確認する。 */
    @Test
    fun pagerTitlePageRange_clampsToAvailablePages() {
        assertEquals(0..1, pagerTitlePageRange(currentPage = 0, pageCount = 2))
        assertEquals(1..2, pagerTitlePageRange(currentPage = 2, pageCount = 3))
    }

    /** 削除中などの範囲外 page では page 0 fallback を返さないことを確認する。 */
    @Test
    fun pagerTitlePageRange_withOutOfBoundsPage_returnsEmptyRange() {
        assertEquals(0 until 0, pagerTitlePageRange(currentPage = 3, pageCount = 2))
        assertEquals(0 until 0, pagerTitlePageRange(currentPage = -1, pageCount = 2))
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
