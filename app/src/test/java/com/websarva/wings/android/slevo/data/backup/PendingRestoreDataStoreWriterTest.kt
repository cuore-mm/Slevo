package com.websarva.wings.android.slevo.data.backup

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.websarva.wings.android.slevo.data.datasource.local.impl.SlevoPreferenceDataStores
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        context = io.mockk.mockk(relaxed = true),
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

    private fun backupSettings(actions: Map<String, String>): com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson {
        return com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson(
            themeMode = "dark",
            isTreeSort = false,
            isThreadMinimapScrollbarEnabled = true,
            textScale = 1.5f,
            isIndividualTextScale = true,
            headerTextScale = 1.3f,
            bodyTextScale = 1.4f,
            lineHeight = 1.6f,
            isRedirect5chNetToIoEnabled = true,
            gestureSettings = com.websarva.wings.android.slevo.data.backup.model.BackupGestureSettings(
                enabled = true,
                showActionHints = false,
                actions = actions,
            ),
        )
    }
}
