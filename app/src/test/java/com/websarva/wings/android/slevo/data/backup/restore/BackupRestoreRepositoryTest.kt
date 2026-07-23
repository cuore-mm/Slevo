package com.websarva.wings.android.slevo.data.backup.restore

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.squareup.moshi.Moshi
import com.websarva.wings.android.slevo.data.backup.BackupRepositoryImpl
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupGestureSettings
import com.websarva.wings.android.slevo.data.backup.model.BackupManifest
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import com.websarva.wings.android.slevo.data.backup.model.IncludedContents
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreManager
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreMarker
import com.websarva.wings.android.slevo.data.backup.pending.RestoreStatus
import com.websarva.wings.android.slevo.data.datasource.local.CookieLocalDataSource
import com.websarva.wings.android.slevo.data.datasource.local.SettingsLocalDataSource
import com.websarva.wings.android.slevo.data.datasource.local.TabsLocalDataSource
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureDirection
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.ThemeMode
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * [com.websarva.wings.android.slevo.data.backup.BackupRepositoryImpl] の preview / restore orchestration を検証する。
 */
@OptIn(ExperimentalStdlibApi::class)
class BackupRestoreRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val moshi = Moshi.Builder().build()

    private lateinit var context: Context
    private lateinit var pendingRestoreManager: PendingRestoreManager
    private lateinit var repository: BackupRepositoryImpl

    @Before
    fun setUp() {
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        context = mockk(relaxed = true)
        every { context.contentResolver } returns contentResolver
        every { context.cacheDir } returns tempFolder.newFolder("cache")
        every { context.filesDir } returns tempFolder.newFolder("files")
        every { context.applicationContext } returns context

        val dbValidator = FakeBackupDatabaseValidator()
        pendingRestoreManager = PendingRestoreManager(context, moshi, dbValidator)

        repository = BackupRepositoryImpl(
            context = context,
            settingsDataSource = FakeSettingsDataSource(),
            tabsDataSource = FakeTabsDataSource(),
            cookieDataSource = FakeCookieDataSource(),
            dbExporter = mockk(relaxed = true),
            outputWriter = mockk(relaxed = true),
            backupReader = BackupReader(moshi, dbValidator, currentDbVersion = 9),
            pendingRestoreManager = pendingRestoreManager,
        )
    }

    @Test
    fun previewBackup_returnsSuccessForValidZip() = runTest {
        val uri = mockk<Uri>()
        every { context.contentResolver.openInputStream(uri) } returns createValidZipInputStream()

        val result = repository.previewBackup(uri)

        Assert.assertTrue(result is BackupRestoreResult.Success)
    }

    @Test
    fun previewBackup_returnsInvalidForMissingManifest() = runTest {
        val uri = mockk<Uri>()
        every { context.contentResolver.openInputStream(uri) } returns createZipWithoutManifest()

        val result = repository.previewBackup(uri)

        Assert.assertTrue(result is BackupRestoreResult.Invalid)
    }

    @Test
    fun previewBackup_returnsFailureWhenInputOpenFails() = runTest {
        val uri = mockk<Uri>()
        every { context.contentResolver.openInputStream(uri) } returns null

        val result = repository.previewBackup(uri)

        Assert.assertTrue(result is BackupRestoreResult.Failure)
    }

    @Test
    fun restoreBackup_createsPendingRestoreForValidZip() = runTest {
        val uri = mockk<Uri>()
        every { context.contentResolver.openInputStream(uri) } returns createValidZipInputStream()

        val result = repository.restoreBackup(uri, includeCookies = true)

        Assert.assertTrue(result is BackupRestoreResult.Success)
        Assert.assertTrue(pendingRestoreManager.readMarker() != null)
    }

    @Test
    fun restoreBackup_excludesCookiesWhenSkipRequested() = runTest {
        val uri = mockk<Uri>()
        every { context.contentResolver.openInputStream(uri) } returns createValidZipWithCookies()

        val result = repository.restoreBackup(uri, includeCookies = false)

        Assert.assertTrue(result is BackupRestoreResult.Success)
        val marker = requireNotNull(pendingRestoreManager.readMarker())
        Assert.assertEquals(false, marker.includeCookies)
    }

    @Test
    fun restoreBackup_returnsFailureWhenManagerFails() = runTest {
        // 既存 prepared marker を作成
        val pendingDir = File(context.filesDir, PendingRestoreManager.Companion.PENDING_DIR_NAME)
        File(pendingDir, PendingRestoreManager.Companion.MARKER_FILENAME).apply {
            parentFile?.mkdirs()
            writeText(
                moshi.adapter(PendingRestoreMarker::class.java).toJson(
                    PendingRestoreMarker(
                        status = RestoreStatus.PREPARED,
                        createdAt = "2026-07-03T00:00:00Z",
                        includeCookies = false,
                        databaseVersion = 9,
                    ),
                )
            )
        }

        val uri = mockk<Uri>()
        every { context.contentResolver.openInputStream(uri) } returns createValidZipInputStream()

        val result = repository.restoreBackup(uri, includeCookies = true)

        Assert.assertTrue(result is BackupRestoreResult.Failure)
    }

    @Test
    fun restoreBackup_returnsInvalidForInvalidZip() = runTest {
        val uri = mockk<Uri>()
        every { context.contentResolver.openInputStream(uri) } returns "not-a-zip".byteInputStream()

        val result = repository.restoreBackup(uri, includeCookies = true)

        Assert.assertTrue(result is BackupRestoreResult.Invalid)
    }

    // --- ZIP builders ---

    private fun createValidZipInputStream() = buildZip {
        writeManifestEntry(cookies = false)
        writeDbEntry()
        writeSettingsEntry()
        writeTabsEntry()
    }

    private fun createValidZipWithCookies() = buildZip {
        writeManifestEntry(cookies = true)
        writeDbEntry()
        writeSettingsEntry()
        writeTabsEntry()
        writeCookiesEntry()
    }

    private fun createZipWithoutManifest() = buildZip {
        writeDbEntry()
        writeSettingsEntry()
        writeTabsEntry()
    }

    private fun buildZip(block: ZipOutputStream.() -> Unit): InputStream {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { it.block() }
        return baos.toByteArray().inputStream()
    }

    private fun ZipOutputStream.writeManifestEntry(cookies: Boolean) {
        val manifest = BackupManifest(
            createdAt = "2026-07-03T00:00:00Z",
            appVersionCode = 1,
            appVersionName = "1.0.0",
            databaseVersion = 9,
            backupFormatVersion = 1,
            backupMode = "full",
            included = IncludedContents(
                database = true,
                settings = true,
                tabs = true,
                cookies = cookies
            ),
        )
        putNextEntry(ZipEntry("manifest.json"))
        write(moshi.adapter(BackupManifest::class.java).toJson(manifest).toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.writeDbEntry() {
        putNextEntry(ZipEntry("database/slevo.db"))
        write(createMinimalSqliteBytes())
        closeEntry()
    }

    private fun ZipOutputStream.writeSettingsEntry() {
        val settings = BackupSettingsJson(
            themeMode = "system", isTreeSort = false, isThreadMinimapScrollbarEnabled = true,
            textScale = 1.0f, isIndividualTextScale = false, headerTextScale = 1.0f,
            bodyTextScale = 1.0f, lineHeight = 1.5f, isRedirect5chNetToIoEnabled = false,
            gestureSettings = BackupGestureSettings(
                enabled = false,
                showActionHints = true,
                actions = emptyMap()
            ),
        )
        putNextEntry(ZipEntry("datastore/settings.json"))
        write(moshi.adapter(BackupSettingsJson::class.java).toJson(settings).toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.writeTabsEntry() {
        val tabs = BackupTabsJson(lastSelectedTabsPage = 0)
        putNextEntry(ZipEntry("datastore/tabs.json"))
        write(moshi.adapter(BackupTabsJson::class.java).toJson(tabs).toByteArray())
        closeEntry()
    }

    private fun ZipOutputStream.writeCookiesEntry() {
        val cookies = BackupCookiesJson(cookies = emptyList())
        putNextEntry(ZipEntry("datastore/cookies.json"))
        write(moshi.adapter(BackupCookiesJson::class.java).toJson(cookies).toByteArray())
        closeEntry()
    }

    /** テスト用ダミー DB バイト列。fake validator が常に成功するため実際の SQLite は不要。 */
    private fun createMinimalSqliteBytes(): ByteArray = byteArrayOf(0x00, 0x01, 0x02)

    // --- Fakes ---

    private class FakeBackupDatabaseValidator : BackupDatabaseValidator {
        override fun validate(dbFile: File): String? = null
        override fun preValidate(dbFile: File, manifestDatabaseVersion: Int): String? = null
        override fun getUserVersion(dbFile: File): Int? = null
    }

    private class FakeSettingsDataSource : SettingsLocalDataSource {
        override fun observeThemeMode(): Flow<ThemeMode> = flowOf(ThemeMode.SYSTEM)
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override fun observeIsTreeSort(): Flow<Boolean> = flowOf(false)
        override suspend fun setTreeSort(enabled: Boolean) {}
        override fun observeIsThreadMinimapScrollbarEnabled(): Flow<Boolean> = flowOf(true)
        override suspend fun setThreadMinimapScrollbarEnabled(enabled: Boolean) {}
        override fun observeTextScale(): Flow<Float> = flowOf(1.0f)
        override suspend fun setTextScale(scale: Float) {}
        override fun observeIsIndividualTextScale(): Flow<Boolean> = flowOf(false)
        override suspend fun setIndividualTextScale(enabled: Boolean) {}
        override fun observeHeaderTextScale(): Flow<Float> = flowOf(1.0f)
        override suspend fun setHeaderTextScale(scale: Float) {}
        override fun observeBodyTextScale(): Flow<Float> = flowOf(1.0f)
        override suspend fun setBodyTextScale(scale: Float) {}
        override fun observeLineHeight(): Flow<Float> = flowOf(1.5f)
        override suspend fun setLineHeight(height: Float) {}
        override fun observeIsRedirect5chNetToIoEnabled(): Flow<Boolean> = flowOf(false)
        override suspend fun getIsRedirect5chNetToIoEnabled(): Boolean = false
        override suspend fun setRedirect5chNetToIoEnabled(enabled: Boolean) {}
        override fun observeGestureSettings(): Flow<GestureSettings> =
            flowOf(GestureSettings.Companion.DEFAULT)
        override suspend fun setGestureEnabled(enabled: Boolean) {}
        override suspend fun setGestureShowActionHints(show: Boolean) {}
        override suspend fun setGestureAction(direction: GestureDirection, action: GestureAction?) {}
        override suspend fun resetGestureSettings() {}
    }

    private class FakeTabsDataSource : TabsLocalDataSource {
        override fun observeLastSelectedTabsPage(): Flow<Int> = flowOf(0)
        override suspend fun setLastSelectedTabsPage(page: Int) {}
    }

    private class FakeCookieDataSource : CookieLocalDataSource {
        override fun getCookies(): Flow<List<Cookie>> = flowOf(emptyList())
        override suspend fun saveCookies(cookies: List<Cookie>) {}
    }
}
