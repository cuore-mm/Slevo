package com.websarva.wings.android.slevo.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the shared URL resolver.
 */
class UrlRoutingResolverTest {
    @Test
    fun resolveUrl_parsesPcBoardUrl() {
        val resolved = resolveUrl("https://agree.5ch.net/operate/")

        assertTrue(resolved is ResolvedUrl.Board)
        resolved as ResolvedUrl.Board
        assertEquals("agree.5ch.net", resolved.host)
        assertEquals("operate", resolved.boardKey)
    }

    @Test
    fun resolveUrl_parsesPcThreadUrl() {
        val resolved = resolveUrl("https://agree.5ch.net/test/read.cgi/operate/1767525739/")

        assertTrue(resolved is ResolvedUrl.Thread)
        resolved as ResolvedUrl.Thread
        assertEquals("agree.5ch.net", resolved.host)
        assertEquals("operate", resolved.boardKey)
        assertEquals("1767525739", resolved.threadKey)
    }

    @Test
    fun resolveUrl_parsesItestBoardUrl() {
        val resolved = resolveUrl("https://itest.5ch.net/subback/operate")

        assertTrue(resolved is ResolvedUrl.ItestBoard)
        resolved as ResolvedUrl.ItestBoard
        assertEquals("operate", resolved.boardKey)
        assertEquals(null, resolved.host)
    }

    @Test
    fun resolveUrl_parsesItestThreadUrl() {
        val resolved = resolveUrl("https://itest.5ch.net/agree/test/read.cgi/operate/1767525739/")

        assertTrue(resolved is ResolvedUrl.Thread)
        resolved as ResolvedUrl.Thread
        assertEquals("agree.5ch.net", resolved.host)
        assertEquals("operate", resolved.boardKey)
        assertEquals("1767525739", resolved.threadKey)
    }

    @Test
    fun resolveUrl_returnsUnknownForDatUrl() {
        val resolved = resolveUrl("https://agree.5ch.net/operate/dat/1767525739.dat")

        assertTrue(resolved is ResolvedUrl.Unknown)
    }

    @Test
    fun resolveUrl_returnsUnknownForOysterUrl() {
        val resolved = resolveUrl("https://agree.5ch.net/operate/oyster/1767/1767525739.dat")

        assertTrue(resolved is ResolvedUrl.Unknown)
    }
}
