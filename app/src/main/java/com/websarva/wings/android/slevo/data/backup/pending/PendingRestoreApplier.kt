package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.data.backup.restore.BackupDatabaseValidator
import com.websarva.wings.android.slevo.data.backup.BackupMoshiFactory
import com.websarva.wings.android.slevo.data.backup.restore.RealBackupDatabaseValidator
import com.websarva.wings.android.slevo.data.backup.model.BackupCookiesJson
import com.websarva.wings.android.slevo.data.backup.model.BackupSettingsJson
import com.websarva.wings.android.slevo.data.backup.model.BackupTabsJson
import com.websarva.wings.android.slevo.data.datasource.local.DATABASE_VERSION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * pending restore 用 DataStore 反映処理の抽象。
 *
 * [PendingRestoreApplier] から分離し、JVM unit test では fake 実装へ差し替える。
 * prepareが成功した後だけreflectを呼び出し、rollbackはpending directoryのdurable snapshotを使う。
 */
internal interface PendingRestoreDataStoreReflector {
    /** staged DataStore JSONを検証し、restore前snapshotをpendingへ永続化する。 */
    suspend fun prepareRollbackSnapshot(pendingDir: File, includeCookies: Boolean): String?

    /**
     * staging 済み JSON を DataStore へ反映する。
     */
    suspend fun reflect(pendingDir: File, includeCookies: Boolean): String?

    /** durable snapshotからDataStoreをrestore前状態へ戻す。 */
    suspend fun restoreRollbackSnapshot(pendingDir: File): String?
}

/**
 * Quarantine処理が使用するfilesystem操作を表す。
 *
 * 実filesystemを直接呼び出す処理をこの契約へ集約し、JVM testでrename、copy、deleteおよび
 * postconditionの結果を決定的に注入できるようにする。
 */
internal interface PendingRestoreFileOperations {
    /** sourceをdestinationへrenameし、APIの成否を返す。 */
    fun rename(source: File, destination: File): Boolean

    /** sourceをdestinationへ上書きcopyする。失敗時は例外を送出する。 */
    fun copy(source: File, destination: File)

    /** fileを削除し、削除できたかを返す。 */
    fun delete(file: File): Boolean

    /** fileがfilesystem上に存在するかを返す。 */
    fun exists(file: File): Boolean

    /** fileがregular fileかを返す。 */
    fun isFile(file: File): Boolean

    /** fileの現在sizeをbyte単位で返す。 */
    fun length(file: File): Long
}

/**
 * [PendingRestoreFileOperations]の本番実装。
 *
 * Kotlin/JavaのFile APIへ直接委譲し、quarantineのpostcondition判定は呼び出し側へ残す。
 */
internal class RealPendingRestoreFileOperations : PendingRestoreFileOperations {
    /** File.renameToの結果を返す。 */
    override fun rename(source: File, destination: File): Boolean = source.renameTo(destination)

    /** File.copyToでdestinationを上書きする。 */
    override fun copy(source: File, destination: File) {
        source.copyTo(destination, overwrite = true)
    }

    /** File.deleteの結果を返す。 */
    override fun delete(file: File): Boolean = file.delete()

    /** File.existsの結果を返す。 */
    override fun exists(file: File): Boolean = file.exists()

    /** File.isFileの結果を返す。 */
    override fun isFile(file: File): Boolean = file.isFile

    /** File.lengthの結果を返す。 */
    override fun length(file: File): Long = file.length()
}

/**
 * [PendingRestoreDataStoreReflector] の本番実装。
 *
 * pending directory の JSON を読み取り、[PendingRestoreDataStoreWriter] で
 * settings / tabs / cookies を DataStore へ反映する。DB置換前のrollback snapshotを保存し、
 * process death後のstale recoveryでも同じsnapshotから復元する。
 */
@OptIn(ExperimentalStdlibApi::class)
internal class RealPendingRestoreDataStoreReflector(
    private val context: Context,
    private val moshi: Moshi,
) : PendingRestoreDataStoreReflector {
    private val settingsAdapter = moshi.adapter<BackupSettingsJson>()
    private val tabsAdapter = moshi.adapter<BackupTabsJson>()
    private val cookiesAdapter = moshi.adapter<BackupCookiesJson>()

    /** staged restore dataの全parse/pre-validation後にDataStoreへ書く値を保持する。 */
    private data class PreparedRestoreData(
        val settings: BackupSettingsJson,
        val tabs: BackupTabsJson,
        val preparedCookies: PendingRestoreDataStoreWriter.PreparedCookies.Success?,
    )

    /** staged JSONを全て読み取り、DataStore write前のvalidationを完了する。 */
    private fun loadPreparedRestoreData(
        pendingDir: File,
        includeCookies: Boolean,
        writer: PendingRestoreDataStoreWriter,
    ): PreparedRestoreData {
        val settingsFile = File(pendingDir, "datastore/settings.json")
        val tabsFile = File(pendingDir, "datastore/tabs.json")
        val cookiesFile = File(pendingDir, "datastore/cookies.json")

        val settings = settingsAdapter.fromJson(settingsFile.readText())
            ?: throw IllegalStateException("failed to parse settings JSON")
        val tabs = tabsAdapter.fromJson(tabsFile.readText())
            ?: throw IllegalStateException("failed to parse tabs JSON")

        val preparedCookies = if (includeCookies && cookiesFile.exists()) {
            val cookies = cookiesAdapter.fromJson(cookiesFile.readText())
                ?: throw IllegalStateException("failed to parse cookies JSON")
            when (val result = writer.prepareCookies(cookies)) {
                is PendingRestoreDataStoreWriter.PreparedCookies.Success -> result
                is PendingRestoreDataStoreWriter.PreparedCookies.Failure -> {
                    throw IllegalStateException(result.message)
                }
            }
        } else {
            null
        }

        return PreparedRestoreData(settings, tabs, preparedCookies)
    }

    /** staged JSONと現在のDataStoreを検証し、rollback sourceをatomicに保存する。 */
    override suspend fun prepareRollbackSnapshot(
        pendingDir: File,
        includeCookies: Boolean,
    ): String? {
        return try {
            val writer = PendingRestoreDataStoreWriter(context, moshi)
            val prepared = loadPreparedRestoreData(pendingDir, includeCookies, writer)
            val snapshot = writer.snapshotDataStores(includeCookies = prepared.preparedCookies != null)
            PendingRestoreDataStoreSnapshotStore(pendingDir, moshi).write(snapshot)
            null
        } catch (e: Exception) {
            "DataStore snapshot preparation failed: ${e.message}"
        }
    }

    /** pending directory の JSON を DataStore へ反映する。 */
    override suspend fun reflect(pendingDir: File, includeCookies: Boolean): String? {
        return try {
            val writer = PendingRestoreDataStoreWriter(context, moshi)
            val prepared = loadPreparedRestoreData(pendingDir, includeCookies, writer)

            try {
                // --- DataStore write (only after all parse and pre-validation succeed) ---
                writer.writeSettings(prepared.settings)
                writer.writeTabs(prepared.tabs)

                if (prepared.preparedCookies != null) {
                    writer.writePreparedCookies(prepared.preparedCookies.cookieJsonSet)
                }
            } catch (writeError: Exception) {
                // --- Best-effort durable DataStore rollback ---
                val rollbackError = restoreRollbackSnapshot(pendingDir)
                if (rollbackError != null) {
                    return "DataStore reflection failed: ${writeError.message};" +
                        " rollback failed: $rollbackError"
                }
                throw writeError
            }

            null
        } catch (e: Exception) {
            "DataStore reflection failed: ${e.message}"
        }
    }

    /** durable snapshotを読み取り、対象DataStoreをrestore前状態へfull overwriteする。 */
    override suspend fun restoreRollbackSnapshot(pendingDir: File): String? {
        return try {
            val snapshot = PendingRestoreDataStoreSnapshotStore(pendingDir, moshi).read()
                ?: return "DataStore rollback snapshot is missing"
            PendingRestoreDataStoreWriter(context, moshi).restoreDataStores(
                snapshot = snapshot,
                restoreSettings = true,
                restoreTabs = true,
                restoreCookies = snapshot.cookies != null,
            )
            null
        } catch (e: Exception) {
            "DataStore rollback failed: ${e.message}"
        }
    }
}

/**
 * 起動時に pending restore を適用する applier。
 *
 * `SlevoApplication.onCreate()` の `super.onCreate()` 直後に手動生成して
 * [runIfNeeded] を呼ぶ。Hilt / Room (AppDatabase) / DAO / Repository /
 * DB 依存 DataSource には一切依存しない。
 *
 * この class は state machine orchestration に集中し、marker/result file I/O、
 * DB file 操作、DataStore 反映の詳細は collaborator へ委譲する。
 */
@OptIn(ExperimentalStdlibApi::class)
class PendingRestoreApplier private constructor(
    private val context: Context,
    private val dbValidator: BackupDatabaseValidator,
    private val dataStoreReflectorOverride: PendingRestoreDataStoreReflector?,
    private val fileStoreOverride: PendingRestoreFileStore?,
    private val dbSwapperOverride: PendingRestoreDbSwapper?,
    private val fileOperationsOverride: PendingRestoreFileOperations?,
    private val nowProvider: () -> String,
    private val currentDbVersion: Int,
) {
    /**
     * 一つのquarantine file操作と、その後に観測したsource/destination状態を表す。
     *
     * [sourceSize]は操作開始時の不変値で、sourceが消えてdestinationが残る失敗はrollback対象になる。
     */
    private data class QuarantineFileResult(
        val source: File,
        val destination: File,
        val sourceSize: Long,
        val succeeded: Boolean,
        val sourceExists: Boolean,
        val sourceIsFile: Boolean,
        val destinationExists: Boolean,
        val destinationIsFile: Boolean,
        val destinationSize: Long,
        val sourceNeedsRestore: Boolean,
    )

    constructor(context: Context) : this(
        context = context,
        dbValidator = RealBackupDatabaseValidator(),
        dataStoreReflectorOverride = null,
        fileStoreOverride = null,
        dbSwapperOverride = null,
        fileOperationsOverride = null,
        nowProvider = { Instant.now().toString() },
        // Hilt / Room 非依存を保つため const val を直接参照（compile 時に inline される）
        currentDbVersion = DATABASE_VERSION,
    )

    private val appContext = context.applicationContext ?: context
    private val moshi = BackupMoshiFactory.create()
    private val fileStore = fileStoreOverride ?: RealPendingRestoreFileStore(appContext, moshi)
    private val dbSwapper = dbSwapperOverride ?: RealPendingRestoreDbSwapper(appContext)
    private val fileOperations = fileOperationsOverride ?: RealPendingRestoreFileOperations()
    private val dataStoreReflector =
        dataStoreReflectorOverride ?: RealPendingRestoreDataStoreReflector(appContext, moshi)
    private val completionFinalizer = PendingRestoreCompletionFinalizer(
        fileStore = fileStore,
        nowProvider = nowProvider,
        currentDbVersion = currentDbVersion,
        logWarning = { message, error ->
            logWarn(if (error == null) message else "$message: ${error.message}")
        },
    )

    /**
     * pending restore が存在する場合、DB 置換と DataStore 反映を同期的に完了する。
     *
     * 例外は外へ投げない。DB置換前のmarkerだけを `failed` に更新し、
     * recoveryまたはfinalizationを示すmarkerは保持したままresult fileへ失敗を記録する。
     */
    suspend fun runIfNeeded() {
        withContext(Dispatchers.IO) {
            try {
                runIfNeededOnIo()
            } catch (e: Exception) {
                recordStartupRestoreFailureOnIo(e)
            }
        }
    }

    /** I/O dispatcher 上で実行する pending restore の本体処理。 */
    private suspend fun runIfNeededOnIo() {
        val marker = fileStore.readMarker() ?: return

        when (marker.status) {
            RestoreStatus.PREPARED -> applyRestore(marker)
            RestoreStatus.APPLYING, RestoreStatus.DB_SWAPPED -> {
                recoverFromStaleApplyingOrDbSwapped(marker)
            }
            RestoreStatus.ROLLBACK_READY -> recoverFromRollbackReady(marker)
            RestoreStatus.MIGRATION_PENDING -> recoverFromMigrationPending(marker)
            RestoreStatus.ROLLBACK_REQUIRED -> recoverFromRollbackRequired(marker)
            RestoreStatus.COMPLETED -> recoverFromCompleted(marker)
            RestoreStatus.FAILED -> {
                logInfo("failed marker found, result file preserved for diagnostics")
            }
        }
    }

    /** MIGRATION_PENDING: Room migration 前後の状態を判定して復旧する。 */
    private suspend fun recoverFromMigrationPending(marker: PendingRestoreMarker) {
        val liveDbFile = dbSwapper.getLiveDbFile()
        val hasRollback = dbSwapper.hasRollbackBackup(fileStore.rollbackDir, liveDbFile)

        // --- Version classification ---
        val userVersion = dbValidator.getUserVersion(liveDbFile)

        // --- DB unreadable ---
        if (userVersion == null) {
            val reason = "stale MIGRATION_PENDING: db unreadable (userVersion=null)," +
                " markerVersion=${marker.databaseVersion}, currentVersion=$currentDbVersion"
            rollbackMigrationFailure(marker, reason, liveDbFile, hasRollback)
            return
        }

        // --- Room migration 済み（user_version が current 以上） ---
        if (userVersion >= currentDbVersion) {
            val strictError = dbValidator.validate(liveDbFile)
            if (strictError == null) {
                logInfo("MIGRATION_PENDING: strict validation passed, transitioning to COMPLETED")
                val completedMarker = marker.copy(status = RestoreStatus.COMPLETED)
                try {
                    // marker が migration finalization の最初の durable commit point。
                    fileStore.writeMarker(completedMarker)
                } catch (error: Exception) {
                    // 失敗時は元の MIGRATION_PENDING と artifact を保持して retry する。
                    logWarn("MIGRATION_PENDING: COMPLETED marker write failed: ${error.message}")
                    return
                }
                recoverFromCompleted(completedMarker)
                return
            }

            logWarn("MIGRATION_PENDING: strict validation failed: $strictError")
            rollbackMigrationFailure(
                marker = marker,
                reason = "stale MIGRATION_PENDING: $strictError (post-migration)",
                liveDbFile = liveDbFile,
                hasRollback = hasRollback,
            )
            return
        }

        // --- Room migration 前（user_version が marker.databaseVersion と一致） ---
        if (userVersion == marker.databaseVersion) {
            val preError = dbValidator.preValidate(liveDbFile, marker.databaseVersion)
            if (preError == null) {
                if (marker.migrationAttemptStarted) {
                    // old user_version と durable 開始証跡の組み合わせは、前回 transaction が
                    // commit されなかった反復失敗として扱い、同じ migration を再試行しない。
                    val reason = "repeated migration failure: migration attempt already started" +
                        " (dbVersion=${userVersion}, markerVersion=${marker.databaseVersion})"
                    logWarn("MIGRATION_PENDING: $reason")
                    rollbackMigrationFailure(marker, reason, liveDbFile, hasRollback)
                    return
                }
                // migration 前 DB は健全。Room open 後の migration と completion checker に委ねる。
                logInfo("MIGRATION_PENDING: pre-migration validation passed," +
                    " awaiting Room migration (dbVersion=${userVersion}," +
                    " markerVersion=${marker.databaseVersion}, currentVersion=$currentDbVersion)")
                return
            }

            // pre-validation 失敗 → 破損または不整合な旧版 DB
            logWarn("MIGRATION_PENDING: pre-migration validation failed: $preError")
            rollbackMigrationFailure(
                marker = marker,
                reason = "stale MIGRATION_PENDING (pre-migration failure): $preError",
                liveDbFile = liveDbFile,
                hasRollback = hasRollback,
            )
            return
        }

        // --- 想定外の中間 version（marker でも current でもない） ---
        val mismatchReason = "stale MIGRATION_PENDING: unexpected intermediate version" +
            " (userVersion=$userVersion, markerVersion=${marker.databaseVersion}," +
            " currentVersion=$currentDbVersion)"
        logWarn(mismatchReason)
        rollbackMigrationFailure(marker, mismatchReason, liveDbFile, hasRollback)
    }

    /** migration validation failureをDB/DataStore rollbackまたはlegacy保全へ振り分ける。 */
    private suspend fun rollbackMigrationFailure(
        marker: PendingRestoreMarker,
        reason: String,
        liveDbFile: File,
        hasRollback: Boolean,
    ) {
        when {
            marker.hadExistingLiveDb == false -> {
                rollbackAndFail(
                    marker = marker,
                    reason = reason,
                    liveDbFile = liveDbFile,
                    hadExistingLiveDb = false,
                )
            }
            hasRollback -> {
                rollbackAndFail(
                    marker = marker,
                    reason = reason,
                    liveDbFile = liveDbFile,
                    hadExistingLiveDb = true,
                )
            }
            else -> quarantineAndFail(marker, reason, liveDbFile)
        }
    }

    /** ROLLBACK_REQUIRED: completion checker が post-validation 失敗を検出した状態。rollback する。 */
    private suspend fun recoverFromRollbackRequired(marker: PendingRestoreMarker) {
        val liveDbFile = dbSwapper.getLiveDbFile()
        val hasRollback = dbSwapper.hasRollbackBackup(fileStore.rollbackDir, liveDbFile)

        if (marker.hadExistingLiveDb == false) {
            rollbackAndFail(
                marker = marker,
                reason = "rollback required",
                liveDbFile = liveDbFile,
                hadExistingLiveDb = false,
            )
            return
        }

        if (!hasRollback) {
            logWarn("ROLLBACK_REQUIRED: no rollback backup available")
            quarantineAndFail(marker, "rollback required but no rollback backup", liveDbFile)
            return
        }

        logInfo("ROLLBACK_REQUIRED: rolling back")
        rollbackAndFail(marker, "rollback required", liveDbFile, hadExistingLiveDb = true)
    }

    /** COMPLETED: post-validation 成功済み。success result と cleanup を再試行する。 */
    private fun recoverFromCompleted(marker: PendingRestoreMarker) {
        logInfo("COMPLETED: retrying success result write and cleanup")
        completionFinalizer.complete(marker, "restore completed successfully")
    }

    /** stale APPLYING または DB_SWAPPED を復旧する。 */
    private suspend fun recoverFromStaleApplyingOrDbSwapped(marker: PendingRestoreMarker) {
        val liveDbFile = dbSwapper.getLiveDbFile()
        val hasCompleteSnapshot = dbSwapper.hasRollbackBackup(fileStore.rollbackDir, liveDbFile)
        val hadExistingLiveDb = marker.hadExistingLiveDb

        when {
            hadExistingLiveDb == true && hasCompleteSnapshot -> {
                logWarn("stale marker found: ${marker.status}, rolling back from complete snapshot")
                rollbackAndFail(
                    marker = marker,
                    reason = "stale marker: ${marker.status}",
                    liveDbFile = liveDbFile,
                    hadExistingLiveDb = true,
                )
            }
            hadExistingLiveDb == true -> {
                // swap 開始前に process death した。元 live DB はそのまま保持する。
                logWarn("stale marker found: ${marker.status}, incomplete rollback snapshot, preserving live DB")
                if (marker.status == RestoreStatus.DB_SWAPPED) {
                    preserveRollbackRequired(
                        marker,
                        "stale ${marker.status}: incomplete rollback snapshot, live DB preserved",
                    )
                } else {
                    preserveAndFail(
                        marker,
                        "stale ${marker.status}: incomplete rollback snapshot, live DB preserved",
                    )
                }
            }
            hadExistingLiveDb == false -> {
                // 元 DB なしで swap 未開始または swap 後。live DB は復元不要な一時 file のみ。
                logWarn("stale marker found: ${marker.status}, fresh install, cleaning up")
                if (marker.status == RestoreStatus.DB_SWAPPED) {
                    rollbackAndFail(
                        marker = marker,
                        reason = "stale ${marker.status}: fresh install cleanup",
                        liveDbFile = liveDbFile,
                        hadExistingLiveDb = false,
                    )
                } else {
                    dbSwapper.cleanupCorruptFreshInstallDb(liveDbFile)
                    fileStore.writeMarker(
                        marker.copy(
                            status = RestoreStatus.FAILED,
                            failureReason = "stale ${marker.status}: fresh install cleanup",
                        ),
                    )
                    writeFailureResult("stale ${marker.status}: fresh install cleanup", marker)
                }
            }
            else -> {
                // 旧 marker (hadExistingLiveDb = null)。推測しないで保全する。
                preserveAmbiguousStateAndFail(marker, liveDbFile)
            }
        }
    }

    /** stale ROLLBACK_READY を復旧する。 */
    private suspend fun recoverFromRollbackReady(marker: PendingRestoreMarker) {
        val liveDbFile = dbSwapper.getLiveDbFile()
        val hasCompleteSnapshot = dbSwapper.hasRollbackBackup(fileStore.rollbackDir, liveDbFile)
        val hadExistingLiveDb = marker.hadExistingLiveDb

        when {
            hadExistingLiveDb == true && hasCompleteSnapshot -> {
                logWarn("stale ROLLBACK_READY: rolling back from complete snapshot")
                rollbackAndFail(
                    marker = marker,
                    reason = "stale marker: ROLLBACK_READY",
                    liveDbFile = liveDbFile,
                    hadExistingLiveDb = true,
                )
            }
            hadExistingLiveDb == true -> {
                // snapshot が不完全なら live DB を上書きしない。
                logWarn("stale ROLLBACK_READY: incomplete snapshot, preserving live DB")
                preserveAndFail(
                    marker,
                    "stale ROLLBACK_READY: incomplete rollback snapshot, live DB preserved",
                )
            }
            hadExistingLiveDb == false -> {
                // 元 DB なしで swap 開始可能状態。fresh install の live DB sidecar を cleanup。
                logWarn("stale ROLLBACK_READY: fresh install, cleaning up")
                rollbackAndFail(
                    marker = marker,
                    reason = "stale ROLLBACK_READY: fresh install cleanup",
                    liveDbFile = liveDbFile,
                    hadExistingLiveDb = false,
                )
            }
            else -> {
                preserveAmbiguousStateAndFail(marker, liveDbFile)
            }
        }
    }

    /**
     * 元 live DB と rollback files を上書き・削除せず failure を記録する。
     *
     * swap phase が不明な旧 marker 用。
     */
    private fun preserveAmbiguousStateAndFail(marker: PendingRestoreMarker, liveDbFile: File) {
        val liveExists = liveDbFile.exists()
        val hasCompleteSnapshot = dbSwapper.hasRollbackBackup(fileStore.rollbackDir, liveDbFile)
        val reason = buildString {
            append("stale ${marker.status}: ambiguous legacy marker")
            append(", liveExists=")
            append(liveExists)
            append(", completeSnapshot=")
            append(hasCompleteSnapshot)
            append("; manual recovery required")
        }
        logError(reason)
        fileStore.writeMarker(
            marker.copy(
                status = RestoreStatus.FAILED,
                failureReason = reason,
            ),
        )
        writeFailureResult(reason, marker)
    }

    /** rollback snapshot と live DB を保全して failure を記録する。 */
    private fun preserveAndFail(marker: PendingRestoreMarker, reason: String) {
        logError(reason)
        fileStore.writeMarker(
            marker.copy(status = RestoreStatus.FAILED, failureReason = reason),
        )
        writeFailureResult(reason, marker)
    }

    /** rollback source不足時にpending artifactを残し、次回起動のmanual recoveryへ委ねる。 */
    private fun preserveRollbackRequired(marker: PendingRestoreMarker, reason: String) {
        logError(reason)
        fileStore.writeMarker(
            marker.copy(status = RestoreStatus.ROLLBACK_REQUIRED, failureReason = reason),
        )
        writeFailureResult(reason, marker)
    }

    /** failure result file を書き込む helper。 */
    private fun writeFailureResult(reason: String, marker: PendingRestoreMarker) {
        fileStore.writeResult(
            success = false,
            message = reason,
            timestamp = nowProvider(),
            backupDatabaseVersion = marker.databaseVersion,
            currentDatabaseVersion = currentDbVersion,
            migrationRequired = marker.databaseVersion < currentDbVersion,
            migrationCompleted = false,
        )
    }

    /**
     * rollback backupがないinvalid DBをincidentへ保存し、全artifactが移動できた場合だけ終端する。
     *
     * sidecarを先に処理して最初の失敗で停止し、partial incidentとretryable stateを保持する。
     */
    private fun quarantineAndFail(
        marker: PendingRestoreMarker,
        reason: String,
        liveDbFile: File,
    ) {
        logError("quarantine: $reason")
        // --- Incident preparation ---
        val incidentDir = try {
            fileStore.createQuarantineIncidentDir()
        } catch (e: Exception) {
            logError("quarantine setup failed: ${e.message}")
            recordQuarantineFailureResult(marker, reason)
            return
        }

        // --- File preservation ---
        val sources = listOf("-wal", "-shm", "")
            .map { suffix -> File(liveDbFile.absolutePath + suffix) }
            .filter(fileOperations::exists)
        if (sources.isEmpty()) {
            // 無効DBを保存できるsourceがない場合は、空のincidentを成功扱いにしない。
            recordQuarantineFailureResult(marker, reason)
            return
        }
        val preserved = mutableListOf<QuarantineFileResult>()
        val failure = sources.firstOrNull { source ->
            val result = preserveQuarantineFile(source, File(incidentDir, source.name))
            preserved += result
            !result.succeeded
        }

        if (failure != null) {
            rollbackQuarantineFiles(preserved)
            recordQuarantineFailureResult(marker, reason)
            return
        }

        // --- Terminal transition ---
        val finalReason = "$reason (invalid DB quarantined to $incidentDir)"
        fileStore.writeMarker(marker.copy(status = RestoreStatus.FAILED, failureReason = finalReason))
        writeFailureResult(finalReason, marker)
        fileStore.cleanupPending()
    }

    /**
     * DB本体またはsidecarをincidentへ移動し、destinationとsourceのpostconditionを検証する。
     *
     * renameが失敗した場合はcopy後にsourceを削除し、全postconditionが成立した時だけ成功を返す。
     */
    private fun preserveQuarantineFile(source: File, destination: File): QuarantineFileResult {
        val sourceSize = fileOperations.length(source)
        var operationError: Exception? = null

        try {
            if (!fileOperations.rename(source, destination)) {
                fileOperations.copy(source, destination)
                if (!fileOperations.delete(source)) {
                    logError("quarantine source delete failed for ${source.name}")
                }
            }
        } catch (e: Exception) {
            operationError = e
        }

        val result = createQuarantineFileResult(source, destination, sourceSize, operationError)
        if (!result.succeeded) {
            logError(
                "quarantine failed for ${source.name}: " +
                    "sourceExists=${result.sourceExists}, destinationExists=${result.destinationExists}, " +
                    "destinationIsFile=${result.destinationIsFile}, destinationSize=${result.destinationSize}, " +
                    "expectedSize=${result.sourceSize}, error=${operationError?.message}",
            )
        }
        return result
    }

    /**
     * filesystem状態からquarantine fileのstructured outcomeを作成する。
     *
     * sourceが消失してdestinationが利用可能な失敗は、現在fileもrollback対象として返す。
     */
    private fun createQuarantineFileResult(
        source: File,
        destination: File,
        sourceSize: Long,
        operationError: Exception?,
    ): QuarantineFileResult {
        val sourceExists = fileOperations.exists(source)
        val sourceIsFile = fileOperations.isFile(source)
        val destinationExists = fileOperations.exists(destination)
        val destinationIsFile = fileOperations.isFile(destination)
        val destinationSize = if (destinationExists) fileOperations.length(destination) else -1L
        val destinationMatches = destinationIsFile && destinationSize == sourceSize
        return QuarantineFileResult(
            source = source,
            destination = destination,
            sourceSize = sourceSize,
            succeeded = operationError == null && destinationMatches && !sourceExists,
            sourceExists = sourceExists,
            sourceIsFile = sourceIsFile,
            destinationExists = destinationExists,
            destinationIsFile = destinationIsFile,
            destinationSize = destinationSize,
            sourceNeedsRestore = !sourceExists && destinationExists,
        )
    }

    /**
     * partial quarantine後に利用可能なdestination artifactをsourceへbest-effortで戻す。
     *
     * destinationとincidentは削除せず、復元後のregular-fileとsizeだけを確認する。
     */
    private fun rollbackQuarantineFiles(results: List<QuarantineFileResult>) {
        // --- Current failure first, then successful files in reverse order ---
        results.asReversed().filter { it.sourceNeedsRestore }.forEach { result ->
            if (!fileOperations.exists(result.destination)) {
                logWarn("quarantine rollback source missing for ${result.source.name}")
                return@forEach
            }

            try {
                fileOperations.copy(result.destination, result.source)
                val restored = fileOperations.isFile(result.source) &&
                    fileOperations.length(result.source) == result.sourceSize
                if (!restored) {
                    logWarn("quarantine rollback postcondition failed for ${result.source.name}")
                }
            } catch (e: Exception) {
                logWarn("quarantine rollback failed for ${result.source.name}: ${e.message}")
            }
        }
    }

    /** 部分失敗時にretryable markerを変更せずfailure resultだけをbest-effortで記録する。 */
    private fun recordQuarantineFailureResult(marker: PendingRestoreMarker, reason: String) {
        val finalReason = "$reason (quarantine failed: manual intervention required)"
        try {
            writeFailureResult(finalReason, marker)
        } catch (e: Exception) {
            logWarn("quarantine failure result write failed: ${e.message}")
        }
    }

    /**
     * 想定外例外の診断結果を記録し、回復可能なmarkerをterminal failureへ変更しない。
     *
     * markerはDB/DataStore recoveryの再開点であるため、DB置換前の [RestoreStatus.PREPARED]
     * と [RestoreStatus.APPLYING] だけを [RestoreStatus.FAILED] へ更新する。保護対象markerの
     * write、pending cleanup、artifact削除は行わず、failure resultだけをbest-effortで記録する。
     */
    private fun recordStartupRestoreFailureOnIo(e: Exception) {
        logError("startup restore failed unexpectedly", e)

        // --- Marker classification ---
        val marker = try {
            fileStore.readMarker()
        } catch (markerReadError: Exception) {
            // 直前のatomic markerを推測せず、二次I/O failureだけを診断してresult記録へ進む。
            logWarn("startup restore marker read failed: ${markerReadError.message}")
            null
        }

        try {
            when (marker?.status) {
                RestoreStatus.PREPARED,
                RestoreStatus.APPLYING -> fileStore.writeMarker(
                    marker.copy(
                        status = RestoreStatus.FAILED,
                        failureReason = "unexpected error: ${e.message}",
                    ),
                )

                RestoreStatus.ROLLBACK_READY,
                RestoreStatus.DB_SWAPPED,
                RestoreStatus.MIGRATION_PENDING,
                RestoreStatus.ROLLBACK_REQUIRED,
                RestoreStatus.COMPLETED,
                RestoreStatus.FAILED,
                null -> {
                    // 回復/完了markerと既存FAILEDはそのまま保持し、marker再writeを避ける。
                }
            }
        } catch (markerWriteError: Exception) {
            // pre-swap markerのatomic write failureでも直前のmarkerとpendingを保持する。
            logWarn("startup restore failure marker write failed: ${markerWriteError.message}")
        }

        // --- Best-effort diagnostic result ---
        try {
            fileStore.writeResult(
                success = false,
                message = "unexpected error: ${e.message}",
                timestamp = nowProvider(),
            )
        } catch (resultWriteError: Exception) {
            // result failureはmarker classificationやrecovery artifactを変更しない。
            logWarn("startup restore failure result write failed: ${resultWriteError.message}")
        }
    }

    /** pending restore の prepared state を適用する。 */
    private suspend fun applyRestore(marker: PendingRestoreMarker) {
        val stagedDbFile = File(fileStore.pendingDir, "database/slevo.db")
        val liveDbFile = dbSwapper.getLiveDbFile()
        val hadExistingLiveDb = liveDbFile.exists()

        // --- Applying marker: rollback snapshot 作成前に元 DB 有無を永続化 ---
        val applyingMarker = marker.copy(
            status = RestoreStatus.APPLYING,
            hadExistingLiveDb = hadExistingLiveDb,
        )
        fileStore.writeMarker(applyingMarker)

        // --- Rollback backup ---
        if (hadExistingLiveDb) {
            val rollbackError = dbSwapper.createRollbackBackup(liveDbFile, fileStore.rollbackDir)
            if (rollbackError != null) {
                failBeforeDbSwap(applyingMarker, rollbackError)
                return
            }
        }

        // --- Durable DataStore rollback snapshot ---
        val dataStoreSnapshotError = dataStoreReflector.prepareRollbackSnapshot(
            pendingDir = fileStore.pendingDir,
            includeCookies = marker.includeCookies,
        )
        if (dataStoreSnapshotError != null) {
            failBeforeDbSwap(applyingMarker, dataStoreSnapshotError)
            return
        }

        // --- Rollback snapshot 完成または元 DB なしを記録してから swap を開始 ---
        val rollbackReadyMarker = marker.copy(
            status = RestoreStatus.ROLLBACK_READY,
            hadExistingLiveDb = hadExistingLiveDb,
        )
        fileStore.writeMarker(rollbackReadyMarker)

        // --- DB replace ---
        val replaceError = dbSwapper.replaceDbFile(stagedDbFile, liveDbFile)
        if (replaceError != null) {
            rollbackAndFail(rollbackReadyMarker, replaceError, liveDbFile, hadExistingLiveDb)
            return
        }

        // --- Post-replace pre-migration validation ---
        val validationError = dbValidator.preValidate(liveDbFile, rollbackReadyMarker.databaseVersion)
        if (validationError != null) {
            rollbackAndFail(
                rollbackReadyMarker,
                "post-replace validation failed: $validationError",
                liveDbFile,
                hadExistingLiveDb,
            )
            return
        }

        // --- DataStore reflection ---
        val dbSwappedMarker = marker.copy(
            status = RestoreStatus.DB_SWAPPED,
            hadExistingLiveDb = hadExistingLiveDb,
            migrationAttemptStarted = false,
        )
        fileStore.writeMarker(dbSwappedMarker)

        val dataStoreError = dataStoreReflector.reflect(fileStore.pendingDir, marker.includeCookies)
        if (dataStoreError != null) {
            rollbackAndFail(dbSwappedMarker, dataStoreError, liveDbFile, hadExistingLiveDb)
            return
        }

        val migrationRequired = marker.databaseVersion < currentDbVersion
        fileStore.writeResult(
            success = true,
            message = "restore completed successfully",
            timestamp = nowProvider(),
            backupDatabaseVersion = marker.databaseVersion,
            currentDatabaseVersion = currentDbVersion,
            migrationRequired = migrationRequired,
            migrationCompleted = !migrationRequired,
        )
        // --- MIGRATION_PENDING: cleanup せず marker/rollback を保持 ---
        fileStore.writeMarker(
            marker.copy(
                status = RestoreStatus.MIGRATION_PENDING,
                hadExistingLiveDb = hadExistingLiveDb,
                migrationAttemptStarted = false,
            ),
        )
        logInfo("applyRestore: transitioned to MIGRATION_PENDING")
    }

    /** DBとDataStoreをrollbackして、完了時だけfailure cleanupを行う。 */
    private suspend fun rollbackAndFail(
        marker: PendingRestoreMarker,
        reason: String,
        liveDbFile: File,
        hadExistingLiveDb: Boolean,
    ) {
        logError("rollback: $reason")
        val rollbackErrors = mutableListOf<String>()

        // --- Database rollback ---
        if (hadExistingLiveDb) {
            try {
                if (!dbSwapper.restoreRollbackBackup(liveDbFile, fileStore.rollbackDir)) {
                    rollbackErrors += "database rollback failed"
                }
            } catch (e: Exception) {
                rollbackErrors += "database rollback failed: ${e.message}"
            }
        } else {
            dbSwapper.cleanupCorruptFreshInstallDb(liveDbFile)
        }

        // --- DataStore rollback ---
        dataStoreReflector.restoreRollbackSnapshot(fileStore.pendingDir)?.let { error ->
            rollbackErrors += error
        }

        if (rollbackErrors.isNotEmpty()) {
            val rollbackReason = "$reason; rollback incomplete: ${rollbackErrors.joinToString("; ")}"
            fileStore.writeMarker(
                marker.copy(
                    status = RestoreStatus.ROLLBACK_REQUIRED,
                    failureReason = rollbackReason,
                ),
            )
            fileStore.writeResult(
                success = false,
                message = rollbackReason,
                timestamp = nowProvider(),
                backupDatabaseVersion = marker.databaseVersion,
                currentDatabaseVersion = currentDbVersion,
                migrationRequired = marker.databaseVersion < currentDbVersion,
                migrationCompleted = false,
            )
            logWarn("rollback incomplete; preserving pending artifacts")
            return
        }

        fileStore.writeMarker(marker.copy(status = RestoreStatus.FAILED, failureReason = reason))
        fileStore.writeResult(
            success = false,
            message = reason,
            timestamp = nowProvider(),
            backupDatabaseVersion = marker.databaseVersion,
            currentDatabaseVersion = currentDbVersion,
            migrationRequired = marker.databaseVersion < currentDbVersion,
            migrationCompleted = false,
        )

        fileStore.cleanupPending()
    }

    /** DB置換前の準備失敗を記録し、変更されていないpendingをcleanupする。 */
    private fun failBeforeDbSwap(marker: PendingRestoreMarker, reason: String) {
        fileStore.writeMarker(marker.copy(status = RestoreStatus.FAILED, failureReason = reason))
        fileStore.writeResult(
            success = false,
            message = reason,
            timestamp = nowProvider(),
            backupDatabaseVersion = marker.databaseVersion,
            currentDatabaseVersion = currentDbVersion,
            migrationRequired = marker.databaseVersion < currentDbVersion,
            migrationCompleted = false,
        )
        fileStore.cleanupPending()
    }

    private fun logInfo(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: RuntimeException) {
            // JVM unit test の android.util.Log stub では例外になるため握りつぶす。
        }
    }

    private fun logWarn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: RuntimeException) {
            // JVM unit test の android.util.Log stub では例外になるため握りつぶす。
        }
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        try {
            if (throwable == null) {
                Log.e(TAG, message)
            } else {
                Log.e(TAG, message, throwable)
            }
        } catch (_: RuntimeException) {
            // JVM unit test の android.util.Log stub では例外になるため握りつぶす。
        }
    }

    /** 定数とテスト用 factory。 */
    companion object {
        private const val TAG = "PendingRestoreApplier"

        internal fun createForTest(
            context: Context,
            dbValidator: BackupDatabaseValidator,
            dataStoreReflector: PendingRestoreDataStoreReflector?,
            fileStore: PendingRestoreFileStore,
            dbSwapper: PendingRestoreDbSwapper,
            fileOperations: PendingRestoreFileOperations = RealPendingRestoreFileOperations(),
            nowProvider: () -> String,
            currentDbVersion: Int = RealBackupDatabaseValidator.Companion.EXPECTED_USER_VERSION,
        ): PendingRestoreApplier {
            return PendingRestoreApplier(
                context = context,
                dbValidator = dbValidator,
                dataStoreReflectorOverride = dataStoreReflector,
                fileStoreOverride = fileStore,
                dbSwapperOverride = dbSwapper,
                fileOperationsOverride = fileOperations,
                nowProvider = nowProvider,
                currentDbVersion = currentDbVersion,
            )
        }
    }
}
