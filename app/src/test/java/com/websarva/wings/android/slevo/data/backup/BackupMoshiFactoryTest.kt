package com.websarva.wings.android.slevo.data.backup

import com.squareup.moshi.Moshi
import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BackupMoshiFactory] の Cookie adapter 構成を検証する。
 *
 * [CookieJsonAdapter] は `@ToJson` / `@FromJson` により
 * pipe-delimited 文字列を JSON string literal として出力する。
 * `CookieLocalDataSourceImpl` による DataStore 保存形式と一致することを確認する。
 */
class BackupMoshiFactoryTest {

    /** `String` adapter との間で JSON string literal を raw string へ戻すために使う。 */
    private val bareMoshi = Moshi.Builder().build()

    @Test
    fun factory_includesCookieAdapter_roundTrip() {
        val moshi = BackupMoshiFactory.create()

        val cookie = Cookie.Builder()
            .name("session")
            .value("abc123")
            .expiresAt(9999999999L)
            .domain("5ch.io")
            .path("/")
            .secure()
            .httpOnly()
            .build()

        val adapter = moshi.adapter(Cookie::class.java)
        val json = adapter.toJson(cookie)

        // JSON string literal であること、JSON object でないことを確認する
        val raw = bareMoshi.adapter(String::class.java).fromJson(json)!!
        val parts = raw.split("|")
        assertEquals("expected 7 pipe-delimited parts, got: $raw", 7, parts.size)
        assertEquals("session", parts[0])
        assertEquals("abc123", parts[1])
        assertEquals("9999999999", parts[2])
        assertEquals("5ch.io", parts[3])
        assertEquals("/", parts[4])
        assertEquals("true", parts[5])
        assertEquals("true", parts[6])

        // round-trip: CookieLocalDataSourceImpl と同じ経路で保存・復元できること
        val restored = adapter.fromJson(json)
        assertNotNull(restored)
        assertEquals("session", restored!!.name)
        assertEquals("abc123", restored.value)
        assertEquals(9999999999L, restored.expiresAt)
        assertEquals("5ch.io", restored.domain)
        assertEquals("/", restored.path)
        assertTrue(restored.secure)
        assertTrue(restored.httpOnly)
    }

    @Test
    fun factory_serialize_returnsJsonStringLiteral_notJsonObject() {
        val moshi = BackupMoshiFactory.create()
        val cookie = Cookie.Builder()
            .name("test")
            .value("val")
            .domain("example.com")
            .build()

        val json = moshi.adapter(Cookie::class.java).toJson(cookie)

        // JSON object（{...}）ではなく JSON string literal（"..."）であること
        assertTrue(
            "should be JSON string literal, not a JSON object: $json",
            json.startsWith("\"") && !json.startsWith("{"),
        )

        // raw 文字列部分が pipe-delimited であること
        val raw = bareMoshi.adapter(String::class.java).fromJson(json)!!
        assertTrue("raw should start with test|: $raw", raw.startsWith("test|"))
    }
}
