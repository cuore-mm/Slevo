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
            replySourceMap = mapOf(1 to listOf(2)),
        )

        assertEquals(listOf(2), result.visiblePostRows.map { it.displayPost.num })
        assertEquals(listOf(0), result.replyCounts)
        assertEquals(-1, result.firstAfterIndex)
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
            replySourceMap = emptyMap(),
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
            replySourceMap = emptyMap(),
        )

        assertEquals(3, result.firstAfterIndex)
        assertEquals(
            result.visiblePostRows.size,
            result.visiblePostRows.map { it.stableKey }.toSet().size,
        )
    }
}
