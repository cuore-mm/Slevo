package com.websarva.wings.android.slevo.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TabPage] の serialized index 順序と canonical 境界を検証する。
 * このテストは既存 page の順序変更や page count と変換処理の drift を検出する。
 */
class TabPageTest {

    /** 既存 page の serialized index と、entries 由来の page 数を固定する。 */
    @Test
    fun entries_preserveSerializedOrderAndCanonicalCount() {
        assertEquals(0, TabPage.BOARD.index)
        assertEquals(1, TabPage.THREAD.index)
        assertEquals(TabPage.entries.size, TabPage.count)

        val indexes = TabPage.entries.map { it.index }
        assertEquals(TabPage.entries.indices.toList(), indexes)
        assertEquals(indexes.size, indexes.toSet().size)
    }

    /** 有効 index の往復変換と、上下の canonical 境界を検証する。 */
    @Test
    fun fromIndex_mapsValidIndexesAndRejectsBoundaries() {
        TabPage.entries.forEach { page ->
            assertEquals(page, TabPage.fromIndex(page.index))
            assertTrue(TabPage.isValidIndex(page.index))
        }

        assertNull(TabPage.fromIndex(-1))
        assertNull(TabPage.fromIndex(TabPage.count))
        assertTrue(!TabPage.isValidIndex(-1))
        assertTrue(!TabPage.isValidIndex(TabPage.count))
    }
}
