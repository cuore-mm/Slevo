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

    /** thread-only preserve policy returns no programmatic target for a transient missing key. */
    @Test
    fun deriveSelectedPageIndex_preservesCurrentPageWhenThreadSelectionIsMissing() {
        val result = deriveSelectedPageIndex(
            tabs = listOf("a", "b", "c"),
            selectedKey = "missing",
            getKey = { it },
            missingSelectionPolicy = MissingSelectionPolicy.PreserveCurrentPage,
        )

        assertEquals(-1, result)
    }

    /** A null thread selection is also unresolved and must not issue a first-page target. */
    @Test
    fun deriveSelectedPageIndex_preservesCurrentPageWhenThreadSelectionIsNull() {
        val result = deriveSelectedPageIndex(
            tabs = listOf("a", "b"),
            selectedKey = null,
            getKey = { it },
            missingSelectionPolicy = MissingSelectionPolicy.PreserveCurrentPage,
        )

        assertEquals(-1, result)
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
