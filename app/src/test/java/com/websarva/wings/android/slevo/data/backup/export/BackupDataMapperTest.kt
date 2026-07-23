package com.websarva.wings.android.slevo.data.backup.export

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.data.backup.export.BackupDataMapper.toBackupString
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.ThemeMode
import com.websarva.wings.android.slevo.data.model.TextDisplaySettingsConstraints
import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BackupDataMapper] の変換と、生成された JSON の field 名・型・並び順を検証する。
 */
@OptIn(ExperimentalStdlibApi::class)
class BackupDataMapperTest {
    private val moshi: Moshi = Moshi.Builder().build()

    // --- Settings ---

    @Test
    fun toBackupSettingsJson_fieldNamesAndTypes() {
        // --- Arrange ---
        val gesture = GestureSettings(
            isEnabled = true,
            showActionHints = false,
            assignments = mapOf(
                GestureDirection.Right to GestureAction.ToTop,
                GestureDirection.Left to GestureAction.Refresh,
            ),
        )

        // --- Act ---
        val json = BackupDataMapper.toBackupSettingsJson(
            themeMode = ThemeMode.DARK,
            isTreeSort = false,
            isThreadMinimapScrollbarEnabled = true,
            textScale = 1.5f,
            isIndividualTextScale = false,
            headerTextScale = 0.85f,
            bodyTextScale = 1.2f,
            lineHeight = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
            isRedirect5chNetToIoEnabled = true,
            gestureSettings = gesture,
        )
        val adapter = moshi.adapter<BackupSettingsJson>()
        val jsonStr = adapter.toJson(json)

        // --- Assert ---
        // 主要 field が存在し、型が正しいことを確認する。
        assertTrue(jsonStr.contains("\"themeMode\":\"dark\""))
        assertTrue(jsonStr.contains("\"isTreeSort\":false"))
        assertTrue(jsonStr.contains("\"isThreadMinimapScrollbarEnabled\":true"))
        assertTrue(jsonStr.contains("\"textScale\":1.5"))
        assertTrue(jsonStr.contains("\"isIndividualTextScale\":false"))
        assertTrue(
            jsonStr.contains("\"lineHeight\":${TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT}"),
        )
        assertTrue(jsonStr.contains("\"isRedirect5chNetToIoEnabled\":true"))

        // gesture
        assertTrue(jsonStr.contains("\"gestureSettings\":"))
        assertTrue(jsonStr.contains("\"enabled\":true"))
        assertTrue(jsonStr.contains("\"showActionHints\":false"))
        assertTrue(jsonStr.contains("\"actions\":"))
        // actions に key が含まれている
        assertTrue(jsonStr.contains("\"left\":\"refresh\""))
        assertTrue(jsonStr.contains("\"right\":\"to-top\""))
    }

    @Test
    fun toBackupSettingsJson_themeModeToBackupString() {
        assertEquals("light", ThemeMode.LIGHT.toBackupString())
        assertEquals("dark", ThemeMode.DARK.toBackupString())
        assertEquals("system", ThemeMode.SYSTEM.toBackupString())
    }

    @Test
    fun toBackupSettingsJson_unassignedGestureAction_isNull() {
        // --- Arrange ---
        val gesture = GestureSettings(
            isEnabled = true,
            showActionHints = true,
            assignments = mapOf(
                GestureDirection.Right to null,
                GestureDirection.Left to GestureAction.Refresh,
            ),
        )

        // --- Act ---
        val json = BackupDataMapper.toBackupSettingsJson(
            themeMode = ThemeMode.SYSTEM,
            isTreeSort = false,
            isThreadMinimapScrollbarEnabled = true,
            textScale = 1.0f,
            isIndividualTextScale = false,
            headerTextScale = 1.0f,
            bodyTextScale = 1.0f,
            lineHeight = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
            isRedirect5chNetToIoEnabled = false,
            gestureSettings = gesture,
        )
        val adapter = moshi.adapter<BackupSettingsJson>()
        val jsonStr = adapter.toJson(json)

        // --- Assert ---
        // 未割り当ての direction は map から省略される（Moshi が null value を破棄するため）。
        // 割り当てのある方向だけが含まれている。
        assertTrue(jsonStr.contains("\"left\":\"refresh\""))
        assertFalse(jsonStr.contains("\"right\""))
    }

    @Test
    fun gestureActions_keysAreSortedByKebabCase() {
        // --- Arrange ---
        val gesture = GestureSettings(
            isEnabled = true,
            showActionHints = true,
            assignments = GestureDirection.entries.associateWith { null },
        )

        // --- Act ---
        val json = BackupDataMapper.toBackupSettingsJson(
            themeMode = ThemeMode.SYSTEM,
            isTreeSort = false,
            isThreadMinimapScrollbarEnabled = true,
            textScale = 1.0f,
            isIndividualTextScale = false,
            headerTextScale = 1.0f,
            bodyTextScale = 1.0f,
            lineHeight = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
            isRedirect5chNetToIoEnabled = false,
            gestureSettings = gesture,
        )
        val adapter = moshi.adapter<BackupSettingsJson>()
        val jsonStr = adapter.toJson(json)

        // --- Assert ---
        // actions object 内の key が昇順であることを確認する。
        val actionsStart = jsonStr.indexOf("\"actions\":{")
        val actionsEnd = jsonStr.indexOf('}', actionsStart)
        val actionsContent = jsonStr.substring(actionsStart, actionsEnd + 1)
        val keys = """\"([a-z-]+)\":""".toRegex().findAll(actionsContent).map { it.groupValues[1] }.toList()
        assertEquals(keys.sorted(), keys)
    }

    // --- Tabs ---

    @Test
    fun toBackupTabsJson_fieldNameAndType() {
        // --- Act ---
        val json = BackupDataMapper.toBackupTabsJson(lastSelectedTabsPage = 2)
        val adapter = moshi.adapter<BackupTabsJson>()
        val jsonStr = adapter.toJson(json)

        // --- Assert ---
        assertEquals("""{"lastSelectedTabsPage":2}""", jsonStr)
    }

    // --- Cookies ---

    @Test
    fun toBackupCookiesJson_fieldNamesAndRequiredFields() {
        // --- Arrange ---
        val cookie = Cookie.Builder()
            .name("session")
            .value("abc123")
            .domain("example.com")
            .path("/")
            .expiresAt(253402300799000L) // distant future
            .secure()
            .httpOnly()
            .build()

        // --- Act ---
        val json = BackupDataMapper.toBackupCookiesJson(listOf(cookie))
        val adapter = moshi.adapter<BackupCookiesJson>()
        val jsonStr = adapter.toJson(json)

        // --- Assert ---
        // 9 required fields がすべて存在する。
        assertTrue(jsonStr.contains("\"name\":\"session\""))
        assertTrue(jsonStr.contains("\"value\":\"abc123\""))
        assertTrue(jsonStr.contains("\"domain\":\"example.com\""))
        assertTrue(jsonStr.contains("\"path\":\"/\""))
        assertTrue(jsonStr.contains("\"expiresAt\":"))
        assertTrue(jsonStr.contains("\"secure\":true"))
        assertTrue(jsonStr.contains("\"httpOnly\":true"))
        assertTrue(jsonStr.contains("\"hostOnly\":false"))
        assertTrue(jsonStr.contains("\"persistent\":true"))
    }

    @Test
    fun toBackupCookiesJson_sortedByDomainPathName() {
        // --- Arrange ---
        val a = Cookie.Builder().name("a").value("").domain("z.com").build()
        val b = Cookie.Builder().name("b").value("").domain("a.com").path("/x").build()
        val c = Cookie.Builder().name("c").value("").domain("a.com").path("/a").build()

        // --- Act ---
        val json = BackupDataMapper.toBackupCookiesJson(listOf(b, a, c))
        val adapter = moshi.adapter<BackupCookiesJson>()
        val jsonStr = adapter.toJson(json)

        // --- Assert ---
        // ソート順: a.com:/a/c, a.com:/x/b, z.com//a
        val cIndex = jsonStr.indexOf("\"name\":\"c\"")
        val bIndex = jsonStr.indexOf("\"name\":\"b\"")
        val aIndex = jsonStr.indexOf("\"name\":\"a\"")
        assertTrue("c before b", cIndex < bIndex)
        assertTrue("b before a", bIndex < aIndex)
    }

    @Test
    fun toBackupCookiesJson_emptyList() {
        val json = BackupDataMapper.toBackupCookiesJson(emptyList())
        val adapter = moshi.adapter<BackupCookiesJson>()
        val jsonStr = adapter.toJson(json)
        assertEquals("""{"cookies":[]}""", jsonStr)
    }

    @Test
    fun enumNameToKebabCase_convertsCorrectly() {
        // GestureDirection
        assertEquals("right", GestureDirection.Right.toBackupString())
        assertEquals("right-up", GestureDirection.RightUp.toBackupString())
        assertEquals("right-left", GestureDirection.RightLeft.toBackupString())
        assertEquals("right-down", GestureDirection.RightDown.toBackupString())
        assertEquals("left", GestureDirection.Left.toBackupString())
        assertEquals("left-up", GestureDirection.LeftUp.toBackupString())
        assertEquals("left-right", GestureDirection.LeftRight.toBackupString())
        assertEquals("left-down", GestureDirection.LeftDown.toBackupString())

        // GestureAction
        assertEquals("to-top", GestureAction.ToTop.toBackupString())
        assertEquals("to-bottom", GestureAction.ToBottom.toBackupString())
        assertEquals("switch-to-next-tab", GestureAction.SwitchToNextTab.toBackupString())
        assertEquals("switch-to-previous-tab", GestureAction.SwitchToPreviousTab.toBackupString())
        assertEquals("open-new-tab", GestureAction.OpenNewTab.toBackupString())
        assertEquals("close-tab", GestureAction.CloseTab.toBackupString())
        assertEquals("open-tab-list", GestureAction.OpenTabList.toBackupString())
        assertEquals("refresh", GestureAction.Refresh.toBackupString())
        assertEquals("post-or-create-thread", GestureAction.PostOrCreateThread.toBackupString())
        assertEquals("search", GestureAction.Search.toBackupString())
        assertEquals("open-bookmark-list", GestureAction.OpenBookmarkList.toBackupString())
        assertEquals("open-board-list", GestureAction.OpenBoardList.toBackupString())
        assertEquals("open-history", GestureAction.OpenHistory.toBackupString())
    }
}
