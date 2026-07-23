package com.websarva.wings.android.slevo.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.BuildConfig
import com.websarva.wings.android.slevo.MainActivity
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreApplier
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreManager
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreMarker
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreResultFile
import com.websarva.wings.android.slevo.data.backup.pending.RealPendingRestoreFileStore
import com.websarva.wings.android.slevo.data.backup.pending.RestoreStatus
import com.websarva.wings.android.slevo.data.backup.restore.RealBackupDatabaseValidator
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 起動時の migration finalization から既存 success Snackbar までを検証する acceptance test。 */
@OptIn(ExperimentalStdlibApi::class)
@RunWith(AndroidJUnit4::class)
class PendingRestoreStartupAcceptanceTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    /** startup recovery が strict validation、finalization、既存 Snackbar を一続きで実行する。 */
    @Test
    fun startupRecovery_finalizesValidatedRestoreAndShowsExistingSuccessSnackbar() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val databaseName = databaseName()
        context.deleteDatabase(databaseName)
        val store = RealPendingRestoreFileStore(context, Moshi.Builder().build())
        store.cleanupResult()
        store.cleanupPending()
        createCurrentDatabase(context, databaseName)

        val liveDbFile = context.getDatabasePath(databaseName)
        assertNull(RealBackupDatabaseValidator().validate(liveDbFile))
        store.writeMarker(
            PendingRestoreMarker(
                status = RestoreStatus.MIGRATION_PENDING,
                createdAt = "2026-07-17T00:00:00Z",
                includeCookies = false,
                databaseVersion = AppDatabase.CURRENT_DATABASE_VERSION,
                hadExistingLiveDb = true,
            ),
        )

        // Application startup recovery is synchronous; run the same applier entrypoint before Activity startup.
        runBlocking { PendingRestoreApplier(context).runIfNeeded() }

        val resultFile = File(
            File(context.filesDir, PendingRestoreManager.RESULT_DIR_NAME),
            PendingRestoreManager.RESULT_FILENAME,
        )
        val result = Moshi.Builder().build().adapter<PendingRestoreResultFile>()
            .fromJson(resultFile.readText())
        assertNotNull(result)
        assertTrue(result!!.success)
        assertTrue(result.migrationCompleted)
        assertFalse(File(store.pendingDir, PendingRestoreManager.MARKER_FILENAME).exists())

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            // MainActivity's existing ViewModel/consumer observes the durable result and feeds the existing Snackbar.
            composeRule.onNodeWithText("バックアップを復元しました").assertIsDisplayed()
            composeRule.mainClock.advanceTimeBy(5_000)
            composeRule.waitForIdle()
            assertFalse(resultFile.exists())
        } finally {
            scenario.close()
        }
    }

    /** Room が生成する current-version file を作り、実 production validator の strict path を使えるようにする。 */
    private fun createCurrentDatabase(
        context: android.content.Context,
        databaseName: String,
    ) {
        Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(*AppDatabase.ALL_REGISTERED_MIGRATIONS.toTypedArray())
            .build()
            .also { database ->
                database.openHelper.writableDatabase
                database.close()
            }
    }

    /** Production の debug/非debug database name と acceptance fixture を一致させる。 */
    private fun databaseName(): String = if (BuildConfig.DEBUG) {
        "slevo_dev_database"
    } else {
        "slevo_database"
    }
}
