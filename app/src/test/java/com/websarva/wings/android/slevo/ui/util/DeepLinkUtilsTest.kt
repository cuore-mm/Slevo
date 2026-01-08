package com.websarva.wings.android.slevo.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Deep Link parsing utilities.
 */
class DeepLinkUtilsTest {
    @Test
    fun normalizeDeepLinkUrl_convertsHttpToHttps() {
        val normalized = normalizeDeepLinkUrl("http://agree.5ch.net/operate/")

        assertEquals("https://agree.5ch.net/operate/", normalized)
    }

    @Test
    fun parseDeepLinkTarget_returnsThreadTarget() {
        val target = parseDeepLinkTarget("https://agree.5ch.net/test/read.cgi/operate/1234567890/")

        assertTrue(target is DeepLinkTarget.Thread)
        target as DeepLinkTarget.Thread
        assertEquals("agree.5ch.net", target.host)
        assertEquals("operate", target.boardKey)
        assertEquals("1234567890", target.threadKey)
    }

    @Test
    fun parseDeepLinkTarget_returnsBoardTarget() {
        val target = parseDeepLinkTarget("https://agree.5ch.net/operate/")

        assertTrue(target is DeepLinkTarget.Board)
        target as DeepLinkTarget.Board
        assertEquals("agree.5ch.net", target.host)
        assertEquals("operate", target.boardKey)
    }

    @Test
    fun parseDeepLinkTarget_returnsItestTarget() {
        val target = parseDeepLinkTarget("https://itest.5ch.net/test/read.cgi/operate/1234567890/")

        assertTrue(target is DeepLinkTarget.Itest)
        target as DeepLinkTarget.Itest
        assertEquals("operate", target.boardKey)
        assertEquals("1234567890", target.threadKey)
    }

    @Test
    fun parseDeepLinkTarget_rejectsDatThreadUrl() {
        val target = parseDeepLinkTarget("https://agree.5ch.net/operate/dat/1234567890.dat")

        assertNull(target)
    }

    @Test
    fun parseDeepLinkTarget_rejectsUnknownHost() {
        val target = parseDeepLinkTarget("https://example.com/test/read.cgi/operate/1234567890/")

        assertNull(target)
    }
}
