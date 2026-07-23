package com.websarva.wings.android.slevo.data.backup.pending

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.websarva.wings.android.slevo.data.backup.BackupMoshiFactory
import com.websarva.wings.android.slevo.data.backup.restore.BackupRestoreMapper
import com.websarva.wings.android.slevo.data.backup.model.BackupCookieItem
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupGestureSettings
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.datasource.local.impl.SlevoPreferenceDataStores
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import io.mockk.mockk
import okhttp3.Cookie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PendingRestoreDataStoreWriter] の companion object メソッドと
 * [SlevoPreferenceDataStores] key の整合性を検証する。
 *
 * DataStore I/O は Android framework に依存するため、
 * companion object の pure function と key format のみをテストする。
 * Hilt 経由 DataSource/Repository/DAO/AppDatabase に依存しないことを確認する。
 */
class PendingRestoreDataStoreWriterTest {
    private val writer = PendingRestoreDataStoreWriter(
        context = mockk(relaxed = true),
        moshi = BackupMoshiFactory.create(),
    )

    // --- kebabToPascalCase ---

    @Test
    fun kebabToPascalCase_convertsCorrectly() {
        assertEquals("Refresh", PendingRestoreDataStoreWriter.kebabToPascalCase("refresh"))
        assertEquals("ToTop", PendingRestoreDataStoreWriter.kebabToPascalCase("to-top"))
        assertEquals("SwitchToNextTab", PendingRestoreDataStoreWriter.kebabToPascalCase("switch-to-next-tab"))
        assertEquals("PostOrCreateThread", PendingRestoreDataStoreWriter.kebabToPascalCase("post-or-create-thread"))
        assertEquals("OpenBookmarkList", PendingRestoreDataStoreWriter.kebabToPascalCase("open-bookmark-list"))
    }

    @Test
    fun toExistingGestureActionNameOrNull_returnsOnlyKnownActions() {
        assertEquals("Refresh", PendingRestoreDataStoreWriter.toExistingGestureActionNameOrNull("refresh"))
        assertEquals(
            "SwitchToNextTab",
            PendingRestoreDataStoreWriter.toExistingGestureActionNameOrNull("switch-to-next-tab"),
        )
        assertEquals(null, PendingRestoreDataStoreWriter.toExistingGestureActionNameOrNull("unknown-action"))
    }

    // --- Gesture action key format ---

    @Test
    fun gestureActionKey_matchesExistingFormat() {
        for (direction in GestureDirection.entries) {
            val key = SlevoPreferenceDataStores.GESTURE_ACTION_KEYS.getValue(direction)
            val expected = "gesture_action_${direction.name.lowercase()}"
            assertEquals("key for ${direction.name}", expected, key.name)
        }
    }

    // --- DataStore key consistency ---

    @Test
    fun dataStoreKeys_matchDataSourceKeys() {
        // SlevoPreferenceDataStores の key が DataSourceImpl と同じ名前であることを確認する。
        assertEquals("theme_mode", SlevoPreferenceDataStores.THEME_MODE_KEY.name)
        assertEquals("tree_sort", SlevoPreferenceDataStores.TREE_SORT_KEY.name)
        assertEquals("thread_minimap_scrollbar", SlevoPreferenceDataStores.THREAD_MINIMAP_SCROLLBAR_KEY.name)
        assertEquals("text_scale", SlevoPreferenceDataStores.TEXT_SCALE_KEY.name)
        assertEquals("individual_text_scale", SlevoPreferenceDataStores.INDIVIDUAL_TEXT_SCALE_KEY.name)
        assertEquals("header_text_scale", SlevoPreferenceDataStores.HEADER_TEXT_SCALE_KEY.name)
        assertEquals("body_text_scale", SlevoPreferenceDataStores.BODY_TEXT_SCALE_KEY.name)
        assertEquals("line_height", SlevoPreferenceDataStores.LINE_HEIGHT_KEY.name)
        assertEquals("redirect_5ch_net_to_io", SlevoPreferenceDataStores.REDIRECT_5CH_NET_TO_IO_KEY.name)
        assertEquals("gesture_enabled", SlevoPreferenceDataStores.GESTURE_ENABLED_KEY.name)
        assertEquals("gesture_show_action_hint", SlevoPreferenceDataStores.GESTURE_SHOW_HINT_KEY.name)
        assertEquals(
            "gesture_assignments_initialized",
            SlevoPreferenceDataStores.GESTURE_ASSIGNMENTS_INITIALIZED_KEY.name,
        )
        assertEquals("last_selected_page", SlevoPreferenceDataStores.LAST_PAGE_KEY.name)
        assertEquals("app_cookies", SlevoPreferenceDataStores.COOKIE_KEY.name)
    }

    @Test
    fun applySettingsToPreferences_fullOverwriteAndGestureValidation() {
        val prefs = mutablePreferencesOf(
            SlevoPreferenceDataStores.THEME_MODE_KEY to "light",
            SlevoPreferenceDataStores.GESTURE_ACTION_KEYS.getValue(GestureDirection.Right) to "OldValue",
            SlevoPreferenceDataStores.GESTURE_ACTION_KEYS.getValue(GestureDirection.Left) to "OldValue",
        )
        val settings = backupSettings(
            actions = mapOf(
                BackupRestoreMapper.kebabCaseFromPascalCase(GestureDirection.Right.name) to "refresh",
                BackupRestoreMapper.kebabCaseFromPascalCase(GestureDirection.Left.name) to "unknown-action",
            ),
        )

        writer.applySettingsToPreferences(prefs, settings)

        assertEquals("dark", prefs[SlevoPreferenceDataStores.THEME_MODE_KEY])
        assertEquals(false, prefs[SlevoPreferenceDataStores.TREE_SORT_KEY])
        assertEquals(
            GestureAction.Refresh.name,
            prefs[SlevoPreferenceDataStores.GESTURE_ACTION_KEYS.getValue(GestureDirection.Right)],
        )
        assertFalse(prefs.contains(SlevoPreferenceDataStores.GESTURE_ACTION_KEYS.getValue(GestureDirection.Left)))
        assertTrue(prefs[SlevoPreferenceDataStores.GESTURE_ASSIGNMENTS_INITIALIZED_KEY] == true)
    }

    private fun backupSettings(actions: Map<String, String>): BackupSettingsJson {
        return BackupSettingsJson(
            themeMode = "dark",
            isTreeSort = false,
            isThreadMinimapScrollbarEnabled = true,
            textScale = 1.5f,
            isIndividualTextScale = true,
            headerTextScale = 1.3f,
            bodyTextScale = 1.4f,
            lineHeight = 1.6f,
            isRedirect5chNetToIoEnabled = true,
            gestureSettings = BackupGestureSettings(
                enabled = true,
                showActionHints = false,
                actions = actions,
            ),
        )
    }

    // --- snapshot / rollback ---

    @Test
    fun restoreToMutablePreferences_overwritesAllKeys() {
        // snapshot に含まれる key/value だけが残り、既存 key は削除されること。
        val target = mutablePreferencesOf(
            stringPreferencesKey("oldKey") to "oldValue",
            stringPreferencesKey("keep") to "willBeOverwritten",
        )
        val snapshot = mutablePreferencesOf(
            stringPreferencesKey("keep") to "snapshotValue",
            stringPreferencesKey("newKey") to "newValue",
        ).toPreferences()

        writer.restoreToMutablePreferences(target, snapshot)

        assertEquals("snapshotValue", target[stringPreferencesKey("keep")])
        assertEquals("newValue", target[stringPreferencesKey("newKey")])
        assertEquals(null, target[stringPreferencesKey("oldKey")])
    }

    @Test
    fun restoreToMutablePreferences_supportsAllKeyTypes() {
        // restore helper が String / Boolean / Float / Int / StringSet の
        // 複数 key type を正しく戻せること。
        val sKey = stringPreferencesKey("s")
        val bKey = booleanPreferencesKey("b")
        val fKey = floatPreferencesKey("f")
        val iKey = intPreferencesKey("i")
        val ssKey = stringSetPreferencesKey("ss")
        val snapshot = mutablePreferencesOf(
            sKey to "hello",
            bKey to true,
            fKey to 3.14f,
            iKey to 42,
            ssKey to setOf("a", "b"),
        ).toPreferences()

        val target = mutablePreferencesOf()
        writer.restoreToMutablePreferences(target, snapshot)

        assertEquals("hello", target[sKey])
        assertEquals(true, target[bKey])
        assertEquals(3.14f, target[fKey])
        assertEquals(42, target[iKey])
        assertEquals(setOf("a", "b"), target[ssKey])
    }

    @Test
    fun restoreToMutablePreferences_emptySnapshot_clearsTarget() {
        // 空 snapshot を restore するとすべての key が削除されること。
        val target = mutablePreferencesOf(
            stringPreferencesKey("key") to "value",
        )
        val snapshot = mutablePreferencesOf().toPreferences()

        writer.restoreToMutablePreferences(target, snapshot)

        assertTrue(target.asMap().isEmpty())
    }

    @Test
    fun dataStoreSnapshot_cookiesNullable() {
        // DataStoreSnapshot の cookies は nullable であること。
        val snapshot = PendingRestoreDataStoreWriter.DataStoreSnapshot(
            settings = mutablePreferencesOf().toPreferences(),
            tabs = mutablePreferencesOf().toPreferences(),
            cookies = null,
        )
        assertEquals(null, snapshot.cookies)
    }

    @Test
    fun prepareCookies_sessionCookie_returnsSuccessAndPersistentFalse() {
        // persistent=false の valid BackupCookieItem を prepareCookies すると
        // success となり、prepared record を deserialize すると persistent==false になること。
        val item = BackupCookieItem(
            name = "s", value = "v", domain = "example.com", path = "/",
            expiresAt = 0, secure = false, httpOnly = false,
            hostOnly = false, persistent = false,
        )
        val cookies = BackupCookiesJson(cookies = listOf(item))
        val result = writer.prepareCookies(cookies)
        assertTrue("expected Success for session cookie", result is PendingRestoreDataStoreWriter.PreparedCookies.Success)

        val set = (result as PendingRestoreDataStoreWriter.PreparedCookies.Success).cookieJsonSet
        assertEquals(1, set.size)
        val cookieAdapter = BackupMoshiFactory.create().adapter(Cookie::class.java)
        val restored = cookieAdapter.fromJson(set.first())
        assertNotNull(restored)
        assertEquals(false, restored!!.persistent)
    }

    @Test
    fun prepareCookies_persistentCookie_returnsSuccessAndPreservesExpiresAt() {
        // persistent=true かつ finite expiresAt の valid BackupCookieItem を prepareCookies すると
        // success となり、prepared record の persistent==true かつ expiresAt が維持されること。
        val expiresAt = 9999999999L
        val item = BackupCookieItem(
            name = "p", value = "x", domain = "example.com", path = "/",
            expiresAt = expiresAt, secure = true, httpOnly = true,
            hostOnly = false, persistent = true,
        )
        val cookies = BackupCookiesJson(cookies = listOf(item))
        val result = writer.prepareCookies(cookies)
        assertTrue("expected Success for persistent cookie", result is PendingRestoreDataStoreWriter.PreparedCookies.Success)

        val set = (result as PendingRestoreDataStoreWriter.PreparedCookies.Success).cookieJsonSet
        assertEquals(1, set.size)
        val cookieAdapter = BackupMoshiFactory.create().adapter(Cookie::class.java)
        val restored = cookieAdapter.fromJson(set.first())
        assertNotNull(restored)
        assertEquals(true, restored!!.persistent)
        assertEquals(expiresAt, restored.expiresAt)
    }

    private fun validCookieItem(
        name: String = "s",
        domain: String = "example.com",
    ) = BackupCookieItem(
        name = name, value = "v", domain = domain, path = "/",
        expiresAt = 0L, secure = false, httpOnly = false,
        hostOnly = false, persistent = false,
    )

    private fun invalidCookieItem() = BackupCookieItem(
        // path が "/" で始まらないため OkHttp Cookie.Builder が reject する。
        name = "s", value = "v", domain = "example.com", path = "no-slash",
        expiresAt = 0L, secure = false, httpOnly = false,
        hostOnly = false, persistent = false,
    )

    @Test
    fun prepareCookies_validCookies_returnsSuccess() {
        val cookies = BackupCookiesJson(cookies = listOf(
            validCookieItem("a"), validCookieItem("b"),
        ))
        val result = writer.prepareCookies(cookies)
        assertTrue("expected Success", result is PendingRestoreDataStoreWriter.PreparedCookies.Success)
        val set = (result as PendingRestoreDataStoreWriter.PreparedCookies.Success).cookieJsonSet
        assertEquals(2, set.size)
    }

    @Test
    fun prepareCookies_emptyList_returnsSuccessWithEmptySet() {
        val cookies = BackupCookiesJson(cookies = emptyList())
        val result = writer.prepareCookies(cookies)
        assertTrue("expected Success", result is PendingRestoreDataStoreWriter.PreparedCookies.Success)
        val set = (result as PendingRestoreDataStoreWriter.PreparedCookies.Success).cookieJsonSet
        assertTrue(set.isEmpty())
    }

    @Test
    fun prepareCookies_oneInvalidCookie_returnsFailure() {
        val cookies = BackupCookiesJson(cookies = listOf(invalidCookieItem()))
        val result = writer.prepareCookies(cookies)
        assertTrue("expected Failure for invalid path", result is PendingRestoreDataStoreWriter.PreparedCookies.Failure)
    }

    @Test
    fun prepareCookies_validAndInvalidMixed_returnsFailure() {
        val cookies = BackupCookiesJson(cookies = listOf(
            validCookieItem("a"), invalidCookieItem(),
        ))
        val result = writer.prepareCookies(cookies)
        assertTrue("expected Failure for mixed", result is PendingRestoreDataStoreWriter.PreparedCookies.Failure)
        val msg = (result as PendingRestoreDataStoreWriter.PreparedCookies.Failure).message
        assertTrue(msg.contains("failed=1"))
        assertTrue(msg.contains("total=2"))
    }

    @Test
    fun prepareCookies_allInvalid_returnsFailureWithCounts() {
        val cookies = BackupCookiesJson(cookies = listOf(
            invalidCookieItem(), invalidCookieItem(),
        ))
        val result = writer.prepareCookies(cookies)
        assertTrue("expected Failure", result is PendingRestoreDataStoreWriter.PreparedCookies.Failure)
        val msg = (result as PendingRestoreDataStoreWriter.PreparedCookies.Failure).message
        assertTrue(msg.contains("failed=2"))
        assertTrue(msg.contains("total=2"))
    }
}
