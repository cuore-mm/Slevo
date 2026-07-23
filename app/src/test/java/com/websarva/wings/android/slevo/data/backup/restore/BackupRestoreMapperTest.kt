package com.websarva.wings.android.slevo.data.backup.restore

import com.websarva.wings.android.slevo.data.backup.export.BackupDataMapper
import com.websarva.wings.android.slevo.data.backup.export.BackupDataMapper.toBackupString
import com.websarva.wings.android.slevo.data.backup.model.BackupCookieItem
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.ThemeMode
import com.websarva.wings.android.slevo.data.model.TextDisplaySettingsConstraints
import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BackupRestoreMapper] の逆変換ロジックを検証する。
 *
 * theme mode、gesture direction/action、Cookie field の round-trip、
 * 未割当 gesture の削除を検証する。
 */
class BackupRestoreMapperTest {

    // --- ThemeMode ---

    @Test
    fun toThemeMode_knownValues() {
        assertEquals(ThemeMode.LIGHT, BackupRestoreMapper.toThemeMode("light"))
        assertEquals(ThemeMode.DARK, BackupRestoreMapper.toThemeMode("dark"))
        assertEquals(ThemeMode.SYSTEM, BackupRestoreMapper.toThemeMode("system"))
    }

    @Test
    fun toThemeMode_unknownValue_returnsNull() {
        assertNull(BackupRestoreMapper.toThemeMode("unknown"))
    }

    // --- GestureSettings ---

    @Test
    fun toGestureSettings_mapsAllDirections() {
        val actions = mapOf(
            "right" to "to-top",
            "right-up" to "to-bottom",
            "right-left" to "open-new-tab",
            "right-down" to "close-tab",
            "left" to "refresh",
            "left-up" to "search",
            "left-right" to "open-history",
            "left-down" to "open-bookmark-list",
        )
        val settings = BackupRestoreMapper.toGestureSettings(
            enabled = true,
            showActionHints = false,
            actions = actions,
        )

        assertTrue(settings.isEnabled)
        assertEquals(false, settings.showActionHints)
        assertEquals(GestureAction.ToTop, settings.assignments[GestureDirection.Right])
        assertEquals(GestureAction.ToBottom, settings.assignments[GestureDirection.RightUp])
        assertEquals(GestureAction.OpenNewTab, settings.assignments[GestureDirection.RightLeft])
        assertEquals(GestureAction.CloseTab, settings.assignments[GestureDirection.RightDown])
        assertEquals(GestureAction.Refresh, settings.assignments[GestureDirection.Left])
        assertEquals(GestureAction.Search, settings.assignments[GestureDirection.LeftUp])
        assertEquals(GestureAction.OpenHistory, settings.assignments[GestureDirection.LeftRight])
        assertEquals(GestureAction.OpenBookmarkList, settings.assignments[GestureDirection.LeftDown])
    }

    @Test
    fun toGestureSettings_missingDirection_unassigned() {
        // left のみ設定、他は未割当
        val actions = mapOf("left" to "refresh")
        val settings = BackupRestoreMapper.toGestureSettings(
            enabled = false,
            showActionHints = true,
            actions = actions,
        )

        assertEquals(GestureAction.Refresh, settings.assignments[GestureDirection.Left])
        // 存在しない direction は null (未割当)
        assertNull(settings.assignments[GestureDirection.Right])
        assertNull(settings.assignments[GestureDirection.RightUp])
        assertNull(settings.assignments[GestureDirection.RightLeft])
        assertNull(settings.assignments[GestureDirection.RightDown])
        assertNull(settings.assignments[GestureDirection.LeftUp])
        assertNull(settings.assignments[GestureDirection.LeftRight])
        assertNull(settings.assignments[GestureDirection.LeftDown])
    }

    @Test
    fun toGestureSettings_nullAction_unassigned() {
        val actions = mapOf<String, String?>("left" to null)
        val settings = BackupRestoreMapper.toGestureSettings(
            enabled = false,
            showActionHints = true,
            actions = actions,
        )
        assertNull(settings.assignments[GestureDirection.Left])
    }

    @Test
    fun toGestureSettings_unknownAction_ignored() {
        val actions = mapOf("left" to "unknown-action")
        val settings = BackupRestoreMapper.toGestureSettings(
            enabled = false,
            showActionHints = true,
            actions = actions,
        )
        assertNull(settings.assignments[GestureDirection.Left])
    }

    // --- Tabs ---

    @Test
    fun toLastSelectedTabsPage_returnsValue() {
        assertEquals(0, BackupRestoreMapper.toLastSelectedTabsPage(0))
        assertEquals(5, BackupRestoreMapper.toLastSelectedTabsPage(5))
    }

    // --- Cookies ---

    @Test
    fun toCookie_validItem_returnsCookie() {
        val item = BackupCookieItem(
            name = "session", value = "abc", domain = "example.com", path = "/",
            expiresAt = 253402300799000L, secure = true, httpOnly = true,
            hostOnly = false, persistent = true,
        )
        val cookie = BackupRestoreMapper.toCookie(item)
        assertNotNull(cookie)
        assertEquals("session", cookie!!.name)
        assertEquals("abc", cookie.value)
        assertEquals("example.com", cookie.domain)
        assertEquals("/", cookie.path)
        assertEquals(253402300799000L, cookie.expiresAt)
        assertEquals(true, cookie.secure)
        assertEquals(true, cookie.httpOnly)
        assertEquals(false, cookie.hostOnly)
        assertEquals(true, cookie.persistent)
    }

    @Test
    fun toCookie_hostOnly_true_usesHostOnlyDomain() {
        // hostOnly=true の BackupCookieItem から復元した Cookie は
        // hostOnly == true であり、domain が正しく保持されること。
        val item = BackupCookieItem(
            name = "s", value = "v", domain = "example.com", path = "/",
            expiresAt = 0, secure = false, httpOnly = false,
            hostOnly = true, persistent = false,
        )
        val cookie = BackupRestoreMapper.toCookie(item)
        assertNotNull(cookie)
        assertEquals("example.com", cookie!!.domain)
        assertEquals(true, cookie.hostOnly)
    }

    @Test
    fun toCookie_hostOnly_false_usesDomain() {
        // hostOnly=false の BackupCookieItem から復元した Cookie は
        // hostOnly == false であること。
        val item = BackupCookieItem(
            name = "s", value = "v", domain = "example.com", path = "/",
            expiresAt = 0, secure = false, httpOnly = false,
            hostOnly = false, persistent = false,
        )
        val cookie = BackupRestoreMapper.toCookie(item)
        assertNotNull(cookie)
        assertEquals("example.com", cookie!!.domain)
        assertEquals(false, cookie.hostOnly)
    }

    @Test
    fun toCookie_expiresAt_roundTrip() {
        // BackupCookieItem の expiresAt が toCookie() で保持されること。
        val item = BackupCookieItem(
            name = "p", value = "x", domain = "example.com", path = "/",
            expiresAt = 253402300799000L, secure = false, httpOnly = false,
            hostOnly = false, persistent = true,
        )
        val cookie = BackupRestoreMapper.toCookie(item)
        assertNotNull("Cookie が null でないこと", cookie)
        assertEquals(
            "expiresAt が保持されること",
            item.expiresAt, cookie!!.expiresAt,
        )
    }

    @Test
    fun toCookies_multipleItems_returnsList() {
        val items = listOf(
            BackupCookieItem(
                name = "a", value = "1", domain = "a.com", path = "/",
                expiresAt = 0, secure = false, httpOnly = false,
                hostOnly = false, persistent = false,
            ),
            BackupCookieItem(
                name = "b", value = "2", domain = "b.com", path = "/",
                expiresAt = 0, secure = false, httpOnly = false,
                hostOnly = false, persistent = false,
            ),
        )
        val cookies = BackupRestoreMapper.toCookies(items)
        assertEquals(2, cookies.size)
        assertEquals("a", cookies[0].name)
        assertEquals("b", cookies[1].name)
    }

    // --- kebab-case helper ---

    @Test
    fun kebabCaseFromPascalCase_convertsCorrectly() {
        assertEquals("right", BackupRestoreMapper.kebabCaseFromPascalCase("Right"))
        assertEquals("right-up", BackupRestoreMapper.kebabCaseFromPascalCase("RightUp"))
        assertEquals("right-left", BackupRestoreMapper.kebabCaseFromPascalCase("RightLeft"))
        assertEquals("right-down", BackupRestoreMapper.kebabCaseFromPascalCase("RightDown"))
        assertEquals("left", BackupRestoreMapper.kebabCaseFromPascalCase("Left"))
        assertEquals("left-up", BackupRestoreMapper.kebabCaseFromPascalCase("LeftUp"))
        assertEquals("left-right", BackupRestoreMapper.kebabCaseFromPascalCase("LeftRight"))
        assertEquals("left-down", BackupRestoreMapper.kebabCaseFromPascalCase("LeftDown"))
    }

    @Test
    fun kebabCaseFromPascalCase_actions() {
        assertEquals("to-top", BackupRestoreMapper.kebabCaseFromPascalCase("ToTop"))
        assertEquals("to-bottom", BackupRestoreMapper.kebabCaseFromPascalCase("ToBottom"))
        assertEquals("switch-to-next-tab", BackupRestoreMapper.kebabCaseFromPascalCase("SwitchToNextTab"))
        assertEquals("refresh", BackupRestoreMapper.kebabCaseFromPascalCase("Refresh"))
        assertEquals("post-or-create-thread", BackupRestoreMapper.kebabCaseFromPascalCase("PostOrCreateThread"))
    }

    // --- round-trip: kebabCaseFromPascalCase と逆変換の整合 ---

    @Test
    fun roundTrip_gestureDirection_kebabCaseSymmetry() {
        // BackupDataMapper.enumNameToKebabCase と kebabCaseFromPascalCase が
        // 同じ結果を生成することを確認する。
        for (direction in GestureDirection.entries) {
            val kebab = BackupDataMapper.enumNameToKebabCase(direction.name)
            val roundTrip = BackupRestoreMapper.kebabCaseFromPascalCase(direction.name)
            assertEquals("direction ${direction.name}", kebab, roundTrip)
        }
    }

    @Test
    fun roundTrip_gestureAction_kebabCaseSymmetry() {
        for (action in GestureAction.entries) {
            val kebab = BackupDataMapper.enumNameToKebabCase(action.name)
            val roundTrip = BackupRestoreMapper.kebabCaseFromPascalCase(action.name)
            assertEquals("action ${action.name}", kebab, roundTrip)
        }
    }

    @Test
    fun roundTrip_themeMode_toBackupStringAndBack() {
        for (mode in ThemeMode.entries) {
            val backupStr = mode.toBackupString()
            val restored = BackupRestoreMapper.toThemeMode(backupStr)
            assertEquals("mode ${mode.name}", mode, restored)
        }
    }

    @Test
    fun roundTrip_gestureSettings_fullRoundTrip() {
        // 1. export: GestureSettings → BackupGestureSettings
        val original = GestureSettings(
            isEnabled = true,
            showActionHints = false,
            assignments = mapOf(
                GestureDirection.Right to GestureAction.ToTop,
                GestureDirection.Left to GestureAction.Refresh,
                GestureDirection.LeftUp to null,
            ),
        )
        val exported = BackupDataMapper.toBackupSettingsJson(
            themeMode = ThemeMode.DARK,
            isTreeSort = false,
            isThreadMinimapScrollbarEnabled = true,
            textScale = 1.5f,
            isIndividualTextScale = false,
            headerTextScale = 0.85f,
            bodyTextScale = 1.2f,
            lineHeight = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
            isRedirect5chNetToIoEnabled = true,
            gestureSettings = original,
        )

        // 2. restore: BackupGestureSettings → GestureSettings
        val restored = BackupRestoreMapper.toGestureSettings(
            enabled = exported.gestureSettings.enabled,
            showActionHints = exported.gestureSettings.showActionHints,
            actions = exported.gestureSettings.actions,
        )

        assertEquals(original.isEnabled, restored.isEnabled)
        assertEquals(original.showActionHints, restored.showActionHints)
        assertEquals(GestureAction.ToTop, restored.assignments[GestureDirection.Right])
        assertEquals(GestureAction.Refresh, restored.assignments[GestureDirection.Left])
        assertNull(restored.assignments[GestureDirection.LeftUp])
    }

    // --- Cookie round-trip: Cookie → BackupCookieItem → Cookie ---

    @Test
    fun roundTrip_cookie_hostOnlyTrue_preserved() {
        // hostOnly=true の OkHttp Cookie を export/import しても
        // hostOnly が保持されること。
        val original = Cookie.Builder()
            .name("session")
            .value("abc")
            .hostOnlyDomain("5ch.io")
            .path("/")
            .expiresAt(9999999999L)
            .secure()
            .build()

        val backupJson = BackupDataMapper.toBackupCookiesJson(listOf(original))
        assertEquals(1, backupJson.cookies.size)
        val exported = backupJson.cookies[0]
        assertEquals(true, exported.hostOnly) // export 時点で hostOnly=true

        val restored = BackupRestoreMapper.toCookie(exported)
        assertNotNull(restored)
        assertEquals(true, restored!!.hostOnly)
        assertEquals("5ch.io", restored.domain)
        assertEquals("session", restored.name)
        assertEquals("abc", restored.value)
        assertEquals("/", restored.path)
        assertEquals(9999999999L, restored.expiresAt)
        assertEquals(true, restored.secure)
    }

    @Test
    fun roundTrip_cookie_hostOnlyFalse_preserved() {
        // hostOnly=false の OkHttp Cookie を export/import しても
        // hostOnly=false が保持されること。
        val original = Cookie.Builder()
            .name("session")
            .value("abc")
            .domain("5ch.io")
            .path("/")
            .expiresAt(9999999999L)
            .build()

        val backupJson = BackupDataMapper.toBackupCookiesJson(listOf(original))
        val exported = backupJson.cookies[0]
        assertEquals(false, exported.hostOnly)

        val restored = BackupRestoreMapper.toCookie(exported)
        assertNotNull(restored)
        assertEquals(false, restored!!.hostOnly)
    }

    @Test
    fun roundTrip_cookie_persistent_expiresAtPreserved() {
        // expiresAt が round-trip で保持されること。
        // session Cookie: expiresAt は OkHttp default (HttpDate.MAX_DATE)
        val sessionCookie = Cookie.Builder()
            .name("s")
            .value("v")
            .domain("5ch.io")
            .build()
        val sessionBackup = BackupDataMapper.toBackupCookiesJson(listOf(sessionCookie))
        val sessionRestored = BackupRestoreMapper.toCookie(sessionBackup.cookies[0])
        assertNotNull(sessionRestored)
        assertEquals(sessionCookie.expiresAt, sessionRestored!!.expiresAt)

        // persistent Cookie: expiresAt=通常値
        val persistentCookie = Cookie.Builder()
            .name("s")
            .value("v")
            .domain("5ch.io")
            .expiresAt(253402300799000L)
            .build()
        val persistentBackup = BackupDataMapper.toBackupCookiesJson(listOf(persistentCookie))
        val persistentRestored = BackupRestoreMapper.toCookie(persistentBackup.cookies[0])
        assertNotNull(persistentRestored)
        assertEquals(persistentCookie.expiresAt, persistentRestored!!.expiresAt)
    }

    // --- invalid Cookie items ---

    @Test
    fun toCookie_invalidPath_returnsNull() {
        // path が "/" で始まらない場合 OkHttp が reject する。
        val item = BackupCookieItem(
            name = "s", value = "v", domain = "example.com", path = "no-slash",
            expiresAt = 0, secure = false, httpOnly = false,
            hostOnly = false, persistent = false,
        )
        assertNull(BackupRestoreMapper.toCookie(item))
    }

    @Test
    fun toCookie_emptyDomainWithHostOnly_returnsNull() {
        // hostOnly=true で domain が空の場合、OkHttp が reject する。
        val item = BackupCookieItem(
            name = "s", value = "v", domain = "", path = "/",
            expiresAt = 0, secure = false, httpOnly = false,
            hostOnly = true, persistent = false,
        )
        assertNull(BackupRestoreMapper.toCookie(item))
    }

    // --- session/persistent cookie ---

    @Test
    fun toCookie_sessionItem_remainsSessionCookie() {
        // persistent=false の backup item を toCookie() すると
        // 復元された Cookie の persistent が false であること。
        val item = BackupCookieItem(
            name = "s", value = "v", domain = "example.com", path = "/",
            expiresAt = 0, secure = false, httpOnly = false,
            hostOnly = false, persistent = false,
        )
        val cookie = BackupRestoreMapper.toCookie(item)
        assertNotNull(cookie)
        assertEquals(false, cookie!!.persistent)
    }

    @Test
    fun toCookie_persistentItem_remainsPersistent() {
        // persistent=true かつ finite expiresAt の backup item を toCookie() すると
        // 復元された Cookie の persistent が true で expiresAt が維持されること。
        val expiresAt = 9999999999L
        val item = BackupCookieItem(
            name = "s", value = "v", domain = "example.com", path = "/",
            expiresAt = expiresAt, secure = true, httpOnly = true,
            hostOnly = false, persistent = true,
        )
        val cookie = BackupRestoreMapper.toCookie(item)
        assertNotNull(cookie)
        assertEquals(true, cookie!!.persistent)
        assertEquals(expiresAt, cookie.expiresAt)
    }

    @Test
    fun toCookie_sessionItem_takesPrecedenceOverExpiresAt() {
        // persistent=false かつ finite expiresAt の矛盾 item でも
        // persistent が優先され、復元された Cookie は session cookie になること。
        val item = BackupCookieItem(
            name = "s", value = "v", domain = "example.com", path = "/",
            expiresAt = 9999999999L, secure = false, httpOnly = false,
            hostOnly = false, persistent = false,
        )
        val cookie = BackupRestoreMapper.toCookie(item)
        assertNotNull(cookie)
        assertEquals(false, cookie!!.persistent)
    }
}
