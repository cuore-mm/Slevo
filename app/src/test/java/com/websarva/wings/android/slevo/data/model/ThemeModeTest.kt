package com.websarva.wings.android.slevo.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ThemeMode] の永続化値変換とテーマ導出を検証するテスト。
 */
class ThemeModeTest {
    @Test
    fun fromStorageValue_returnsSystemWhenNullOrUnknown() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageValue("unknown"))
    }

    @Test
    fun resolveDarkTheme_usesExplicitModeAndSystemFallback() {
        assertFalse(ThemeMode.LIGHT.resolveDarkTheme(isSystemDark = true))
        assertTrue(ThemeMode.DARK.resolveDarkTheme(isSystemDark = false))
        assertTrue(ThemeMode.SYSTEM.resolveDarkTheme(isSystemDark = true))
        assertFalse(ThemeMode.SYSTEM.resolveDarkTheme(isSystemDark = false))
    }
}
