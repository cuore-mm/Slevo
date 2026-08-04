package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PendingOwnPostEntity
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [OwnPostMatcher] のprovider非依存な本文・identity照合を検証する。 */
class OwnPostMatcherTest {
    private val matcher = OwnPostMatcher()

    @Test
    fun matches_normalizesLineBreaksAndTrailingWhitespace() {
        val pending = pending(content = "one\r\ntwo  \n")
        val post = post(content = "one\ntwo")

        assertTrue(matcher.matches(pending, post))
    }

    @Test
    fun matches_treatsEmptyIdentityAsWildcard() {
        assertTrue(matcher.matches(pending(name = "", email = ""), post(name = "default", email = "sage")))
    }

    @Test
    fun matches_requiresNonEmptyIdentityAndExactContent() {
        assertFalse(matcher.matches(pending(name = "Alice"), post(name = "Bob")))
        assertFalse(matcher.matches(pending(content = "same"), post(content = " SAME ")))
        assertFalse(matcher.matches(pending(content = "a b"), post(content = "ab")))
    }

    private fun pending(
        content: String = "message",
        name: String = "name",
        email: String = "mail",
    ) = PendingOwnPostEntity(
        providerId = "provider",
        boardKey = "board",
        threadKey = "thread",
        content = content,
        name = name,
        email = email,
        baseResCount = 0,
        lastCheckedResNum = 0,
        submittedAt = 1L,
        expiresAt = 2L,
    )

    private fun post(
        content: String = "message",
        name: String = "name",
        email: String = "mail",
    ) = ThreadPostUiModel(
        header = ThreadPostUiModel.Header(
            name = name,
            email = email,
            date = "2024/01/01 00:00:00",
            id = "id",
        ),
        body = ThreadPostUiModel.Body(content),
    )
}
