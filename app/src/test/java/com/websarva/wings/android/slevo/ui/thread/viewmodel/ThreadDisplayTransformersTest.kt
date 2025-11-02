package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.ui.thread.state.DisplayPost
import com.websarva.wings.android.slevo.ui.thread.state.ReplyInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadDisplayTransformersTest {

    private fun reply(
        content: String,
        id: String,
        name: String = "name",
        email: String = "",
        date: String = "2024/01/01 00:00:00"
    ) = ReplyInfo(
        name = name,
        email = email,
        date = date,
        id = id,
        content = content
    )

    @Test
    fun deriveReplyMaps_collectsCountsAndSources() {
        val posts = listOf(
            reply(content = ">>2 >>3", id = "id1"),
            reply(content = "no ref", id = "id2"),
            reply(content = ">>1", id = "id1")
        )

        val (idCountMap, idIndexList, replySourceMap) = deriveReplyMaps(posts)

        assertEquals(mapOf("id1" to 2, "id2" to 1), idCountMap)
        assertEquals(listOf(1, 1, 2), idIndexList)
        assertEquals(mapOf(1 to listOf(3), 2 to listOf(1), 3 to listOf(1)), replySourceMap)
    }

    @Test
    fun deriveTreeOrder_buildsDepthAndOrder() {
        val posts = listOf(
            reply(content = "root", id = "id1"),
            reply(content = ">>1 child", id = "id2"),
            reply(content = ">>2 grand", id = "id3"),
            reply(content = ">>1 sibling", id = "id4")
        )

        val (order, depthMap) = deriveTreeOrder(posts)

        assertEquals(listOf(1, 2, 3, 4), order)
        assertEquals(mapOf(1 to 0, 2 to 1, 3 to 2, 4 to 1), depthMap)
    }

    @Test
    fun buildOrderedPosts_appendsTreeSortedNewPosts() {
        val posts = listOf(
            reply(content = "root", id = "id1"),
            reply(content = ">>1 child", id = "id2"),
            reply(content = ">>2 grand", id = "id3"),
            reply(content = ">>1 new child", id = "id4")
        )
        val (order, depthMap) = deriveTreeOrder(posts)

        val result = buildOrderedPosts(
            posts = posts,
            order = order,
            sortType = ThreadSortType.TREE,
            treeDepthMap = depthMap,
            prevResCount = 3
        )

        val expected = listOf(
            DisplayPost(1, posts[0], dimmed = false, isAfter = false, depth = 0),
            DisplayPost(2, posts[1], dimmed = false, isAfter = false, depth = 1),
            DisplayPost(3, posts[2], dimmed = false, isAfter = false, depth = 2),
            DisplayPost(4, posts[3], dimmed = false, isAfter = true, depth = 1)
        )
        assertEquals(expected, result)
        assertEquals(listOf(false, false, false, true), result.map { it.isAfter })
    }

    @Test
    fun buildOrderedPosts_handlesNumberSortAndNgFiltering() {
        val posts = listOf(
            reply(content = "first", id = "id1"),
            reply(content = "second", id = "id2"),
            reply(content = "third", id = "id3")
        )

        val ordered = buildOrderedPosts(
            posts = posts,
            order = listOf(1, 2, 3),
            sortType = ThreadSortType.NUMBER,
            treeDepthMap = emptyMap(),
            prevResCount = 2
        )

        assertEquals(listOf(1, 2, 3), ordered.map { it.num })
        assertEquals(listOf(false, false, true), ordered.map { it.isAfter })

        val visible = ordered.filterNot { it.num in setOf(2) }
        assertEquals(listOf(1, 3), visible.map { it.num })
    }

    @Test
    fun parseDateToUnix_parsesWithFallback() {
        val timestamp = parseDateToUnix("2024/01/02 03:04:05.123")
        val expected = DATE_FORMAT.parse("2024/01/02 03:04:05")!!.time
        assertEquals(expected, timestamp)
    }
}
