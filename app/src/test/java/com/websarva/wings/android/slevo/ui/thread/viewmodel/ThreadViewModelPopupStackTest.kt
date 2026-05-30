package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ThreadViewModel のポップアップ重複抑止ロジックを検証するユニットテスト。
 *
 * 連続同一の抑止と A→B→A 許可の要件を、表示内容ベース判定で確認する。
 */
class ThreadViewModelPopupStackTest {

    @Test
    fun appendPopupIfDistinct_suppressesConsecutiveDuplicateIdPopup() {
        val first = popup(
            popupId = 1L,
            postNumbers = listOf(1),
            offset = IntOffset(10, 20),
            size = IntSize(120, 160),
        )
        val duplicate = popup(
            popupId = 2L,
            postNumbers = listOf(1),
            offset = IntOffset(200, 220),
            size = IntSize(300, 360),
        )

        val afterFirst = appendPopupIfDistinct(emptyList(), first)
        val afterDuplicate = appendPopupIfDistinct(afterFirst, duplicate)

        assertEquals(1, afterDuplicate.size)
        assertEquals(1L, afterDuplicate[0].popupId)
    }

    @Test
    fun appendPopupIfDistinct_allowsSamePopupAfterDifferentPopup() {
        val popupA1 = popup(popupId = 1L, postNumbers = listOf(1))
        val popupB = popup(popupId = 2L, postNumbers = listOf(2))
        val popupA2 = popup(popupId = 3L, postNumbers = listOf(1))

        val afterA = appendPopupIfDistinct(emptyList(), popupA1)
        val afterB = appendPopupIfDistinct(afterA, popupB)
        val afterA2 = appendPopupIfDistinct(afterB, popupA2)

        assertEquals(listOf(1L, 2L, 3L), afterA2.map { it.popupId })
    }

    @Test
    fun appendPopupIfDistinct_suppressesConsecutiveDuplicateReplyNumberPopup() {
        val first = popup(popupId = 1L, postNumbers = listOf(7))
        val duplicate = popup(popupId = 2L, postNumbers = listOf(7))

        val result = appendPopupIfDistinct(appendPopupIfDistinct(emptyList(), first), duplicate)

        assertEquals(1, result.size)
        assertEquals(listOf(7), result[0].postNumbers)
    }

    @Test
    fun appendPopupIfDistinct_suppressesConsecutiveDuplicateTreePopup() {
        val first = popup(
            popupId = 10L,
            postNumbers = listOf(3, 4, 5),
            indentLevels = listOf(0, 1, 2),
            offset = IntOffset(0, 0),
            size = IntSize(100, 200),
        )
        val duplicate = popup(
            popupId = 11L,
            postNumbers = listOf(3, 4, 5),
            indentLevels = listOf(0, 1, 2),
            offset = IntOffset(300, 400),
            size = IntSize(500, 600),
        )

        val result = appendPopupIfDistinct(appendPopupIfDistinct(emptyList(), first), duplicate)

        assertEquals(1, result.size)
        assertEquals(10L, result[0].popupId)
    }

    @Test
    fun appendPopupIfDistinct_allowsTreePopupWhenIndentDiffers() {
        val popupA = popup(
            popupId = 1L,
            postNumbers = listOf(3, 4),
            indentLevels = listOf(0, 1),
        )
        val popupB = popup(
            popupId = 2L,
            postNumbers = listOf(3, 4),
            indentLevels = listOf(0, 0),
        )

        val result = appendPopupIfDistinct(appendPopupIfDistinct(emptyList(), popupA), popupB)

        assertEquals(listOf(1L, 2L), result.map { it.popupId })
    }

    private fun popup(
        popupId: Long,
        postNumbers: List<Int>,
        indentLevels: List<Int> = emptyList(),
        offset: IntOffset = IntOffset.Zero,
        size: IntSize = IntSize.Zero,
    ): PopupInfo {
        return PopupInfo(
            popupId = popupId,
            postNumbers = postNumbers,
            offset = offset,
            size = size,
            indentLevels = indentLevels,
        )
    }
}
