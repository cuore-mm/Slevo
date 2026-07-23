package com.websarva.wings.android.slevo.data.backup

import com.websarva.wings.android.slevo.data.model.GestureDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [PendingRestoreDataStoreWriter] の companion object メソッドを検証する。
 *
 * DataStore I/O は Android framework に依存するため、
 * companion object の pure function のみをテストする。
 * Hilt 経由 DataSource/Repository/DAO/AppDatabase に依存しないことを確認する。
 */
class PendingRestoreDataStoreWriterTest {

    // --- kebabToPascalCase ---

    @Test
    fun kebabToPascalCase_convertsCorrectly() {
        assertEquals("Refresh", PendingRestoreDataStoreWriter.kebabToPascalCase("refresh"))
        assertEquals("ToTop", PendingRestoreDataStoreWriter.kebabToPascalCase("to-top"))
        assertEquals("SwitchToNextTab", PendingRestoreDataStoreWriter.kebabToPascalCase("switch-to-next-tab"))
        assertEquals("PostOrCreateThread", PendingRestoreDataStoreWriter.kebabToPascalCase("post-or-create-thread"))
        assertEquals("OpenBookmarkList", PendingRestoreDataStoreWriter.kebabToPascalCase("open-bookmark-list"))
    }

    // --- Gesture action key format ---

    @Test
    fun gestureActionKey_matchesExistingFormat() {
        // 既存 SettingsLocalDataSourceImpl と同じ key format であることを確認する。
        for (direction in GestureDirection.entries) {
            val key = PendingRestoreDataStoreWriter.gestureActionKey(direction)
            val expected = "gesture_action_${direction.name.lowercase()}"
            assertEquals("key for ${direction.name}", expected, key.name)
        }
    }
}
