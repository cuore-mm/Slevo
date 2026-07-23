package com.websarva.wings.android.slevo.data.backup.pending

import android.util.AtomicFile
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.data.backup.BackupMoshiFactory
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [PendingRestoreDataStoreSnapshotStore] の型変換、atomic publish、validationを検証する。 */
@OptIn(ExperimentalStdlibApi::class)
@RunWith(RobolectricTestRunner::class)
class PendingRestoreDataStoreSnapshotTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val moshi = BackupMoshiFactory.create()

    @Test
    fun snapshot_roundTripsAllSupportedPreferenceTypes() {
        val settings = mutablePreferencesOf(
            stringPreferencesKey("string") to "value",
            booleanPreferencesKey("boolean") to true,
            intPreferencesKey("int") to 42,
            longPreferencesKey("long") to 123L,
            floatPreferencesKey("float") to 1.5f,
            doublePreferencesKey("double") to 2.5,
            stringSetPreferencesKey("set") to setOf("z", "a"),
        ).toPreferences()
        val store = createStore()

        store.write(
            PendingRestoreDataStoreWriter.DataStoreSnapshot(
                settings = settings,
                tabs = mutablePreferencesOf().toPreferences(),
                cookies = null,
            ),
        )

        val restored = store.read()
        assertNotNull(restored)
        assertEquals("value", restored!!.settings[stringPreferencesKey("string")])
        assertEquals(true, restored.settings[booleanPreferencesKey("boolean")])
        assertEquals(42, restored.settings[intPreferencesKey("int")])
        assertEquals(123L, restored.settings[longPreferencesKey("long")])
        assertEquals(1.5f, restored.settings[floatPreferencesKey("float")])
        assertEquals(2.5, restored.settings[doublePreferencesKey("double")])
        assertEquals(setOf("a", "z"), restored.settings[stringSetPreferencesKey("set")])
        assertNull(restored.cookies)
    }

    @Test
    fun snapshot_distinguishesEmptyCookiesFromCookiesExcluded() {
        val store = createStore()
        store.write(
            PendingRestoreDataStoreWriter.DataStoreSnapshot(
                settings = mutablePreferencesOf().toPreferences(),
                tabs = mutablePreferencesOf().toPreferences(),
                cookies = mutablePreferencesOf().toPreferences(),
            ),
        )

        val restored = store.read()

        assertNotNull(restored)
        assertNotNull(restored!!.cookies)
        assertTrue(restored.cookies!!.asMap().isEmpty())
    }

    @Test
    fun snapshotWriteInterrupted_readsPreviouslyCommittedSnapshot() {
        val store = createStore()
        val previous = PendingRestoreDataStoreWriter.DataStoreSnapshot(
            settings = mutablePreferencesOf(stringPreferencesKey("state") to "old").toPreferences(),
            tabs = mutablePreferencesOf().toPreferences(),
            cookies = null,
        )
        store.write(previous)

        val snapshotFile = File(
            tempFolder.root,
            "pending/${PendingRestoreManager.DATASTORE_ROLLBACK_SNAPSHOT_FILENAME}",
        )
        val output = AtomicFile(snapshotFile).startWrite()
        output.write("{partial".toByteArray())
        output.close()

        val restored = store.read()

        assertNotNull(restored)
        assertEquals("old", restored!!.settings[stringPreferencesKey("state")])
    }

    @Test
    fun snapshot_serializesKeysAndStringSetsDeterministically() {
        val firstDir = tempFolder.newFolder("first")
        val secondDir = tempFolder.newFolder("second")
        val firstStore = PendingRestoreDataStoreSnapshotStore(firstDir, moshi)
        val secondStore = PendingRestoreDataStoreSnapshotStore(secondDir, moshi)
        val firstPreferences = mutablePreferencesOf(
            stringPreferencesKey("b") to "two",
            stringSetPreferencesKey("set") to setOf("z", "a"),
            stringPreferencesKey("a") to "one",
        ).toPreferences()
        val secondPreferences = mutablePreferencesOf(
            stringPreferencesKey("a") to "one",
            stringSetPreferencesKey("set") to setOf("a", "z"),
            stringPreferencesKey("b") to "two",
        ).toPreferences()
        val firstSnapshot = PendingRestoreDataStoreWriter.DataStoreSnapshot(
            settings = firstPreferences,
            tabs = mutablePreferencesOf().toPreferences(),
            cookies = null,
        )
        val secondSnapshot = firstSnapshot.copy(settings = secondPreferences)

        firstStore.write(firstSnapshot)
        secondStore.write(secondSnapshot)

        assertEquals(
            File(firstDir, PendingRestoreManager.DATASTORE_ROLLBACK_SNAPSHOT_FILENAME).readText(),
            File(secondDir, PendingRestoreManager.DATASTORE_ROLLBACK_SNAPSHOT_FILENAME).readText(),
        )
    }

    @Test
    fun invalidSnapshot_isRejectedBeforePreferencesRestore() {
        val pendingDir = tempFolder.newFolder("pending")
        val snapshotFile = File(pendingDir, PendingRestoreManager.DATASTORE_ROLLBACK_SNAPSHOT_FILENAME)
        val invalid = PendingRestoreDataStoreSnapshot(
            formatVersion = PendingRestoreDataStoreSnapshot.CURRENT_FORMAT_VERSION,
            settings = listOf(
                PendingRestorePreferenceEntry(
                    key = "duplicate",
                    type = PendingRestorePreferenceType.STRING,
                    stringValue = "one",
                ),
                PendingRestorePreferenceEntry(
                    key = "duplicate",
                    type = PendingRestorePreferenceType.STRING,
                    stringValue = "two",
                ),
            ),
            tabs = emptyList(),
            cookies = null,
        )
        snapshotFile.writeText(moshi.adapter<PendingRestoreDataStoreSnapshot>().toJson(invalid))

        try {
            PendingRestoreDataStoreSnapshotStore(pendingDir, moshi).read()
            assertTrue("duplicate snapshot key must be rejected", false)
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("duplicate"))
        }
    }

    @Test
    fun snapshotWrite_failsWhenPendingPathIsAFile() {
        val pendingPath = tempFolder.newFile("pending-file")
        val store = PendingRestoreDataStoreSnapshotStore(pendingPath, moshi)
        val snapshot = PendingRestoreDataStoreWriter.DataStoreSnapshot(
            settings = mutablePreferencesOf().toPreferences(),
            tabs = mutablePreferencesOf().toPreferences(),
            cookies = null,
        )

        try {
            store.write(snapshot)
            assertTrue("a file cannot be used as pending directory", false)
        } catch (error: IllegalStateException) {
            assertTrue(error.message!!.contains("not a directory"))
        }
    }

    @Test
    fun cleanupPending_removesSnapshotAndAtomicArtifacts() {
        val store = createStore()
        store.write(
            PendingRestoreDataStoreWriter.DataStoreSnapshot(
                settings = mutablePreferencesOf().toPreferences(),
                tabs = mutablePreferencesOf().toPreferences(),
                cookies = null,
            ),
        )
        val snapshotFile = File(
            tempFolder.root,
            "pending/${PendingRestoreManager.DATASTORE_ROLLBACK_SNAPSHOT_FILENAME}",
        )
        val output = AtomicFile(snapshotFile).startWrite()
        output.write("{partial".toByteArray())
        output.close()

        val pendingDir = snapshotFile.parentFile!!
        pendingDir.deleteRecursively()

        assertFalse(snapshotFile.exists())
        assertFalse(File("${snapshotFile.path}.new").exists())
        assertFalse(File("${snapshotFile.path}.bak").exists())
    }

    private fun createStore(): PendingRestoreDataStoreSnapshotStore {
        return PendingRestoreDataStoreSnapshotStore(
            pendingDir = tempFolder.newFolder("pending"),
            moshi = moshi,
        )
    }
}
