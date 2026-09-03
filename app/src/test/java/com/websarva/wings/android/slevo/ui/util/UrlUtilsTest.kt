package com.websarva.wings.android.slevo.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * URLユーティリティの挙動を検証するテスト。
 */
class UrlUtilsTest {
    /**
     * itest.5ch.io の板URLが解析できることを確認する。
     */
    @Test
    fun parseItestUrl_parsesItestIoBoard() {
        val info = parseItestUrl("https://itest.5ch.io/subback/operate")

        assertEquals("operate", info?.boardKey)
        assertEquals(null, info?.threadKey)
    }

    /**
     * itest.5ch.io のスレURLが解析できることを確認する。
     */
    @Test
    fun parseItestUrl_parsesItestIoThread() {
        val info = parseItestUrl("https://itest.5ch.io/agree/test/read.cgi/operate/1234567890/")

        assertEquals("operate", info?.boardKey)
        assertEquals("1234567890", info?.threadKey)
    }

    /**
     * itest.2ch.sc は許可されないことを確認する。
     */
    @Test
    fun parseItestUrl_rejectsItest2ch() {
        val info = parseItestUrl("https://itest.2ch.sc/subback/operate")

        assertNull(info)
    }

    /**
     * URLから登録ドメイン形式のサービス名を抽出できることを確認する。
     */
    @Test
    fun parseServiceName_extractsRegistrableDomain() {
        val serviceName = parseServiceName("https://agree.5ch.net/operate/")

        assertEquals("5ch.net", serviceName)
    }

    /**
     * ホストを持たないURLでは空文字を返すことを確認する。
     */
    @Test
    fun parseServiceName_returnsEmptyForInvalidUrl() {
        val serviceName = parseServiceName("not a url")

        assertEquals("", serviceName)
    }
}
