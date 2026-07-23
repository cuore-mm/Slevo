package com.websarva.wings.android.slevo.data.backup.pending

import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import com.websarva.wings.android.slevo.data.backup.model.BackupGestureSettings
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import com.websarva.wings.android.slevo.data.backup.restore.BackupPreview
import com.websarva.wings.android.slevo.data.backup.restore.FakeBackupDatabaseValidator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * [PendingRestoreManager.prepareRestore()] の failure path cleanup を検証する Robolectric test。
 *
 * - integrity check failure 後に pending directory が cleanup されること
 * - marker write failure 後に pending directory が cleanup されること
 */
@RunWith(RobolectricTestRunner::class)
class PendingRestoreManagerPrepareTest {

    private val moshi = Moshi.Builder().build()
    private lateinit var filesDir: File

    @Before
    fun setUp() {
        filesDir = ApplicationProvider.getApplicationContext<android.content.Context>().filesDir
        // テスト前に pending-restore が残っていれば削除
        val pendingDir = File(filesDir, "pending-restore")
        if (pendingDir.exists()) pendingDir.deleteRecursively()
    }

    // --- Helpers ---

    /**
     * 有効な JSON field を持つ [BackupPreview] fixture を生成する。
     * DB payload は [dbFile] として渡す。
     */
    private fun createBackupPreview(dbFile: File): BackupPreview {
        return BackupPreview(
            createdAt = "2026-01-01T00:00:00Z",
            appVersionCode = 1,
            appVersionName = "1.0.0",
            databaseVersion = 9,
            containsCookies = false,
            dbFile = dbFile,
            settingsJson = BackupSettingsJson(
                themeMode = "system",
                isTreeSort = false,
                isThreadMinimapScrollbarEnabled = true,
                textScale = 1.0f,
                isIndividualTextScale = false,
                headerTextScale = 1.0f,
                bodyTextScale = 1.0f,
                lineHeight = 1.5f,
                isRedirect5chNetToIoEnabled = false,
                gestureSettings = BackupGestureSettings(
                    enabled = false,
                    showActionHints = true,
                    actions = emptyMap(),
                ),
            ),
            tabsJson = BackupTabsJson(lastSelectedTabsPage = 0),
            cookiesJson = null,
        )
    }

    // --- 6.8 integrity check failure ---

    /**
     * DB staging 成功後、`checkIntegrity()` が failure を返すと
     * `cleanupPendingDir()` が呼ばれ、pending directory が削除される。
     */
    @Test
    fun prepareRestore_integrityFailure_cleansPendingDir() = runTest {
        val pendingDir = File(filesDir, "pending-restore")
        val sourceDbFile = File(filesDir, "fixture-db.tmp").apply {
            writeText("fake db content")
        }
        try {
            val preview = createBackupPreview(dbFile = sourceDbFile)
            val failingValidator = FakeBackupDatabaseValidator(
                preValidationError = "integrity check failed",
            )
            val manager = PendingRestoreManager(
                context = ApplicationProvider.getApplicationContext(),
                moshi = moshi,
                dbValidator = failingValidator,
            )

            val error = manager.prepareRestore(preview)

            assertNotNull("should return error", error)
            assertTrue(error!!.contains("integrity check failed"))
            assertFalse(
                "pending directory should be cleaned up after integrity failure",
                pendingDir.exists(),
            )
        } finally {
            sourceDbFile.delete()
            if (pendingDir.exists()) pendingDir.deleteRecursively()
        }
    }

    // --- 6.9 marker write failure ---

    /**
     * DB staging と DataStore JSON staging 成功後、
     * marker write が失敗すると pending directory が cleanup される。
     *
     * [PendingRestoreManager.shouldFailMarkerWrite] をテスト hook として使う。
     */
    @Test
    fun prepareRestore_markerWriteFailure_cleansPendingDir() = runTest {
        val pendingDir = File(filesDir, "pending-restore")
        val sourceDbFile = File(filesDir, "fixture-db.tmp").apply {
            writeText("fake db content")
        }
        try {
            val preview = createBackupPreview(dbFile = sourceDbFile)
            val passingValidator = FakeBackupDatabaseValidator()

            val manager = PendingRestoreManager(
                context = ApplicationProvider.getApplicationContext(),
                moshi = moshi,
                dbValidator = passingValidator,
            ).apply {
                shouldFailMarkerWrite = true
            }

            // pending-restore が残っていれば削除しておく
            if (pendingDir.exists()) pendingDir.deleteRecursively()

            val error = manager.prepareRestore(preview)

            assertNotNull("should return error", error)
            assertTrue(error!!.contains("failed to write marker"))
            assertFalse(
                "pending directory should be cleaned up after marker write failure",
                pendingDir.exists(),
            )
        } finally {
            sourceDbFile.delete()
            if (pendingDir.exists()) pendingDir.deleteRecursively()
        }
    }

    @Test
    fun prepareRestore_successWritesReadablePreparedMarker() = runTest {
        val pendingDir = File(filesDir, "pending-restore")
        val sourceDbFile = File(filesDir, "fixture-db.tmp").apply {
            writeText("fake db content")
        }
        try {
            val manager = PendingRestoreManager(
                context = ApplicationProvider.getApplicationContext(),
                moshi = moshi,
                dbValidator = FakeBackupDatabaseValidator(),
            )

            val error = manager.prepareRestore(createBackupPreview(sourceDbFile))

            assertTrue(error == null)
            val marker = manager.readMarker()
            assertNotNull(marker)
            assertTrue(marker!!.status == RestoreStatus.PREPARED)
            assertTrue(marker.databaseVersion == 9)
            assertTrue(pendingDir.exists())
        } finally {
            sourceDbFile.delete()
            if (pendingDir.exists()) pendingDir.deleteRecursively()
        }
    }

    @Test
    fun handleExistingPending_failedMarkerPreservesQuarantineIncident() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = RealPendingRestoreFileStore(context, moshi)
        val incident = store.createQuarantineIncidentDir()
        val quarantinedDb = File(incident, "slevo.db").apply { writeText("quarantined") }
        store.writeMarker(
            PendingRestoreMarker(
                status = RestoreStatus.FAILED,
                createdAt = "2026-07-03T00:00:00Z",
                includeCookies = false,
                databaseVersion = 9,
            ),
        )

        try {
            val manager = PendingRestoreManager(
                context = context,
                moshi = moshi,
                dbValidator = FakeBackupDatabaseValidator(),
            )

            assertTrue(manager.handleExistingPending() == null)
            assertFalse(store.pendingDir.exists())
            assertTrue(quarantinedDb.exists())
            assertTrue(quarantinedDb.readText() == "quarantined")
        } finally {
            store.cleanupPending()
            store.quarantineRootDir.deleteRecursively()
        }
    }

    /** active COMPLETED marker は result がある間も block し、marker-last cleanup 後だけ解除する。 */
    @Test
    fun handleExistingPending_completedMarkerBlocksUntilCleanupSucceeds() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = RealPendingRestoreFileStore(context, moshi)
        store.writeMarker(
            PendingRestoreMarker(
                status = RestoreStatus.COMPLETED,
                createdAt = "2026-07-15T00:00:00Z",
                includeCookies = false,
                databaseVersion = 9,
            ),
        )
        store.writeResult(
            success = true,
            message = "restore completed successfully",
            timestamp = "2026-07-15T00:00:00Z",
            migrationCompleted = true,
        )

        try {
            val manager = PendingRestoreManager(
                context = context,
                moshi = moshi,
                dbValidator = FakeBackupDatabaseValidator(),
            )

            assertNotNull(manager.handleExistingPending())
            assertTrue(store.cleanupPending())
            assertTrue(manager.handleExistingPending() == null)
        } finally {
            store.cleanupPending()
        }
    }
}
