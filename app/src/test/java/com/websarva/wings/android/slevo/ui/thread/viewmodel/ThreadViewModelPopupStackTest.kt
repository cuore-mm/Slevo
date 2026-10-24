package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.PopupContentType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ポップアップ重複抑止ロジックを検証するユニットテスト。
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

    /** 同じ番号でも返信先候補一覧と通常ポップアップは別内容として積む。 */
    @Test
    fun appendPopupIfDistinctSeparatesReplyTargetPopupMode() {
        val standard = popup(popupId = 1L, postNumbers = listOf(1))
        val targets = popup(
            popupId = 2L,
            postNumbers = listOf(1),
            contentType = PopupContentType.REPLY_TARGETS,
        )

        val result = appendPopupIfDistinct(appendPopupIfDistinct(emptyList(), standard), targets)

        assertEquals(listOf(1L, 2L), result.map { it.popupId })
    }

    /** 取得済み候補の順序が異なる返信先一覧を別内容として扱う。 */
    @Test
    fun appendPopupIfDistinctSeparatesReplyTargetCandidateOrder() {
        val forward = popup(
            popupId = 1L,
            postNumbers = listOf(1, 2),
            contentType = PopupContentType.REPLY_TARGETS,
        )
        val reversed = popup(
            popupId = 2L,
            postNumbers = listOf(2, 1),
            contentType = PopupContentType.REPLY_TARGETS,
        )

        val result = appendPopupIfDistinct(appendPopupIfDistinct(emptyList(), forward), reversed)

        assertEquals(listOf(1L, 2L), result.map { it.popupId })
    }

    /** 通常一覧はNG投稿で閉じ、取得済みNG返信先は状態行として維持する。 */
    @Test
    fun prunePopupStackRetainsAcquiredNgReplyTargetCandidates() {
        val standard = popup(popupId = 1L, postNumbers = listOf(2))
        val replyTargets = popup(
            popupId = 2L,
            postNumbers = listOf(2),
            contentType = PopupContentType.REPLY_TARGETS,
        )

        val result = prunePopupStackWithoutRenderablePosts(
            popupStack = listOf(standard, replyTargets),
            postCount = 3,
            ngPostNumbers = setOf(2),
        )

        assertEquals(listOf(2L), result.map { it.popupId })
    }

    /** 取得済み候補が一件も残らない返信先一覧を閉じる。 */
    @Test
    fun prunePopupStackDropsReplyTargetPopupWithoutAcquiredCandidates() {
        val missingReplyTargets = popup(
            popupId = 1L,
            postNumbers = listOf(99),
            contentType = PopupContentType.REPLY_TARGETS,
        )

        val result = prunePopupStackWithoutRenderablePosts(
            popupStack = listOf(missingReplyTargets),
            postCount = 3,
            ngPostNumbers = emptySet(),
        )

        assertEquals(emptyList<PopupInfo>(), result)
    }

    private fun popup(
        popupId: Long,
        postNumbers: List<Int>,
        indentLevels: List<Int> = emptyList(),
        contentType: PopupContentType = PopupContentType.STANDARD,
        offset: IntOffset = IntOffset.Zero,
        size: IntSize = IntSize.Zero,
    ): PopupInfo {
        return PopupInfo(
            popupId = popupId,
            postNumbers = postNumbers,
            offset = offset,
            size = size,
            indentLevels = indentLevels,
            contentType = contentType,
        )
    }
}
