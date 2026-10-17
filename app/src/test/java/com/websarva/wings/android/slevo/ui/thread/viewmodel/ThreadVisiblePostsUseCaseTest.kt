package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostGroup
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [ThreadVisiblePostsUseCase] の表示行構築を検証するテスト。
 */
class ThreadVisiblePostsUseCaseTest {

    private fun post(content: String, id: String) = ThreadPostUiModel(
        header = ThreadPostUiModel.Header(name = "name", email = "", date = "2024/01/01 00:00:00", id = id),
        body = ThreadPostUiModel.Body(content = content),
    )

    @Test
    fun buildVisibleRows_appliesSearchNgAndReplyCount() {
        val useCase = ThreadVisiblePostsUseCase()
        val posts = listOf(
            post("root", "id1"),
            post(">>1 child target", "id2"),
            post("other", "id3"),
        )

        val result = useCase.buildVisibleRows(
            posts = posts,
            groups = listOf(ThreadPostGroup(startResNo = 1, endResNo = 3, prevResCount = 0)),
            sortType = ThreadSortType.NUMBER,
            treeOrder = emptyList(),
            treeDepthMap = emptyMap(),
            treeRootMap = emptyMap(),
            latestArrivalGroupIndex = null,
            searchQuery = "target",
            ngPostNumbers = emptySet(),
            visibleReplySourceMap = mapOf(1 to listOf(2)),
        )

        assertEquals(listOf(2), result.visiblePostRows.map { it.displayPost.num })
        assertEquals(listOf(0), result.replyCounts)
        assertEquals(-1, result.firstAfterIndex)
    }

    @Test
    fun buildVisibleRows_excludesNgRepliesFromHeaderCount() {
        val result = ThreadVisiblePostsUseCase().buildVisibleRows(
            posts = listOf(
                post("root", "id1"),
                post("reply 2", "id2"),
                post("reply 3", "id3"),
                post("reply 4", "id4"),
            ),
            groups = listOf(ThreadPostGroup(startResNo = 1, endResNo = 4, prevResCount = 0)),
            sortType = ThreadSortType.NUMBER,
            treeOrder = emptyList(),
            treeDepthMap = emptyMap(),
            treeRootMap = emptyMap(),
            latestArrivalGroupIndex = null,
            searchQuery = "",
            ngPostNumbers = setOf(3),
            visibleReplySourceMap = mapOf(1 to listOf(2, 4)),
        )

        assertEquals(2, result.replyCounts.first())
    }

    /** 表示用返信マップの件数をそのまま利用し、0・2・5件を表示値へ反映する。 */
    @Test
    fun buildVisibleRowsUsesVisibleReplyCountForZeroTwoAndFiveReplies() {
        val posts = (1..6).map { number -> post("post$number", "id$number") }
        val group = listOf(ThreadPostGroup(startResNo = 1, endResNo = 6, prevResCount = 0))

        fun replyCount(numbers: List<Int>): Int = ThreadVisiblePostsUseCase().buildVisibleRows(
            posts = posts,
            groups = group,
            sortType = ThreadSortType.NUMBER,
            treeOrder = emptyList(),
            treeDepthMap = emptyMap(),
            treeRootMap = emptyMap(),
            latestArrivalGroupIndex = null,
            searchQuery = "",
            ngPostNumbers = emptySet(),
            visibleReplySourceMap = if (numbers.isEmpty()) emptyMap() else mapOf(1 to numbers),
        ).replyCounts.first()

        assertEquals(0, replyCount(emptyList()))
        assertEquals(2, replyCount(listOf(2, 4)))
        assertEquals(5, replyCount(listOf(2, 3, 4, 5, 6)))
    }

    @Test
    fun buildVisibleRows_keepsUnreadBoundaryAfterNumberSearchAndNgFiltering() {
        val useCase = ThreadVisiblePostsUseCase()
        val posts = listOf(
            post("root", "id1"),
            post("old", "id2"),
            post("new target", "id3"),
            post("new visible", "id4"),
        )

        val result = useCase.buildVisibleRows(
            posts = posts,
            groups = listOf(
                ThreadPostGroup(startResNo = 1, endResNo = 2, prevResCount = 0),
                ThreadPostGroup(startResNo = 3, endResNo = 4, prevResCount = 2),
            ),
            sortType = ThreadSortType.NUMBER,
            treeOrder = emptyList(),
            treeDepthMap = emptyMap(),
            treeRootMap = emptyMap(),
            latestArrivalGroupIndex = 1,
            searchQuery = "new",
            ngPostNumbers = setOf(3),
            visibleReplySourceMap = emptyMap(),
        )

        assertEquals(listOf(4), result.visiblePostRows.map { it.displayPost.num })
        assertEquals(0, result.firstAfterIndex)
    }

    @Test
    fun buildVisibleRows_keepsLatestGroupHeadInTreeAndStableKeysUnique() {
        val useCase = ThreadVisiblePostsUseCase()
        val posts = listOf(
            post("root", "id1"),
            post(">>1 child", "id2"),
            post(">>2 old grandchild", "id3"),
            post(">>1 new child", "id4"),
        )
        val treeOrder = listOf(1, 2, 3, 4)
        val treeDepthMap = mapOf(1 to 0, 2 to 1, 3 to 2, 4 to 1)
        val treeRootMap = mapOf(1 to 1, 2 to 1, 3 to 1, 4 to 1)

        val result = useCase.buildVisibleRows(
            posts = posts,
            groups = listOf(
                ThreadPostGroup(startResNo = 1, endResNo = 3, prevResCount = 0),
                ThreadPostGroup(startResNo = 4, endResNo = 4, prevResCount = 3),
            ),
            sortType = ThreadSortType.TREE,
            treeOrder = treeOrder,
            treeDepthMap = treeDepthMap,
            treeRootMap = treeRootMap,
            latestArrivalGroupIndex = 1,
            searchQuery = "",
            ngPostNumbers = emptySet(),
            visibleReplySourceMap = emptyMap(),
        )

        assertEquals(3, result.firstAfterIndex)
        assertEquals(
            result.visiblePostRows.size,
            result.visiblePostRows.map { it.stableKey }.toSet().size,
        )
    }
}
