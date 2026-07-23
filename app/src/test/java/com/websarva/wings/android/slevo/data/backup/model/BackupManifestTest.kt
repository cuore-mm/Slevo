package com.websarva.wings.android.slevo.data.backup.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BackupManifest] の JSON encode/decode と `included.cookies` の分岐を検証する。
 */
@OptIn(ExperimentalStdlibApi::class)
class BackupManifestTest {
    private val moshi: Moshi = Moshi.Builder().build()

    @Test
    fun manifest_encodeDecode_cookiesIncluded() {
        // --- Arrange ---
        val manifest = BackupManifest(
            createdAt = "2026-06-22T12:00:00Z",
            appVersionCode = 1,
            appVersionName = "1.0.0",
            databaseVersion = 9,
            included = IncludedContents(cookies = true),
        )
        val adapter = moshi.adapter<BackupManifest>()

        // --- Act ---
        val json = adapter.toJson(manifest)
        val decoded = adapter.fromJson(json)

        // --- Assert ---
        assertEquals(
            """{"backupFormatVersion":1,"backupMode":"full","createdAt":"2026-06-22T12:00:00Z",""" +
                """"appVersionCode":1,"appVersionName":"1.0.0","databaseVersion":9,""" +
                """"included":{"database":true,"settings":true,"tabs":true,"cookies":true}}""",
            json
        )
        assertNotNull(decoded)
        assertEquals(1, decoded?.backupFormatVersion)
        assertEquals("full", decoded?.backupMode)
        assertEquals("2026-06-22T12:00:00Z", decoded?.createdAt)
        assertEquals(1L, decoded?.appVersionCode)
        assertEquals("1.0.0", decoded?.appVersionName)
        assertEquals(9, decoded?.databaseVersion)
        assertTrue(decoded?.included?.cookies == true)
        assertTrue(decoded?.included?.database == true)
    }

    @Test
    fun manifest_encodeDecode_cookiesNotIncluded() {
        // --- Arrange ---
        val manifest = BackupManifest(
            createdAt = "2026-06-22T12:00:00Z",
            appVersionCode = 2,
            appVersionName = "2.0.0",
            databaseVersion = 9,
            included = IncludedContents(cookies = false),
        )
        val adapter = moshi.adapter<BackupManifest>()

        // --- Act ---
        val json = adapter.toJson(manifest)
        val decoded = adapter.fromJson(json)

        // --- Assert ---
        assertNotNull(decoded)
        assertFalse(decoded?.included?.cookies == true)
        assertEquals(2L, decoded?.appVersionCode)
        assertEquals("2.0.0", decoded?.appVersionName)
    }

    @Test
    fun manifest_defaultBackupFormatVersion_is1() {
        // Moshi は Kotlin デフォルト値を使わないため、JSON に明示的に含める。
        val manifest = BackupManifest(
            backupFormatVersion = 1,
            createdAt = "2026-01-01T00:00:00Z",
            appVersionCode = 1,
            appVersionName = "",
            databaseVersion = 9,
            included = IncludedContents(cookies = false),
        )
        val adapter = moshi.adapter<BackupManifest>()
        val json = adapter.toJson(manifest)
        val decoded = adapter.fromJson(json)
        assertNotNull(decoded)
        assertEquals(1, decoded?.backupFormatVersion)
    }

    @Test
    fun manifest_defaultBackupMode_isFull() {
        val manifest = BackupManifest(
            backupMode = "full",
            createdAt = "2026-01-01T00:00:00Z",
            appVersionCode = 1,
            appVersionName = "",
            databaseVersion = 9,
            included = IncludedContents(cookies = false),
        )
        val adapter = moshi.adapter<BackupManifest>()
        val json = adapter.toJson(manifest)
        val decoded = adapter.fromJson(json)
        assertNotNull(decoded)
        assertEquals("full", decoded?.backupMode)
    }
}
