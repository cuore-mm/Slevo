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
        assertEquals("expected 8 pipe-delimited parts, got: $raw", 8, parts.size)
        assertEquals("session", parts[0])
        assertEquals("abc123", parts[1])
        assertEquals("9999999999", parts[2])
        assertEquals("5ch.io", parts[3])
        assertEquals("/", parts[4])
        assertEquals("true", parts[5])
        assertEquals("true", parts[6])
        assertEquals("false", parts[7]) // hostOnly: .domain() を使っているため false

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
        assertEquals(false, restored.hostOnly)
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

    @Test
    fun adapter_hostOnlyTrue_roundTrip() {
        // hostOnly=true の Cookie が serialize/deserialize を経ても
        // hostOnly == true として復元されること。
        val moshi = BackupMoshiFactory.create()
        val cookie = Cookie.Builder()
            .name("s")
            .value("v")
            .hostOnlyDomain("5ch.io")
            .path("/")
            .build()

        val adapter = moshi.adapter(Cookie::class.java)
        val json = adapter.toJson(cookie)

        // 8 field であること
        val raw = bareMoshi.adapter(String::class.java).fromJson(json)!!
        val parts = raw.split("|")
        assertEquals("expected 8 pipe-delimited parts, got: $raw", 8, parts.size)
        assertEquals("true", parts[7]) // hostOnly

        val restored = adapter.fromJson(json)
        assertNotNull(restored)
        assertEquals("5ch.io", restored!!.domain)
        assertEquals(true, restored.hostOnly)
    }

    @Test
    fun adapter_hostOnlyFalse_roundTrip() {
        // hostOnly=false の Cookie が serialize/deserialize を経ても
        // hostOnly == false として復元されること。
        val moshi = BackupMoshiFactory.create()
        val cookie = Cookie.Builder()
            .name("s")
            .value("v")
            .domain("5ch.io")
            .path("/")
            .build()

        val adapter = moshi.adapter(Cookie::class.java)
        val json = adapter.toJson(cookie)

        val raw = bareMoshi.adapter(String::class.java).fromJson(json)!!
        val parts = raw.split("|")
        assertEquals("false", parts[7]) // hostOnly

        val restored = adapter.fromJson(json)
        assertNotNull(restored)
        assertEquals(false, restored!!.hostOnly)
    }

    @Test
    fun adapter_legacySevenField_deserializedAsDomainScoped() {
        // 旧 7 field 形式を読み込み、hostOnly == false として復元すること。
        val moshi = BackupMoshiFactory.create()
        val adapter = moshi.adapter(Cookie::class.java)

        // 旧形式: 7 field (hostOnly なし)
        val legacyRaw = "session|abc123|9999999999|5ch.io|/|true|true"
        val legacyJson = bareMoshi.adapter(String::class.java).toJson(legacyRaw)

        val restored = adapter.fromJson(legacyJson)
        assertNotNull("legacy 7 field 形式も読み込めること", restored)
        assertEquals("session", restored!!.name)
        assertEquals("abc123", restored.value)
        assertEquals(9999999999L, restored.expiresAt)
        assertEquals("5ch.io", restored.domain)
        assertEquals("/", restored.path)
        assertEquals(true, restored.secure)
        assertEquals(true, restored.httpOnly)
        assertEquals(false, restored.hostOnly) // 旧形式では false
    }

    @Test
    fun adapter_invalidFormat_returnsNull() {
        // field 数不足の場合は null を返すこと。
        val moshi = BackupMoshiFactory.create()
        val adapter = moshi.adapter(Cookie::class.java)

        // 6 field 未満
        val shortRaw = "a|b|c|d|e|f"
        val shortJson = bareMoshi.adapter(String::class.java).toJson(shortRaw)
        assertEquals(null, adapter.fromJson(shortJson))

        // expiresAt が数値でない
        val badExpiryRaw = "a|b|NOT_A_NUMBER|d|e|f|false|false"
        val badExpiryJson = bareMoshi.adapter(String::class.java).toJson(badExpiryRaw)
        assertEquals(null, adapter.fromJson(badExpiryJson))
    }
}
