package com.websarva.wings.android.slevo.data.util

import com.websarva.wings.android.slevo.data.datasource.local.entity.NgEntity
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.ui.thread.viewmodel.toThreadPostUiModel
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

    /** DATレスと画面モデルが同じID・名前・本文をmatcherへ渡すことを確認する。 */
    @Test
    fun replyInfoAndThreadPostUiModelProduceTheSameNgMatch() {
        val replyInfo = ReplyInfo(
            name = " 名無しの名前 &amp; trip",
            email = "sage",
            date = "2026/01/01 00:00:00",
            id = " user-7 ",
            content = "本文 &gt;&gt;2 blocked",
        )
        val uiPost = replyInfo.toThreadPostUiModel()
        val replyTarget = PostNgTarget(replyInfo.id, replyInfo.name, replyInfo.content)
        val uiTarget = PostNgTarget(uiPost.header.id, uiPost.header.name, uiPost.body.content)
        val rules = PostNgMatcher.compile(
            listOf(
                NgEntity(pattern = "user-7", isRegex = false, boardId = null, type = NgType.USER_ID),
                NgEntity(pattern = "名前 &amp;", isRegex = false, boardId = 5, type = NgType.USER_NAME),
                NgEntity(pattern = "blocked$", isRegex = true, boardId = 5, type = NgType.WORD),
            ),
        )

        assertEquals(replyTarget, uiTarget)
        assertEquals(
            PostNgMatcher.isIgnored(replyTarget, boardId = 5, rules = rules),
            PostNgMatcher.isIgnored(uiTarget, boardId = 5, rules = rules),
        )
        assertEquals(
            PostNgMatcher.isIgnored(replyTarget, boardId = 6, rules = rules),
            PostNgMatcher.isIgnored(uiTarget, boardId = 6, rules = rules),
        )
    }
}
