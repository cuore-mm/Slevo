package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
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
        val post = post(number = 1, id = "id-a")
        val first = popup(
            popupId = 1L,
            posts = listOf(post),
            offset = IntOffset(10, 20),
            size = IntSize(120, 160),
        )
        val duplicate = popup(
            popupId = 2L,
            posts = listOf(post),
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
        val aPost = post(number = 1, id = "id-a")
        val bPost = post(number = 2, id = "id-b")
        val popupA1 = popup(popupId = 1L, posts = listOf(aPost))
        val popupB = popup(popupId = 2L, posts = listOf(bPost))
        val popupA2 = popup(popupId = 3L, posts = listOf(aPost))

        val afterA = appendPopupIfDistinct(emptyList(), popupA1)
        val afterB = appendPopupIfDistinct(afterA, popupB)
        val afterA2 = appendPopupIfDistinct(afterB, popupA2)

        assertEquals(listOf(1L, 2L, 3L), afterA2.map { it.popupId })
    }

    @Test
    fun appendPopupIfDistinct_suppressesConsecutiveDuplicateReplyNumberPopup() {
        val replyPost = post(number = 7, id = "id-reply")
        val first = popup(popupId = 1L, posts = listOf(replyPost))
        val duplicate = popup(popupId = 2L, posts = listOf(replyPost))

        val result = appendPopupIfDistinct(appendPopupIfDistinct(emptyList(), first), duplicate)

        assertEquals(1, result.size)
        assertEquals(listOf("id-reply"), result[0].posts.map { it.header.id })
    }

    @Test
    fun appendPopupIfDistinct_suppressesConsecutiveDuplicateTreePopup() {
        val treePosts = listOf(
            post(number = 3, id = "id-root"),
            post(number = 4, id = "id-child"),
            post(number = 5, id = "id-leaf"),
        )
        val first = popup(
            popupId = 10L,
            posts = treePosts,
            indentLevels = listOf(0, 1, 2),
            offset = IntOffset(0, 0),
            size = IntSize(100, 200),
        )
        val duplicate = popup(
            popupId = 11L,
            posts = treePosts,
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
        val treePosts = listOf(
            post(number = 3, id = "id-root"),
            post(number = 4, id = "id-child"),
        )
        val popupA = popup(
            popupId = 1L,
            posts = treePosts,
            indentLevels = listOf(0, 1),
        )
        val popupB = popup(
            popupId = 2L,
            posts = treePosts,
            indentLevels = listOf(0, 0),
        )

        val result = appendPopupIfDistinct(appendPopupIfDistinct(emptyList(), popupA), popupB)

        assertEquals(listOf(1L, 2L), result.map { it.popupId })
    }

    private fun popup(
        popupId: Long,
        posts: List<ThreadPostUiModel>,
        indentLevels: List<Int> = emptyList(),
        offset: IntOffset = IntOffset.Zero,
        size: IntSize = IntSize.Zero,
    ): PopupInfo {
        return PopupInfo(
            popupId = popupId,
            posts = posts,
            offset = offset,
            size = size,
            indentLevels = indentLevels,
        )
    }

    private fun post(number: Int, id: String): ThreadPostUiModel {
        return ThreadPostUiModel(
            header = ThreadPostUiModel.Header(
                name = "name-$number",
                email = "",
                date = number.toString(),
                id = id,
            ),
            body = ThreadPostUiModel.Body(
                content = "content-$number",
            ),
        )
    }
}
