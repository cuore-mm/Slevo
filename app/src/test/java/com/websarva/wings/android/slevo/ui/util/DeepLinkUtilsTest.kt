package com.websarva.wings.android.slevo.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deep Link解決処理のユニットテスト。
 */
class DeepLinkUtilsTest {
    @Test
    fun resolveDeepLinkUrl_returnsThreadTarget() {
        val target = resolveDeepLinkUrl("https://agree.5ch.net/test/read.cgi/operate/1234567890/")

        assertTrue(target is ResolvedUrl.Thread)
        target as ResolvedUrl.Thread
        assertEquals("agree.5ch.net", target.host)
        assertEquals("operate", target.boardKey)
        assertEquals("1234567890", target.threadKey)
    }

    @Test
    fun resolveDeepLinkUrl_returnsBoardTarget() {
        val target = resolveDeepLinkUrl("https://agree.5ch.net/operate/")

        assertTrue(target is ResolvedUrl.Board)
        target as ResolvedUrl.Board
        assertEquals("agree.5ch.net", target.host)
        assertEquals("operate", target.boardKey)
    }

    @Test
    fun resolveDeepLinkUrl_returnsItestBoardTarget() {
        val target = resolveDeepLinkUrl("https://itest.5ch.net/subback/operate")

        assertTrue(target is ResolvedUrl.ItestBoard)
        target as ResolvedUrl.ItestBoard
        assertEquals("operate", target.boardKey)
        assertNull(target.host)
    }

    @Test
    fun resolveDeepLinkUrl_returnsItestThreadTarget() {
        val target = resolveDeepLinkUrl("https://itest.5ch.net/agree/test/read.cgi/operate/1234567890/")

        assertTrue(target is ResolvedUrl.Thread)
        target as ResolvedUrl.Thread
        assertEquals("agree.5ch.net", target.host)
        assertEquals("operate", target.boardKey)
        assertEquals("1234567890", target.threadKey)
    }

    @Test
    fun resolveDeepLinkUrl_rejectsDatThreadUrl() {
        val target = resolveDeepLinkUrl("https://agree.5ch.net/operate/dat/1234567890.dat")

        assertNull(target)
    }

    @Test
    fun resolveDeepLinkUrl_rejectsUnknownHost() {
        val target = resolveDeepLinkUrl("https://example.com/test/read.cgi/operate/1234567890/")

        assertNull(target)
    }
}
