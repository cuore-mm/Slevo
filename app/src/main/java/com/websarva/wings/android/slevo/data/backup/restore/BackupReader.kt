package com.websarva.wings.android.slevo.data.backup.restore

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.data.backup.BackupResourceLimitExceededException
import com.websarva.wings.android.slevo.data.backup.BackupResourceLimits
import com.websarva.wings.android.slevo.data.backup.export.BackupDataMapper
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupManifest
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.data.model.TextDisplaySettingsConstraints
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * バックアップ ZIP を読み取り、manifest・DB・DataStore JSON を検証して [BackupPreview] を生成する。
 *
 * 以下の検証を順に行う:
 * 1. ZIP entry の path validation (zip-slip、未知 entry、重複 entry)
 * 2. 必須 entry の存在確認
 * 3. manifest JSON の parse と field validation
 * 4. backupFormatVersion / backupMode / databaseVersion migration path の検証
 * 5. Cookie 整合性 (manifest と entry の一致)
 * 6. DB schema compatibility ([BackupDatabaseValidator] 経由)
 * 7. DataStore JSON の parse と値 validation
 *
 * すべての展開entryは [resourceLimits] に従ってバイト数制限され、
 * 一時DBは成功previewへの引き渡し以外では必ず削除される。
 *
 * @param moshi JSON のデシリアライズに使う Moshi インスタンス。
 * @param dbValidator DB schema 検証に使う validator。テスト時に fake で置き換え可能。
 * @param currentDbVersion 現在の Room DB version（DI 目的で保持。validateManifest は代わりに AppDatabase の static helper を使う）。
 * @param resourceLimits 展開サイズ上限policy。テストでは小さい値を注入できる。
 */
@Singleton
@OptIn(ExperimentalStdlibApi::class)
class BackupReader @Inject constructor(
    private val moshi: Moshi,
    private val dbValidator: BackupDatabaseValidator,
    @CurrentDatabaseVersion private val currentDbVersion: Int,
    private val resourceLimits: BackupResourceLimits = BackupResourceLimits(),
) {
    private val manifestAdapter = moshi.adapter<BackupManifest>()
    private val settingsAdapter = moshi.adapter<BackupSettingsJson>()
    private val tabsAdapter = moshi.adapter<BackupTabsJson>()
    private val cookiesAdapter = moshi.adapter<BackupCookiesJson>()

    /**
     * Temp DB file の作成方法。production では platform temp directory を使う。
     * test から差し替え可能にするため [internal] [var] として公開する。
     */
    internal var tempDbFileProvider: () -> File = {
        File.createTempFile("backup_db_", ".db")
    }

    /**
     * Temp DB output stream の作成方法。
     *
     * productionでは [File.outputStream] を使い、testではwrite failureを注入できる。
     */
    internal var tempDbOutputProvider: (File) -> OutputStream = { file ->
        file.outputStream()
    }

    /** bounded copyで使う固定バッファサイズ (8 KiB)。 */
    private val copyBuffer = ByteArray(8192)

    /**
     * ZIP [InputStream] からバックアップを読み取り、検証して [BackupPreview] を生成する。
     *
     * @param input ZIP ファイルの入力ストリーム。
     * @return 検証成功時は [BackupPreview] を含む [Result]、失敗時は [BackupRestoreResult.Invalid] または
     *   [BackupRestoreResult.Failure]。
     */
    fun readBackup(input: InputStream): BackupReaderResult {
        // --- 1. ZIP entry の読み取りと path validation ---
        val jsonEntries = mutableMapOf<String, ByteArray>()
        var dbTempFile: File? = null
        val seenEntries = mutableSetOf<String>()
        var totalDecompressed: Long = 0
        var entryCount = 0
        // 成功previewへのownership transfer以外ではfinallyがtemp DBを削除する。
        try {
            try {
                ZipInputStream(input).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    // --- entry count limit ---
                    entryCount++
                    if (entryCount > resourceLimits.entryCount) {
                        val ex = BackupResourceLimitExceededException(
                            entryName = name,
                            actual = entryCount.toLong(),
                            limit = resourceLimits.entryCount.toLong(),
                            target = "entry-count",
                        )
                        return handleLimitException(dbTempFile, ex)
                    }
                    // --- duplicate check (file entry) ---
                    if (name in seenEntries) {
                        return cleanupAndError(
                            dbTempFile,
                            BackupRestoreResult.Invalid("duplicate entry: $name"),
                        )
                    }
                    // --- path validation ---
                    val pathError = validatePath(name, entry.isDirectory)
                    if (pathError != null) {
                        return cleanupAndError(
                            dbTempFile,
                            BackupRestoreResult.Invalid(pathError),
                        )
                    }
                    if (!entry.isDirectory) {
                        seenEntries.add(name)
                        // DB entry → bounded stream to temp file
                        if (name == DB_PATH) {
                            val tmp = tempDbFileProvider()
                            try {
                                val entryLimit = resourceLimits.limitForEntry(DB_PATH)
                                    ?: 0L // known entry; must be present
                                val written = tempDbOutputProvider(tmp).use { output ->
                                    copyWithLimit(
                                        input = zip,
                                        output = output,
                                        entryLimit = entryLimit,
                                        totalDecompressed = totalDecompressed,
                                        totalLimit = resourceLimits.totalBytes,
                                        entryName = name,
                                    )
                                }
                                totalDecompressed += written
                            } catch (e: CancellationException) {
                                // cancellationでも部分temp DBを残さず、構造化並行性を維持する。
                                tmp.delete()
                                throw e
                            } catch (e: Exception) {
                                tmp.delete()
                                // BackupResourceLimitExceededException はここで catch して展開後の専用扱い
                                if (e is BackupResourceLimitExceededException) {
                                    return handleLimitException(dbTempFile, e)
                                }
                                // その他のI/O失敗
                                throw RuntimeException(
                                    "failed to stream DB entry: ${e.message}", e)
                            }
                            dbTempFile = tmp
                        } else {
                            // JSON entry → bounded memory read
                            val entryLimit = resourceLimits.limitForEntry(name)
                            val bytes = if (entryLimit != null) {
                                readBytesWithLimit(
                                    input = zip,
                                    entryLimit = entryLimit,
                                    totalDecompressed = totalDecompressed,
                                    totalLimit = resourceLimits.totalBytes,
                                    entryName = name,
                                )
                            } else {
                                // unknown file entry — should have been caught by path validation
                                return cleanupAndError(
                                    dbTempFile,
                                    BackupRestoreResult.Invalid("unknown entry: $name"),
                                )
                            }
                            totalDecompressed += bytes.size.toLong()
                            jsonEntries[name] = bytes
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                }
            } catch (e: BackupResourceLimitExceededException) {
            // limit超過はInvalidとして扱う
                return handleLimitException(dbTempFile, e)
            } catch (e: CancellationException) {
            // CancellationExceptionを結果型へ変換せず、cleanup後に再throwする。
                dbTempFile?.delete()
                throw e
            } catch (e: Exception) {
            // --- stream/read failure cleanup ---
                dbTempFile?.delete()
                return BackupReaderResult.Error(
                    BackupRestoreResult.Invalid("failed to read ZIP: ${e.message}"),
                )
            }

        // --- 2. 必須 entry の存在確認 ---
        for (required in REQUIRED_ENTRIES) {
            if (required !in seenEntries) {
                return cleanupAndError(
                    dbTempFile,
                    BackupRestoreResult.Invalid("missing required entry: $required"),
                )
            }
        }

        // --- 3. manifest の parse ---
        val manifest = try {
            manifestAdapter.fromJson(String(jsonEntries[MANIFEST_PATH]!!, Charsets.UTF_8))
        } catch (e: Exception) {
            return cleanupAndError(
                dbTempFile,
                BackupRestoreResult.Invalid("invalid manifest JSON: ${e.message}"),
            )
        }
        if (manifest == null) {
            return cleanupAndError(
                dbTempFile,
                BackupRestoreResult.Invalid("manifest JSON is null"),
            )
        }

        // --- 4. manifest field validation ---
        val manifestError = validateManifest(manifest)
        if (manifestError != null) {
            return cleanupAndError(
                dbTempFile,
                BackupRestoreResult.Invalid(manifestError),
            )
        }

        // --- 5. Cookie 整合性 ---
        val hasCookieEntry = COOKIES_PATH in seenEntries
        if (manifest.included.cookies != hasCookieEntry) {
            return cleanupAndError(
                dbTempFile,
                BackupRestoreResult.Invalid(
                    "cookie inconsistency: manifest.included.cookies=${manifest.included.cookies}," +
                        " entry exists=$hasCookieEntry",
                ),
            )
        }

        // --- 6. DB schema pre-migration validation ---
        val validatedDbFile = dbTempFile
            ?: return BackupReaderResult.Error(
                BackupRestoreResult.Invalid("DB temp file missing"),
            )
        try {
            val dbError = dbValidator.preValidate(validatedDbFile, manifest.databaseVersion)
            if (dbError != null) {
                return cleanupAndError(
                    dbTempFile,
                    BackupRestoreResult.Invalid("DB validation failed: $dbError"),
                )
            }
        } catch (e: CancellationException) {
            // DB validator内でcancelされた場合もpartial temp DBを残さない。
            dbTempFile?.delete()
            throw e
        } catch (e: Exception) {
            // DB validation threw unexpected exception
            return cleanupAndError(
                dbTempFile,
                BackupRestoreResult.Invalid("DB validation failed: ${e.message}"),
            )
        }

        // --- 7. DataStore JSON の parse と値 validation ---
        val settingsJson = parseSettings(jsonEntries[SETTINGS_PATH]!!)
            ?: return cleanupAndError(
                dbTempFile,
                BackupRestoreResult.Invalid("invalid settings JSON"),
            )
        val tabsJson = parseTabs(jsonEntries[TABS_PATH]!!)
            ?: return cleanupAndError(
                dbTempFile,
                BackupRestoreResult.Invalid("invalid tabs JSON"),
            )
        val cookiesJson = if (manifest.included.cookies) {
            parseCookies(jsonEntries[COOKIES_PATH]!!)
                ?: return cleanupAndError(
                    dbTempFile,
                    BackupRestoreResult.Invalid("invalid cookies JSON"),
                )
        } else {
            null
        }

        // --- 8. BackupPreview の生成 ---
        // Ownership transfer: null out dbTempFile so cleanup scope doesn't delete it.
        val transferredDbFile = dbTempFile
        dbTempFile = null
        return BackupReaderResult.Success(
            BackupPreview(
                createdAt = manifest.createdAt,
                appVersionCode = manifest.appVersionCode,
                appVersionName = manifest.appVersionName,
                databaseVersion = manifest.databaseVersion,
                containsCookies = manifest.included.cookies,
                dbFile = transferredDbFile!!,
                settingsJson = settingsJson,
                tabsJson = tabsJson,
                cookiesJson = cookiesJson,
            ),
        )
        } finally {
            // dbTempFile=null is the only ownership transfer to the preview caller.
            dbTempFile?.delete()
        }
    }

    // --- Resource limit helpers ---

    /**
     * [InputStream] から固定バッファでbyteを読み取り [OutputStream] へ書き込む。
     *
     * entry上限とtotal上限の両方を1回の書き込みごとにチェックし、
     * 上限を1 byteでも超える場合は [BackupResourceLimitExceededException] を投げる。
     * 上限超過検出用の余分なbyteはoutputへ書き込まない。
     *
     * @param input 読み取り元ストリーム。
     * @param output 書き込み先ストリーム（呼び出し側でcloseすること）。
     * @param entryLimit 個別エントリ上限（バイト）。
     * @param totalDecompressed 現在の合計展開byte数。
     * @param totalLimit 合計上限（バイト）。
     * @param entryName エントリ名（超過例外用）。
     * @return このエントリで書き込んだbyte数。
     * @throws BackupResourceLimitExceededException 上限超過時。
     */
    private fun copyWithLimit(
        input: InputStream,
        output: OutputStream,
        entryLimit: Long,
        totalDecompressed: Long,
        totalLimit: Long,
        entryName: String,
    ): Long {
        var entryWritten: Long = 0
        while (true) {
            // --- Remaining capacity ---
            val entryRemaining = entryLimit - entryWritten
            val totalRemaining = totalLimit - totalDecompressed - entryWritten
            if (entryRemaining < 0) {
                throw BackupResourceLimitExceededException(
                    entryName = entryName,
                    actual = entryWritten,
                    limit = entryLimit,
                    target = "entry",
                )
            }
            if (totalRemaining < 0) {
                throw BackupResourceLimitExceededException(
                    entryName = null,
                    actual = totalDecompressed + entryWritten,
                    limit = totalLimit,
                    target = "total",
                )
            }
            // 余剰1 byteだけを読み、上限超過を検出する。
            val bytesRead = input.read(copyBuffer, 0, requestLength(minOf(entryRemaining, totalRemaining)))
            if (bytesRead == -1) break
            if (bytesRead > entryRemaining) {
                throw BackupResourceLimitExceededException(
                    entryName = entryName,
                    actual = entryWritten + bytesRead,
                    limit = entryLimit,
                    target = "entry",
                )
            }
            if (bytesRead > totalRemaining) {
                throw BackupResourceLimitExceededException(
                    entryName = null,
                    actual = totalDecompressed + entryWritten + bytesRead,
                    limit = totalLimit,
                    target = "total",
                )
            }
            output.write(copyBuffer, 0, bytesRead)
            entryWritten += bytesRead
        }
        return entryWritten
    }

    /** remaining上限と超過検出用1 byteを超えないread長を返す。 */
    private fun requestLength(remaining: Long): Int {
        val maxWithProbe = if (remaining >= copyBuffer.size.toLong()) {
            copyBuffer.size.toLong()
        } else {
            remaining + 1
        }
        return maxWithProbe.toInt()
    }

    /**
     * [InputStream] から上限付きで全byteを読み取り、[ByteArray] を返す。
     *
     * entry上限とtotal上限をチェックし、上限超過時は [BackupResourceLimitExceededException] を投げる。
     *
     * @param input 読み取り元ストリーム。
     * @param entryLimit 個別エントリ上限（バイト）。
     * @param totalDecompressed 現在の合計展開byte数。
     * @param totalLimit 合計上限（バイト）。
     * @param entryName エントリ名（超過例外用）。
     * @return 読み取ったbyte配列。
     * @throws BackupResourceLimitExceededException 上限超過時。
     */
    private fun readBytesWithLimit(
        input: InputStream,
        entryLimit: Long,
        totalDecompressed: Long,
        totalLimit: Long,
        entryName: String,
    ): ByteArray {
        val buffer = ByteArrayOutputStream()
        copyWithLimit(input, buffer, entryLimit, totalDecompressed, totalLimit, entryName)
        return buffer.toByteArray()
    }

    /**
     * [BackupResourceLimitExceededException] を処理し、
     * temp DBを削除して [BackupRestoreResult.Invalid] を返す。
     */
    private fun handleLimitException(
        dbTempFile: File?,
        e: BackupResourceLimitExceededException,
    ): BackupReaderResult.Error {
        dbTempFile?.delete()
        val detail = e.message ?: "backup exceeds resource limits"
        return BackupReaderResult.Error(BackupRestoreResult.Invalid("size limit exceeded: $detail"))
    }

    /**
     * Failure path で DB temp file の best-effort cleanup を行い、
     * [BackupReaderResult.Error] を返す helper。
     */
    private fun cleanupAndError(
        dbTempFile: File?,
        result: BackupRestoreResult,
    ): BackupReaderResult.Error {
        dbTempFile?.delete()
        return BackupReaderResult.Error(result)
    }

    // --- Path validation ---

    /**
     * ZIP entry の path を検証する。
     *
     * - directory entry は `database/` と `datastore/` のみ許容し、無視する。
     * - `../`、絶対パス、空 entry 名、未知 file entry、未知 directory entry を拒否する。
     *
     * @return エラーメッセージ。問題なしの場合は null。
     */
    private fun validatePath(name: String, isDirectory: Boolean): String? {
        if (name.contains("\\")) return "backslash in path: $name"
        if (name.startsWith("/")) return "absolute path: $name"
        if (name.contains("../")) return "path traversal: $name"
        if (name.isEmpty()) return "empty entry name"

        if (isDirectory) {
            // directory entry は database/ と datastore/ のみ許容
            if (name !in ALLOWED_DIRECTORY_ENTRIES) {
                return "unknown directory entry: $name"
            }
            return null
        }

        if (name !in ALL_KNOWN_FILE_ENTRIES) {
            return "unknown entry: $name"
        }
        return null
    }

    // --- Manifest validation ---

    /**
     * manifest の field 値を検証する。
     *
     * @return エラーメッセージ。問題なしの場合は null。
     */
    private fun validateManifest(manifest: BackupManifest): String? {
        if (manifest.backupFormatVersion != EXPECTED_FORMAT_VERSION) {
            return "unsupported backupFormatVersion: ${manifest.backupFormatVersion}"
        }
        if (manifest.backupMode != EXPECTED_BACKUP_MODE) {
            return "unsupported backupMode: ${manifest.backupMode}"
        }
        if (!manifest.included.database) return "manifest.included.database must be true"
        if (!manifest.included.settings) return "manifest.included.settings must be true"
        if (!manifest.included.tabs) return "manifest.included.tabs must be true"
        if (manifest.databaseVersion > AppDatabase.CURRENT_DATABASE_VERSION) {
            return "databaseVersion is in the future:" +
                " manifest=${manifest.databaseVersion}, current=${AppDatabase.CURRENT_DATABASE_VERSION}"
        }
        if (manifest.databaseVersion < AppDatabase.MINIMUM_RESTORABLE_DATABASE_VERSION) {
            return "databaseVersion is too old:" +
                " manifest=${manifest.databaseVersion}," +
                " minimum=${AppDatabase.MINIMUM_RESTORABLE_DATABASE_VERSION}"
        }
        if (!AppDatabase.hasMigrationPathForRestore(manifest.databaseVersion)) {
            return "no migration path:" +
                " manifest=${manifest.databaseVersion}," +
                " current=${AppDatabase.CURRENT_DATABASE_VERSION}"
        }
        return null
    }

    // --- DataStore JSON parse と validation ---

    /**
     * settings JSON を parse し、field 値を検証する。
     *
     * @return 検証済みの [BackupSettingsJson]。エラー時は null。
     */
    private fun parseSettings(bytes: ByteArray): BackupSettingsJson? {
        return try {
            val json = settingsAdapter.fromJson(String(bytes, Charsets.UTF_8)) ?: return null
            validateSettings(json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * settings の field 値を検証する。
     *
     * - themeMode は既知の値 ("light", "dark", "system") のみ許容する。
     * - scale / lineHeight は canonical range 内の有限値のみ許容する。
     * - gesture direction key は既知の kebab-case のみ許容する。
     * - gesture action value は null または [GestureAction] の既知の kebab-case のみ許容する。
     *
     * @return 検証済みの [BackupSettingsJson]。エラー時は null。
     */
    private fun validateSettings(json: BackupSettingsJson): BackupSettingsJson? {
        if (json.themeMode !in KNOWN_THEME_MODES) return null
        if (!TextDisplaySettingsConstraints.isValidTextScale(json.textScale)) return null
        if (!TextDisplaySettingsConstraints.isValidTextScale(json.headerTextScale)) return null
        if (!TextDisplaySettingsConstraints.isValidTextScale(json.bodyTextScale)) return null
        if (!TextDisplaySettingsConstraints.isValidLineHeight(json.lineHeight)) return null
        val knownGestureActions = GestureAction.entries
            .map { BackupDataMapper.enumNameToKebabCase(it.name) }
            .toSet()
        for ((key, action) in json.gestureSettings.actions) {
            if (key !in KNOWN_GESTURE_DIRECTIONS) return null
            if (action != null && action !in knownGestureActions) return null
        }
        return json
    }

    /**
     * tabs JSON を parse し、field 値を検証する。
     *
     * @return 検証済みの [BackupTabsJson]。エラー時は null。
     */
    private fun parseTabs(bytes: ByteArray): BackupTabsJson? {
        return try {
            val json = tabsAdapter.fromJson(String(bytes, Charsets.UTF_8)) ?: return null
            if (!TabPage.isValidIndex(json.lastSelectedTabsPage)) return null
            json
        } catch (_: Exception) {
            null
        }
    }

    /**
     * cookies JSON を parse し、field 値を検証する。
     *
     * - name、domain、path は空文字列を許容しない。
     *
     * @return 検証済みの [BackupCookiesJson]。エラー時は null。
     */
    private fun parseCookies(bytes: ByteArray): BackupCookiesJson? {
        return try {
            val json = cookiesAdapter.fromJson(String(bytes, Charsets.UTF_8)) ?: return null
            for (item in json.cookies) {
                if (item.name.isEmpty()) return null
                if (item.domain.isEmpty()) return null
                if (item.path.isEmpty()) return null
            }
            json
        } catch (_: Exception) {
            null
        }
    }

    // --- Cleanup helper ---

    /** 定数。 */
    companion object {
        const val MANIFEST_PATH = "manifest.json"
        const val DB_PATH = "database/slevo.db"
        const val SETTINGS_PATH = "datastore/settings.json"
        const val TABS_PATH = "datastore/tabs.json"
        const val COOKIES_PATH = "datastore/cookies.json"

        const val EXPECTED_FORMAT_VERSION = 1
        const val EXPECTED_BACKUP_MODE = "full"

        val REQUIRED_ENTRIES = listOf(MANIFEST_PATH, DB_PATH, SETTINGS_PATH, TABS_PATH)
        val ALLOWED_DIRECTORY_ENTRIES = setOf("database/", "datastore/")
        val ALL_KNOWN_FILE_ENTRIES = setOf(
            MANIFEST_PATH, DB_PATH, SETTINGS_PATH, TABS_PATH, COOKIES_PATH,
        )

        val KNOWN_THEME_MODES = setOf("light", "dark", "system")
        val KNOWN_GESTURE_DIRECTIONS = setOf(
            "right", "right-up", "right-left", "right-down",
            "left", "left-up", "left-right", "left-down",
        )
    }
}

/**
 * [BackupReader.readBackup] の結果を表す sealed class。
 *
 * [BackupReader] 内部では [BackupRestoreResult] を直接使い、
 * 呼び出し元へ [BackupPreview] を返すためのラッパー。
 */
sealed class BackupReaderResult {
    /** 検証成功。[preview] に検証済みの preview データが含まれる。 */
    data class Success(val preview: BackupPreview) : BackupReaderResult()
    /** 検証失敗。[result] に失敗種別が含まれる。 */
    data class Error(val result: BackupRestoreResult) : BackupReaderResult()
}

/**
 * 現在の Room DB version を注入するための Hilt qualifier。
 *
 * [BackupReader] の constructor で [currentDbVersion] を識別するために使う。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CurrentDatabaseVersion
