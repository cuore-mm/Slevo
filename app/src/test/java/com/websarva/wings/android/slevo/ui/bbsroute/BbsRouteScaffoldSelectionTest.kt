package com.websarva.wings.android.slevo.ui.bbsroute

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `BbsRouteScaffold` の selected key からページ index を導出する規則を検証するテスト。
 */
class BbsRouteScaffoldSelectionTest {

    /**
     * selected key に一致するタブが存在する場合、その index を返すことを確認する。
     */
    @Test
    fun deriveSelectedPageIndex_returnsMatchedIndex() {
        val tabs = listOf("a", "b", "c")

        val result = deriveSelectedPageIndex(
            tabs = tabs,
            selectedKey = "b",
            getKey = { it },
        )

        assertEquals(1, result)
    }

    /**
     * selected key が存在しない場合は先頭ページを返すことを確認する。
     */
    @Test
    fun deriveSelectedPageIndex_returnsFirstWhenSelectedKeyIsMissing() {
        val tabs = listOf("a", "b", "c")

        val result = deriveSelectedPageIndex(
            tabs = tabs,
            selectedKey = "z",
            getKey = { it },
        )

        assertEquals(0, result)
    }

    /**
     * タブが空の場合は -1 を返すことを確認する。
     */
    @Test
    fun deriveSelectedPageIndex_returnsMinusOneWhenTabsAreEmpty() {
        val result = deriveSelectedPageIndex(
            tabs = emptyList<String>(),
            selectedKey = "a",
            getKey = { it },
        )

        assertEquals(-1, result)
    }
}
