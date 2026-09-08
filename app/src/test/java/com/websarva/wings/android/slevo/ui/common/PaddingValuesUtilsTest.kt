package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Scaffold 間の Insets 合成が、各辺の所有規則とレイアウト方向を守ることを検証する。
 */
class PaddingValuesUtilsTest {
    /** 画面側の上・左右を維持し、下だけ大きい値を選ぶことを確認する。 */
    @Test
    fun mergeScaffoldPaddingValues_prefersScreenEdgesAndLargerBottom() {
        val screen = PaddingValues(start = 4.dp, top = 8.dp, end = 12.dp, bottom = 16.dp)
        val appChrome = PaddingValues(start = 20.dp, top = 24.dp, end = 28.dp, bottom = 32.dp)

        val result = mergeScaffoldPaddingValues(screen, appChrome)

        assertEquals(4.dp, result.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(8.dp, result.calculateTopPadding())
        assertEquals(12.dp, result.calculateEndPadding(LayoutDirection.Ltr))
        assertEquals(32.dp, result.calculateBottomPadding())
    }

    /** RTLでは start/end の関係を入力値のまま保持することを確認する。 */
    @Test
    fun mergeScaffoldPaddingValues_preservesRtlStartAndEnd() {
        val screen = PaddingValues(start = 6.dp, top = 0.dp, end = 10.dp, bottom = 0.dp)

        val result = mergeScaffoldPaddingValues(screen, PaddingValues())

        assertEquals(10.dp, result.calculateStartPadding(LayoutDirection.Rtl))
        assertEquals(6.dp, result.calculateEndPadding(LayoutDirection.Rtl))
    }

    /** 画面側のbottomだけがある場合、その値を保持することを確認する。 */
    @Test
    fun mergeScaffoldPaddingValues_usesScreenBottomWhenChromeIsZero() {
        val result = mergeScaffoldPaddingValues(
            screenPadding = PaddingValues(bottom = 12.dp),
            appChromePadding = PaddingValues(),
        )

        assertEquals(12.dp, result.calculateBottomPadding())
    }

    /** アプリ chrome 側のbottomだけがある場合、その値を採用することを確認する。 */
    @Test
    fun mergeScaffoldPaddingValues_usesChromeBottomWhenScreenIsZero() {
        val result = mergeScaffoldPaddingValues(
            screenPadding = PaddingValues(),
            appChromePadding = PaddingValues(bottom = 20.dp),
        )

        assertEquals(20.dp, result.calculateBottomPadding())
    }

    /** 両方のbottomが0の場合に余白を追加しないことを確認する。 */
    @Test
    fun mergeScaffoldPaddingValues_keepsBottomZeroWhenBothAreZero() {
        val result = mergeScaffoldPaddingValues(PaddingValues(), PaddingValues())

        assertEquals(0.dp, result.calculateBottomPadding())
    }

    /** 既存コンテンツ余白へ追加余白を各辺ごとに加算することを確認する。 */
    @Test
    fun addPaddingValues_sumsEveryEdge() {
        val result = addPaddingValues(
            base = PaddingValues(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp),
            additional = PaddingValues(start = 5.dp, top = 6.dp, end = 7.dp, bottom = 8.dp),
        )

        assertEquals(6.dp, result.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(8.dp, result.calculateTopPadding())
        assertEquals(10.dp, result.calculateEndPadding(LayoutDirection.Ltr))
        assertEquals(12.dp, result.calculateBottomPadding())
    }
}
