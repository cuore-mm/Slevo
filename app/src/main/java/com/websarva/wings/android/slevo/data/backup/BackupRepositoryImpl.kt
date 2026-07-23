package com.websarva.wings.android.slevo.data.backup

import android.content.Context
import android.net.Uri
import com.websarva.wings.android.slevo.BuildConfig
import com.websarva.wings.android.slevo.data.backup.export.BackupDataMapper
import com.websarva.wings.android.slevo.data.backup.export.BackupExportResult
import com.websarva.wings.android.slevo.data.backup.export.BackupOutputException
import com.websarva.wings.android.slevo.data.backup.export.BackupOutputWriter
import com.websarva.wings.android.slevo.data.backup.export.BackupZipWriter
import com.websarva.wings.android.slevo.data.backup.export.DatabaseBackupExporter
import com.websarva.wings.android.slevo.data.backup.model.BackupManifest
import com.websarva.wings.android.slevo.data.backup.model.IncludedContents
import com.websarva.wings.android.slevo.data.backup.pending.PendingRestoreManager
import com.websarva.wings.android.slevo.data.backup.restore.BackupReader
import com.websarva.wings.android.slevo.data.backup.restore.BackupReaderResult
import com.websarva.wings.android.slevo.data.backup.restore.BackupRestoreResult
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.local.CookieLocalDataSource
import com.websarva.wings.android.slevo.data.datasource.local.SettingsLocalDataSource
import com.websarva.wings.android.slevo.data.datasource.local.TabsLocalDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BackupRepository] の実装。
 *
 * バックアップの全ステップ（manifest作成、DBエクスポート、DataStore読取、ZIP書込）を
 * 順序通りに orchestrate し、success/failure を [com.websarva.wings.android.slevo.data.backup.export.BackupExportResult] で返す。
 * [backupMutex] で同時実行を 1 件ずつ直列化する。
 * 全ブロッキング I/O（ZIP読取、SQLite操作、ファイルコピー）は [kotlinx.coroutines.Dispatchers.IO] 上で実行し、
 * 呼び出し元 dispatcher（例: Main）をブロックしない。
 */
@Singleton
class BackupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataSource: SettingsLocalDataSource,
    private val tabsDataSource: TabsLocalDataSource,
    private val cookieDataSource: CookieLocalDataSource,
    private val dbExporter: DatabaseBackupExporter,
    private val outputWriter: BackupOutputWriter,
    private val backupReader: BackupReader,
    private val pendingRestoreManager: PendingRestoreManager,
    private val resourceLimits: BackupResourceLimits = BackupResourceLimits(),
) : BackupRepository {

    private val backupMutex = Mutex()

    /**
     * SAF の保存先 [uri] へバックアップ ZIP を出力する。
     * [backupMutex] で同時実行を直列化し、内部で manifest 作成・DB export・
     * DataStore 読取・ZIP 書込を順次実行する。
     */
    override suspend fun exportBackup(uri: Uri, includeCookies: Boolean): BackupExportResult {
        return backupMutex.withLock {
            withContext(Dispatchers.IO) {
                exportInternal(uri, includeCookies)
            }
        }
    }

    /**
     * SAF の [uri] からバックアップ ZIP を読み取り、検証して preview を返す。
     * DB/DataStore へ書き込まない。
     */
    override suspend fun previewBackup(uri: Uri): BackupRestoreResult {
        return backupMutex.withLock {
            withContext(Dispatchers.IO) {
                val input = openUri(uri) ?: return@withContext BackupRestoreResult.Failure("failed to open input")
                when (val result = backupReader.readBackup(input)) {
                    is BackupReaderResult.Success -> {
                        // --- preview 表示後は DB temp file 不要のため削除 ---
                        result.preview.dbFile.delete()
                        BackupRestoreResult.Success(result.preview.containsCookies)
                    }
                    is BackupReaderResult.Error -> result.result
                }
            }
        }
    }

    /**
     * SAF の [uri] からバックアップ ZIP を再読み込み・再検証し、pending restore を作成する。
     * live DB と DataStore はこの時点で変更しない。
     */
    override suspend fun restoreBackup(uri: Uri, includeCookies: Boolean): BackupRestoreResult {
        return backupMutex.withLock {
            withContext(Dispatchers.IO) {
                // --- commit 時に ZIP を再読み込み・再検証 ---
                val input = openUri(uri) ?: return@withContext BackupRestoreResult.Failure("failed to open input")
                val readerResult = backupReader.readBackup(input)
                val preview = when (readerResult) {
                    is BackupReaderResult.Success -> readerResult.preview
                    is BackupReaderResult.Error -> return@withContext readerResult.result
                }

                try {
                    // --- Cookie skip ---
                    val effectivePreview = if (!includeCookies) {
                        preview.copy(cookiesJson = null, containsCookies = false)
                    } else {
                        preview
                    }

                    // --- pending restore 作成 ---
                    val error = pendingRestoreManager.prepareRestore(effectivePreview)
                    if (error != null) {
                        BackupRestoreResult.Failure(error)
                    } else {
                        BackupRestoreResult.Success(containsCookies = false)
                    }
                } finally {
                    // --- restore 後は DB temp file 不要のため best-effort 削除 ---
                    // prepareRestore が move 済みの場合は no-op
                    preview.dbFile.delete()
                }
            }
        }
    }

    // --- orchestration ---

    private suspend fun exportInternal(uri: Uri, includeCookies: Boolean): BackupExportResult {
        val sessionDir = createSessionDir()
        var dbFile: File? = null
        try {
            // --- manifest ---
            val manifest = buildManifest(includeCookies)

            // --- DB export ---
            dbFile = try {
                dbExporter.exportDatabase(File(sessionDir, "database"))
            } catch (e: Exception) {
                return BackupExportResult.Failure("DB export failed: ${e.message}")
            }

            // --- DataStore reads ---
            val settings = try {
                BackupDataMapper.toBackupSettingsJson(
                    themeMode = settingsDataSource.observeThemeMode().first(),
                    isTreeSort = settingsDataSource.observeIsTreeSort().first(),
                    isThreadMinimapScrollbarEnabled = settingsDataSource.observeIsThreadMinimapScrollbarEnabled().first(),
                    textScale = settingsDataSource.observeTextScale().first(),
                    isIndividualTextScale = settingsDataSource.observeIsIndividualTextScale().first(),
                    headerTextScale = settingsDataSource.observeHeaderTextScale().first(),
                    bodyTextScale = settingsDataSource.observeBodyTextScale().first(),
                    lineHeight = settingsDataSource.observeLineHeight().first(),
                    isRedirect5chNetToIoEnabled = settingsDataSource.getIsRedirect5chNetToIoEnabled(),
                    gestureSettings = settingsDataSource.observeGestureSettings().first(),
                )
            } catch (e: Exception) {
                return BackupExportResult.Failure("Settings JSON failed: ${e.message}")
            }

            val tabs = try {
                BackupDataMapper.toBackupTabsJson(
                    lastSelectedTabsPage = tabsDataSource.observeLastSelectedTabsPage().first(),
                )
            } catch (e: Exception) {
                return BackupExportResult.Failure("Tabs JSON failed: ${e.message}")
            }

            val cookies = if (includeCookies) {
                try {
                    BackupDataMapper.toBackupCookiesJson(
                        cookieDataSource.getCookies().first(),
                    )
                } catch (e: Exception) {
                    return BackupExportResult.Failure("Cookies JSON failed: ${e.message}")
                }
            } else null

            // --- ZIP write ---
            return writeZip(uri, manifest, dbFile, settings, tabs, cookies)
        } finally {
            cleanupSession(sessionDir)
        }
    }

    private suspend fun writeZip(
        uri: Uri,
        manifest: BackupManifest,
        dbFile: File,
        settings: Any,
        tabs: Any,
        cookies: Any?,
    ): BackupExportResult {
        try {
            outputWriter.writeToUri(uri) { outputStream ->
                val writer = BackupZipWriter(outputStream, resourceLimits = resourceLimits)
                try {
                    writer.writeJsonEntry("manifest.json", manifest)
                    writer.writeFileEntry("database/slevo.db", dbFile)
                    writer.writeJsonEntry("datastore/settings.json", settings)
                    writer.writeJsonEntry("datastore/tabs.json", tabs)
                    if (cookies != null) {
                        writer.writeJsonEntry("datastore/cookies.json", cookies)
                    }
                } finally {
                    // BackupZipWriter が close を制御するため、ここでは close しない。
                    // BackupOutputWriter が block 完了後に outputStream を close する。
                }
                writer.close()
                if (!writer.isSuccessful()) {
                    throw BackupOutputException("ZIP write failed: ${writer.failureReason()}")
                }
            }
            return BackupExportResult.Success
        } catch (e: BackupOutputException) {
            return BackupExportResult.Failure("ZIP write failed: ${e.message}")
        } catch (e: Exception) {
            return BackupExportResult.Failure("Output write failed: ${e.message}")
        }
    }

    // --- helpers ---

    private fun openUri(uri: Uri): java.io.InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (_: Exception) {
            null
        }
    }

    private fun createSessionDir(): File {
        val dir = File(context.cacheDir, "backups/${System.currentTimeMillis()}")
        dir.mkdirs()
        return dir
    }

    private fun cleanupSession(dir: File) {
        try {
            dir.deleteRecursively()
        } catch (_: Exception) {
            // best-effort cleanup
        }
    }

    private fun buildManifest(includeCookies: Boolean): BackupManifest {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        return BackupManifest(
            createdAt = df.format(Date()),
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            appVersionName = BuildConfig.VERSION_NAME,
            databaseVersion = AppDatabase.CURRENT_DATABASE_VERSION,
            included = IncludedContents(cookies = includeCookies),
        )
    }
}
