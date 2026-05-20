package com.websarva.wings.android.slevo.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 5ch.net から 5ch.io へのURL正規化を検証するテスト。
 */
class BoardUrlNormalizationTest {
    /**
     * 5ch.net の板URLが 5ch.io に正規化されることを確認する。
     */
    @Test
    fun normalizeBoardUrlTo5chIo_convertsNetHost() {
        val input = BoardUrlNormalizationInput(
            boardUrl = "https://agree.5ch.net/operate/",
            isEnabled = true,
        )

        val normalized = normalizeBoardUrlTo5chIo(input)

        assertEquals("https://agree.5ch.io/operate/", normalized)
    }

    /**
     * itest の 5ch.net URLが 5ch.io に正規化されることを確認する。
     */
    @Test
    fun normalizeBoardUrlTo5chIo_convertsItestNetHost() {
        val input = BoardUrlNormalizationInput(
            boardUrl = "https://itest.5ch.net/subback/operate",
            isEnabled = true,
        )

        val normalized = normalizeBoardUrlTo5chIo(input)

        assertEquals("https://itest.5ch.io/subback/operate", normalized)
    }

    /**
     * 既に 5ch.io のURLは正規化しても変わらないことを確認する。
     */
    @Test
    fun normalizeBoardUrlTo5chIo_preservesIoHost() {
        val input = BoardUrlNormalizationInput(
            boardUrl = "https://agree.5ch.io/operate/",
            isEnabled = true,
        )

        val normalized = normalizeBoardUrlTo5chIo(input)

        assertEquals("https://agree.5ch.io/operate/", normalized)
    }

    /**
     * 設定オフでは正規化しないことを確認する。
     */
    @Test
    fun normalizeBoardUrlTo5chIo_skipsWhenDisabled() {
        val input = BoardUrlNormalizationInput(
            boardUrl = "https://agree.5ch.net/operate/",
            isEnabled = false,
        )

        val normalized = normalizeBoardUrlTo5chIo(input)

        assertEquals("https://agree.5ch.net/operate/", normalized)
    }

    /**
     * 5ch.net以外のドメインは正規化しないことを確認する。
     */
    @Test
    fun normalizeBoardUrlTo5chIo_skipsOtherDomains() {
        val input = BoardUrlNormalizationInput(
            boardUrl = "https://example.bbspink.com/operate/",
            isEnabled = true,
        )

        val normalized = normalizeBoardUrlTo5chIo(input)

        assertEquals("https://example.bbspink.com/operate/", normalized)
    }
}
