package com.websarva.wings.android.slevo.data.backup.restore

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupManifest
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import java.io.File
import java.io.InputStream
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
 * @param moshi JSON のデシリアライズに使う Moshi インスタンス。
 * @param dbValidator DB schema 検証に使う validator。テスト時に fake で置き換え可能。
 * @param currentDbVersion 現在の Room DB version（DI 目的で保持。validateManifest は代わりに AppDatabase の static helper を使う）。
 */
@Singleton
@OptIn(ExperimentalStdlibApi::class)
class BackupReader @Inject constructor(
    private val moshi: Moshi,
    private val dbValidator: BackupDatabaseValidator,
    @CurrentDatabaseVersion private val currentDbVersion: Int,
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
        try {
            ZipInputStream(input).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
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
                        // DB entry → stream to temp file
                        if (name == DB_PATH) {
                            val tmp = tempDbFileProvider()
                            try {
                                tmp.outputStream().use { output -> zip.copyTo(output) }
                            } catch (e: Exception) {
                                tmp.delete()
                                // re-wrap to keep cleanup scope clean
                                throw RuntimeException(
                                    "failed to stream DB entry: ${e.message}", e)
                            }
                            dbTempFile = tmp
                        } else {
                            // JSON entry → memory
                            jsonEntries[name] = zip.readBytes()
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
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
                // guard: cleanupAndError handles dbTempFile cleanup
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
     * - scale / lineHeight は正の有限値のみ許容する。
     * - gesture direction key は既知の kebab-case のみ許容する。
     *
     * @return 検証済みの [BackupSettingsJson]。エラー時は null。
     */
    private fun validateSettings(json: BackupSettingsJson): BackupSettingsJson? {
        if (json.themeMode !in KNOWN_THEME_MODES) return null
        if (!json.textScale.isFinite() || json.textScale <= 0f) return null
        if (!json.headerTextScale.isFinite() || json.headerTextScale <= 0f) return null
        if (!json.bodyTextScale.isFinite() || json.bodyTextScale <= 0f) return null
        if (!json.lineHeight.isFinite() || json.lineHeight <= 0f) return null
        for (key in json.gestureSettings.actions.keys) {
            if (key !in KNOWN_GESTURE_DIRECTIONS) return null
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
            if (json.lastSelectedTabsPage < 0) return null
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
