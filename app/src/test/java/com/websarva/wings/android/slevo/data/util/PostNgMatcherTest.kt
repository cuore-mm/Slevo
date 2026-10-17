package com.websarva.wings.android.slevo.data.util

import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.model.NgType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [PostNgMatcher] の板適用とレス属性ごとの一致を検証するテスト。 */
class PostNgMatcherTest {
    @Test
    fun matchesIdNameAndBodyOnlyOnApplicableBoard() {
        val post = PostNgTarget(id = "user-7", name = "custom trip", content = "blocked word")
        val idRule = PostNgMatcher.compile(
            listOf(NgEntity(pattern = "user-7", isRegex = false, boardId = null, type = NgType.USER_ID)),
        )
        val nameRule = PostNgMatcher.compile(
            listOf(NgEntity(pattern = "custom", isRegex = false, boardId = 5, type = NgType.USER_NAME)),
        )
        val wordRule = PostNgMatcher.compile(
            listOf(NgEntity(pattern = "blocked\\s+word", isRegex = true, boardId = 5, type = NgType.WORD)),
        )

        assertTrue(PostNgMatcher.isIgnored(post, boardId = 5, rules = idRule))
        assertTrue(PostNgMatcher.isIgnored(post, boardId = 6, rules = idRule))
        assertTrue(PostNgMatcher.isIgnored(post, boardId = 5, rules = nameRule))
        assertFalse(PostNgMatcher.isIgnored(post, boardId = 6, rules = nameRule))
        assertTrue(PostNgMatcher.isIgnored(post, boardId = 5, rules = wordRule))
        assertFalse(PostNgMatcher.isIgnored(post, boardId = 6, rules = wordRule))
        assertEquals(setOf(1), PostNgMatcher.ignoredPostNumbers(listOf(post), 5, idRule))
    }

    @Test
    fun ignoresInvalidRegexAndDoesNotApplyThreadTitleRulesToPosts() {
        val rules = PostNgMatcher.compile(
            listOf(
                NgEntity(pattern = "[", isRegex = true, boardId = null, type = NgType.WORD),
                NgEntity(pattern = ".*", isRegex = true, boardId = null, type = NgType.THREAD_TITLE),
            ),
        )

        assertTrue(rules.isNotEmpty())
        assertFalse(
            PostNgMatcher.isIgnored(
                PostNgTarget(id = "id", name = "name", content = "topic"),
                boardId = 1,
                rules = rules,
            ),
        )
    }
}
