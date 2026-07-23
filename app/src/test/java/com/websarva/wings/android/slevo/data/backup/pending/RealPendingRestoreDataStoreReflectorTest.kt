package com.websarva.wings.android.slevo.data.backup.pending

import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.data.backup.BackupMoshiFactory
import com.websarva.wings.android.slevo.data.backup.model.BackupCookieItem
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupGestureSettings
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import com.websarva.wings.android.slevo.data.datasource.local.impl.SlevoPreferenceDataStores
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [RealPendingRestoreDataStoreReflector] の cookie payload invariant を検証する。 */
@OptIn(ExperimentalStdlibApi::class)
@RunWith(RobolectricTestRunner::class)
class RealPendingRestoreDataStoreReflectorTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val moshi = BackupMoshiFactory.create()
    private val reflector = RealPendingRestoreDataStoreReflector(context, moshi)

    /** DataStore provider の singleton を後続テストへ持ち越さない。 */
    @After
    fun tearDown() {
        SlevoPreferenceDataStores.resetForTest()
    }

    /** Cookie payload を含めない場合、payload を読まず snapshot にも cookies を含めない。 */
    @Test
    fun prepareRollbackSnapshot_withoutCookies_doesNotReadCookiePayload() = runTest {
        val pendingDir = createPendingDirectory()
        writeRequiredPayloads(pendingDir)
        File(pendingDir, "datastore/cookies.json").writeText("not-json")

        val error = reflector.prepareRollbackSnapshot(pendingDir, includeCookies = false)

        assertNull(error)
        val snapshot = PendingRestoreDataStoreSnapshotStore(pendingDir, moshi).read()
        assertNotNull(snapshot)
        assertNull(snapshot!!.cookies)
    }

    /** Cookie payload を含める場合、staged file がないと snapshot preparation に失敗する。 */
    @Test
    fun prepareRollbackSnapshot_withCookies_requiresStagedCookieFile() = runTest {
        val pendingDir = createPendingDirectory()
        writeRequiredPayloads(pendingDir)

        val error = reflector.prepareRollbackSnapshot(pendingDir, includeCookies = true)

        assertNotNull(error)
        assertTrue(error!!.contains("staged cookies file is missing or not a regular file"))
    }

    /** Cookie payload が directory の場合も、regular file invariant 違反として失敗する。 */
    @Test
    fun prepareRollbackSnapshot_withCookies_rejectsNonRegularCookiePayload() = runTest {
        val pendingDir = createPendingDirectory()
        writeRequiredPayloads(pendingDir)
        File(pendingDir, "datastore/cookies.json").mkdirs()

        val error = reflector.prepareRollbackSnapshot(pendingDir, includeCookies = true)

        assertNotNull(error)
        assertTrue(error!!.contains("staged cookies file is missing or not a regular file"))
    }

    /** Cookie JSON の parse failure は reflector の failure result として維持する。 */
    @Test
    fun reflect_withCookies_preservesCookieParseFailure() = runTest {
        val pendingDir = createPendingDirectory()
        writeRequiredPayloads(pendingDir)
        File(pendingDir, "datastore/cookies.json").writeText("null")

        val error = reflector.reflect(pendingDir, includeCookies = true)

        assertNotNull(error)
        assertTrue(error!!.contains("failed to parse cookies JSON"))
    }

    /** Cookie preparation failure は DataStore write 前の failure result として維持する。 */
    @Test
    fun reflect_withCookies_preservesCookiePreparationFailure() = runTest {
        val pendingDir = createPendingDirectory()
        writeRequiredPayloads(pendingDir)
        val invalidCookie = BackupCookieItem(
            name = "name",
            value = "value",
            domain = "example.com",
            path = "no-slash",
            expiresAt = 0L,
            secure = false,
            httpOnly = false,
            hostOnly = false,
            persistent = false,
        )
        File(pendingDir, "datastore/cookies.json").writeText(
            moshi.adapter<BackupCookiesJson>().toJson(BackupCookiesJson(listOf(invalidCookie))),
        )

        val error = reflector.reflect(pendingDir, includeCookies = true)

        assertNotNull(error)
        assertTrue(error!!.contains("failed to serialize restored cookies"))
    }

    /** settings / tabs の最小有効 payload を staging directory に作成する。 */
    private fun writeRequiredPayloads(pendingDir: File) {
        val datastoreDir = File(pendingDir, "datastore").apply { mkdirs() }
        datastoreDir.resolve("settings.json").writeText(
            moshi.adapter<BackupSettingsJson>().toJson(
                BackupSettingsJson(
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
            ),
        )
        datastoreDir.resolve("tabs.json").writeText(
            moshi.adapter<BackupTabsJson>().toJson(BackupTabsJson(lastSelectedTabsPage = 0)),
        )
    }

    /** 各テスト専用の pending directory を作成する。 */
    private fun createPendingDirectory(): File = tempFolder.newFolder("pending")
}
