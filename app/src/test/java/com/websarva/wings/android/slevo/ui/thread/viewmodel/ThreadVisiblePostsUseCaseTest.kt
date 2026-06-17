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
}
