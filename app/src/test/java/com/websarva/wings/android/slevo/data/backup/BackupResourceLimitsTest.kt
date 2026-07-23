package com.websarva.wings.android.slevo.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [BackupResourceLimits] の既定上限値と既知エントリのmappingを検証する。
 */
class BackupResourceLimitsTest {

    @Test
    fun defaultLimits_returnsExpectedValues() {
        val limits = BackupResourceLimits()
        assertEquals(64L * 1024, limits.manifestBytes)
        assertEquals(256L * 1024 * 1024, limits.databaseBytes)
        assertEquals(1L * 1024 * 1024, limits.settingsBytes)
        assertEquals(64L * 1024, limits.tabsBytes)
        assertEquals(8L * 1024 * 1024, limits.cookiesBytes)
        assertEquals(272L * 1024 * 1024, limits.totalBytes)
        assertEquals(7, limits.entryCount)
    }

    @Test
    fun limitForEntry_knownEntries_returnsCorrectValue() {
        val limits = BackupResourceLimits()
        assertEquals(64L * 1024, limits.limitForEntry("manifest.json"))
        assertEquals(256L * 1024 * 1024, limits.limitForEntry("database/slevo.db"))
        assertEquals(1L * 1024 * 1024, limits.limitForEntry("datastore/settings.json"))
        assertEquals(64L * 1024, limits.limitForEntry("datastore/tabs.json"))
        assertEquals(8L * 1024 * 1024, limits.limitForEntry("datastore/cookies.json"))
    }

    @Test
    fun limitForEntry_unknownEntry_returnsNull() {
        val limits = BackupResourceLimits()
        assertNull(limits.limitForEntry("unknown/file.txt"))
        assertNull(limits.limitForEntry(""))
    }

    @Test
    fun customLimits_overrideDefaults() {
        val limits = BackupResourceLimits(
            manifestBytes = 10,
            databaseBytes = 20,
            settingsBytes = 30,
            tabsBytes = 40,
            cookiesBytes = 50,
            totalBytes = 100,
            entryCount = 3,
        )
        assertEquals(10L, limits.limitForEntry("manifest.json"))
        assertEquals(20L, limits.limitForEntry("database/slevo.db"))
        assertEquals(100L, limits.totalBytes)
        assertEquals(3, limits.entryCount)
    }
}
