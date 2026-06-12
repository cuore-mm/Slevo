package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.ui.thread.state.DisplayPost
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * スレッド表示変換ヘルパーの振る舞いを検証するテスト。
 */
class ThreadDisplayTransformersTest {

    private fun post(
        content: String,
        id: String,
        name: String = "name",
        email: String = "",
        date: String = "2024/01/01 00:00:00"
    ) = ThreadPostUiModel(
        header = ThreadPostUiModel.Header(
            name = name,
            email = email,
            date = date,
            id = id,
        ),
        body = ThreadPostUiModel.Body(
            content = content,
        ),
    )

    @Test
    fun deriveReplyMaps_collectsCountsAndSources() {
        val posts = listOf(
            post(content = ">>2 >>3", id = "id1"),
            post(content = "no ref", id = "id2"),
            post(content = ">>1", id = "id1")
        )

        val (idCountMap, idIndexList, replySourceMap) = deriveReplyMaps(posts)

        assertEquals(mapOf("id1" to 2, "id2" to 1), idCountMap)
        assertEquals(listOf(1, 1, 2), idIndexList)
        assertEquals(mapOf(1 to listOf(3), 2 to listOf(1), 3 to listOf(1)), replySourceMap)
    }

    @Test
    fun deriveTreeOrder_buildsDepthAndOrder() {
        val posts = listOf(
            post(content = "root", id = "id1"),
            post(content = ">>1 child", id = "id2"),
            post(content = ">>2 grand", id = "id3"),
            post(content = ">>1 sibling", id = "id4")
        )

        val (order, depthMap) = deriveTreeOrder(posts)

        assertEquals(listOf(1, 2, 3, 4), order)
        assertEquals(mapOf(1 to 0, 2 to 1, 3 to 2, 4 to 1), depthMap)
    }

    @Test
    fun buildOrderedPosts_handlesTreeAfterGrouping() {
        val posts = listOf(
            post(content = "root", id = "id1"),
            post(content = ">>1 child", id = "id2"),
            post(content = ">>2 grand", id = "id3"),
            post(content = ">>1 new child", id = "id4")
        )
        val (order, depthMap) = deriveTreeOrder(posts)

        val result = buildOrderedPosts(
            posts = posts,
            order = order,
            sortType = ThreadSortType.TREE,
            treeDepthMap = depthMap,
            treeRootMap = deriveTreeRoots(order, depthMap),
            firstNewResNo = 4,
            prevResCount = 3
        )

        val expected = listOf(
            DisplayPost(1, posts[0], dimmed = false, isAfter = false, depth = 0, rootNumber = 1),
            DisplayPost(2, posts[1], dimmed = false, isAfter = false, depth = 1, rootNumber = 1),
            DisplayPost(3, posts[2], dimmed = false, isAfter = false, depth = 2, rootNumber = 1),
            DisplayPost(1, posts[0], dimmed = true, isAfter = true, depth = 0, rootNumber = 1),
            DisplayPost(4, posts[3], dimmed = false, isAfter = true, depth = 1, rootNumber = 1)
        )
        assertEquals(expected, result)
        assertTrue(result.drop(3).all { it.isAfter })
    }

    @Test
    fun buildGroupDisplayPosts_extractsAfterGroupForTree() {
        val posts = listOf(
            post(content = "root", id = "id1"),
            post(content = ">>1 child", id = "id2"),
            post(content = ">>2 grand", id = "id3"),
            post(content = ">>1 new child", id = "id4")
        )
        val (order, depthMap) = deriveTreeOrder(posts)

        val result = buildGroupDisplayPosts(
            posts = posts,
            order = order,
            sortType = ThreadSortType.TREE,
            treeDepthMap = depthMap,
            treeRootMap = deriveTreeRoots(order, depthMap),
            firstNewResNo = 4,
            prevResCount = 3
        )

        val expected = listOf(
            DisplayPost(1, posts[0], dimmed = true, isAfter = true, depth = 0, rootNumber = 1),
            DisplayPost(4, posts[3], dimmed = false, isAfter = true, depth = 1, rootNumber = 1)
        )
        assertEquals(expected, result)
    }

    @Test
    fun deriveTreeRoots_assignsRootNumberPerTree() {
        val posts = listOf(
            post(content = "root1", id = "id1"),
            post(content = ">>1 child1", id = "id2"),
            post(content = "root2", id = "id3"),
            post(content = ">>3 child2", id = "id4"),
        )
        val (order, depthMap) = deriveTreeOrder(posts)

        val result = deriveTreeRoots(order, depthMap)

        assertEquals(1, result[1])
        assertEquals(1, result[2])
        assertEquals(3, result[3])
        assertEquals(3, result[4])
    }

    @Test
    fun buildGroupDisplayPosts_returnsAllWhenInitialGroup() {
        val posts = listOf(
            post(content = "first", id = "id1"),
            post(content = "second", id = "id2")
        )

        val result = buildGroupDisplayPosts(
            posts = posts,
            order = listOf(1, 2),
            sortType = ThreadSortType.NUMBER,
            treeDepthMap = emptyMap(),
            treeRootMap = emptyMap(),
            firstNewResNo = null,
            prevResCount = 0
        )

        assertEquals(listOf(1, 2), result.map { it.num })
        assertTrue(result.all { !it.isAfter })
    }

    @Test
    fun buildOrderedPosts_handlesNumberSortAndNgFiltering() {
        val posts = listOf(
            post(content = "first", id = "id1"),
            post(content = "second", id = "id2"),
            post(content = "third", id = "id3")
        )

        val ordered = buildOrderedPosts(
            posts = posts,
            order = listOf(1, 2, 3),
            sortType = ThreadSortType.NUMBER,
            treeDepthMap = emptyMap(),
            treeRootMap = emptyMap(),
            firstNewResNo = 3,
            prevResCount = 2
        )

        assertEquals(listOf(1, 2, 3), ordered.map { it.num })
        assertEquals(listOf(false, false, true), ordered.map { it.isAfter })

        val visible = ordered.filterNot { it.num in setOf(2) }
        assertEquals(listOf(1, 3), visible.map { it.num })
    }

    @Test
    fun buildThreadListItemKey_keepsKeysUniqueWithDuplicatePosts() {
        val duplicated = listOf(
            DisplayPost(num = 388, post = post(content = "root", id = "id1"), dimmed = true, isAfter = false, depth = 0, rootNumber = 388),
            DisplayPost(num = 388, post = post(content = "root", id = "id1"), dimmed = true, isAfter = true, depth = 0, rootNumber = 388),
            DisplayPost(num = 388, post = post(content = "root", id = "id1"), dimmed = true, isAfter = true, depth = 1, rootNumber = 388)
        )

        val keys = duplicated.map(::buildThreadListItemKey)

        assertEquals(3, keys.toSet().size)
    }

    @Test
    fun buildThreadListItemKey_isStableAcrossDisplayIndexChanges() {
        val row = DisplayPost(
            num = 25,
            post = post(content = "sample", id = "id2"),
            dimmed = false,
            isAfter = true,
            depth = 2,
            rootNumber = 1,
        )

        val keyAtFirstRender = buildThreadListItemKey(row)
        val keyAtSecondRender = buildThreadListItemKey(row)

        assertEquals(keyAtFirstRender, keyAtSecondRender)
    }

    @Test
    fun buildThreadListItemKey_differsWhenDisplayRoleDiffers() {
        val normal = DisplayPost(
            num = 100,
            post = post(content = "row", id = "id3"),
            dimmed = false,
            isAfter = false,
            depth = 0,
            rootNumber = 100,
        )
        val dimmedContext = normal.copy(dimmed = true, isAfter = true)

        val normalKey = buildThreadListItemKey(normal)
        val dimmedContextKey = buildThreadListItemKey(dimmedContext)

        assertTrue(normalKey != dimmedContextKey)
    }

    @Test
    fun parseDateToUnix_parsesWithFallback() {
        val timestamp = parseDateToUnix("2024/01/02 03:04:05.123")
        val expected = DATE_FORMAT.parse("2024/01/02 03:04:05")!!.time
        assertEquals(expected, timestamp)
    }
}
