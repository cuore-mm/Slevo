package com.websarva.wings.android.slevo.ui.tabs.screen

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * タブ一覧の初期スクロール対象index導出を検証する。
 *
 * 表示順とstable keyだけを入力として扱い、Composeのレイアウト状態には依存しない。
 */
class TabInitialScrollTest {

    /** 選択中の板タブが一覧途中にあれば、そのindexを返すことを確認する。 */
    @Test
    fun selectedBoardTab_returnsMatchingIndex() {
        val result = resolveInitialTabListIndex(
            items = listOf("board-a", "board-b", "board-c"),
            selectedKey = "board-b",
            keyOf = { it },
        )

        assertEquals(1, result)
    }

    /** 選択中のスレッドが一覧途中にあれば、そのindexを返すことを確認する。 */
    @Test
    fun selectedThreadTab_returnsMatchingIndex() {
        val result = resolveInitialTabListIndex(
            items = listOf("thread-1", "thread-2", "thread-3"),
            selectedKey = "thread-2",
            keyOf = { it },
        )

        assertEquals(1, result)
    }

    /** 先頭・末尾の選択タブはそれぞれ境界indexへ解決されることを確認する。 */
    @Test
    fun selectedTabAtListBoundary_returnsBoundaryIndex() {
        val items = listOf("first", "middle", "last")

        assertEquals(
            0,
            resolveInitialTabListIndex(items, selectedKey = "first", keyOf = { it }),
        )
        assertEquals(
            2,
            resolveInitialTabListIndex(items, selectedKey = "last", keyOf = { it }),
        )
    }

    /** selected keyがnullまたは一覧に存在しない場合は末尾へフォールバックすることを確認する。 */
    @Test
    fun missingSelectedKey_returnsLastIndex() {
        assertEquals(
            2,
            resolveInitialTabListIndex(
                items = listOf("a", "b", "c"),
                selectedKey = null,
                keyOf = { it },
            ),
        )
        assertEquals(
            2,
            resolveInitialTabListIndex(
                items = listOf("a", "b", "c"),
                selectedKey = "missing",
                keyOf = { it },
            ),
        )
    }

    /** 空一覧ではスクロール対象を返さず、存在しない項目を待たないことを確認する。 */
    @Test
    fun emptyList_returnsNoTarget() {
        assertEquals(
            null,
            resolveInitialTabListIndex(
                items = emptyList<String>(),
                selectedKey = "missing",
                keyOf = { it },
            ),
        )
    }
}
