package com.websarva.wings.android.slevo.data.backup.pending

import android.util.AtomicFile
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * process death後のDataStore rollbackに使う、全対象storeのsnapshot file model。
 *
 * `cookies == null`はcookies非対象、空listは対象storeが空であることを表す。
 * `formatVersion`、各store内のkey一意性、typed valueの整合性を読み取り時に検証する。
 */
@JsonClass(generateAdapter = true)
internal data class PendingRestoreDataStoreSnapshot(
    val formatVersion: Int,
    val settings: List<PendingRestorePreferenceEntry>,
    val tabs: List<PendingRestorePreferenceEntry>,
    val cookies: List<PendingRestorePreferenceEntry>?,
) {
    /** Snapshot formatのversion。将来versionが変わった場合は自動復元しない。 */
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }

    /** snapshotのschemaと全entryを検証する。 */
    fun validate() {
        require(formatVersion == CURRENT_FORMAT_VERSION) {
            "unsupported DataStore snapshot format: $formatVersion"
        }
        settings.validateEntries("settings")
        tabs.validateEntries("tabs")
        cookies?.validateEntries("cookies")
    }
}

/**
 * DataStore Preferencesの1 key/valueをJSONへ変換したentry。
 *
 * typeに対応するnullable fieldだけがnon-nullでなければならず、StringSetは安定した順序で保存する。
 */
@JsonClass(generateAdapter = true)
internal data class PendingRestorePreferenceEntry(
    val key: String,
    val type: PendingRestorePreferenceType,
    val stringValue: String? = null,
    val booleanValue: Boolean? = null,
    val intValue: Int? = null,
    val longValue: Long? = null,
    val floatValue: Float? = null,
    val doubleValue: Double? = null,
    val stringSetValue: List<String>? = null,
)

/**
 * Preferencesがサポートする値型。
 */
@JsonClass(generateAdapter = false)
internal enum class PendingRestorePreferenceType {
    STRING,
    BOOLEAN,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
    STRING_SET,
}

/**
 * DataStore Preferencesをatomicなsnapshot fileへ保存・復元するstore。
 *
 * `.preferences_pb`を直接操作せず、DataStore APIで取得した値だけをapp-private pending directoryに保存する。
 * snapshot未作成は`null`、不正なsnapshotは例外としてcallerへ返す。
 */
@OptIn(ExperimentalStdlibApi::class)
internal class PendingRestoreDataStoreSnapshotStore(
    private val pendingDir: File,
    moshi: Moshi,
) {
    private val snapshotFile = File(pendingDir, PendingRestoreManager.DATASTORE_ROLLBACK_SNAPSHOT_FILENAME)
    private val backupFile = File("${snapshotFile.path}.bak")
    private val atomicFile = AtomicFile(snapshotFile)
    private val adapter = moshi.adapter<PendingRestoreDataStoreSnapshot>()

    /** DataStore snapshotを検証してatomicにpublishする。 */
    fun write(snapshot: PendingRestoreDataStoreWriter.DataStoreSnapshot) {
        val fileModel = snapshot.toFileModel()
        fileModel.validate()
        ensureParentDirectory()

        val output: FileOutputStream = atomicFile.startWrite()
        try {
            output.write(adapter.toJson(fileModel).toByteArray(StandardCharsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            try {
                atomicFile.failWrite(output)
            } catch (cleanupError: Exception) {
                error.addSuppressed(cleanupError)
            }
            throw error
        }
    }

    /** atomic snapshotを読み取り、検証済みのDataStore Preferencesへ変換する。 */
    fun read(): PendingRestoreDataStoreWriter.DataStoreSnapshot? {
        if (!snapshotFile.exists() && backupFile.exists() && !backupFile.renameTo(snapshotFile)) {
            throw IllegalStateException("failed to recover DataStore rollback snapshot: $snapshotFile")
        }

        val fileModel = try {
            val json = atomicFile.openRead().use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
            adapter.fromJson(json)
                ?: throw IllegalStateException("DataStore rollback snapshot is null")
        } catch (_: FileNotFoundException) {
            return null
        } catch (error: Exception) {
            throw IllegalStateException("failed to read DataStore rollback snapshot: $snapshotFile", error)
        }

        fileModel.validate()
        return fileModel.toDataStoreSnapshot()
    }

    private fun ensureParentDirectory() {
        if (!pendingDir.exists() && !pendingDir.mkdirs()) {
            throw IllegalStateException("failed to create pending directory: $pendingDir")
        }
        if (!pendingDir.isDirectory) {
            throw IllegalStateException("pending path is not a directory: $pendingDir")
        }
    }
}

/** Preferencesをdeterministicなsnapshot entry listへ変換する。 */
private fun Preferences.toSnapshotEntries(): List<PendingRestorePreferenceEntry> = asMap()
    .entries
    .sortedBy { it.key.name }
    .map { (key, value) ->
        when (value) {
            is String -> PendingRestorePreferenceEntry(
                key = key.name,
                type = PendingRestorePreferenceType.STRING,
                stringValue = value,
            )
            is Boolean -> PendingRestorePreferenceEntry(
                key = key.name,
                type = PendingRestorePreferenceType.BOOLEAN,
                booleanValue = value,
            )
            is Int -> PendingRestorePreferenceEntry(
                key = key.name,
                type = PendingRestorePreferenceType.INT,
                intValue = value,
            )
            is Long -> PendingRestorePreferenceEntry(
                key = key.name,
                type = PendingRestorePreferenceType.LONG,
                longValue = value,
            )
            is Float -> PendingRestorePreferenceEntry(
                key = key.name,
                type = PendingRestorePreferenceType.FLOAT,
                floatValue = value,
            )
            is Double -> PendingRestorePreferenceEntry(
                key = key.name,
                type = PendingRestorePreferenceType.DOUBLE,
                doubleValue = value,
            )
            is Set<*> -> {
                require(value.all { it is String }) {
                    "unsupported StringSet value for preference key: ${key.name}"
                }
                PendingRestorePreferenceEntry(
                    key = key.name,
                    type = PendingRestorePreferenceType.STRING_SET,
                    stringSetValue = value.filterIsInstance<String>().sorted(),
                )
            }
            else -> throw IllegalArgumentException(
                "unsupported DataStore value type for preference key: ${key.name}",
            )
        }
    }

/** DataStore snapshotをJSON file modelへ変換する。 */
private fun PendingRestoreDataStoreWriter.DataStoreSnapshot.toFileModel(): PendingRestoreDataStoreSnapshot =
    PendingRestoreDataStoreSnapshot(
        formatVersion = PendingRestoreDataStoreSnapshot.CURRENT_FORMAT_VERSION,
        settings = settings.toSnapshotEntries(),
        tabs = tabs.toSnapshotEntries(),
        cookies = cookies?.toSnapshotEntries(),
    )

/** Snapshot file modelをDataStore writerが使用するPreferences snapshotへ変換する。 */
private fun PendingRestoreDataStoreSnapshot.toDataStoreSnapshot(): PendingRestoreDataStoreWriter.DataStoreSnapshot =
    PendingRestoreDataStoreWriter.DataStoreSnapshot(
        settings = settings.toPreferences("settings"),
        tabs = tabs.toPreferences("tabs"),
        cookies = cookies?.toPreferences("cookies"),
    )

/** entry listを検証してPreferencesへfull overwrite可能な状態に変換する。 */
private fun List<PendingRestorePreferenceEntry>.toPreferences(storeName: String): Preferences {
    validateEntries(storeName)
    val preferences = mutablePreferencesOf()
    for (entry in this) {
        entry.writeTo(preferences)
    }
    return preferences.toPreferences()
}

/** store内entryのkey一意性とtyped value整合性を検証する。 */
private fun List<PendingRestorePreferenceEntry>.validateEntries(storeName: String) {
    require(all { it.key.isNotEmpty() }) { "$storeName snapshot contains an empty key" }
    require(map { it.key }.toSet().size == size) {
        "$storeName snapshot contains duplicate keys"
    }
    for (entry in this) {
        entry.validate()
    }
}

/** entryのtypeとnullable value fieldの整合性を検証する。 */
private fun PendingRestorePreferenceEntry.validate() {
    val valueCount = listOf(
        stringValue,
        booleanValue,
        intValue,
        longValue,
        floatValue,
        doubleValue,
        stringSetValue,
    ).count { it != null }
    require(valueCount == 1) { "snapshot entry has invalid value fields: $key" }
    when (type) {
        PendingRestorePreferenceType.STRING -> require(stringValue != null)
        PendingRestorePreferenceType.BOOLEAN -> require(booleanValue != null)
        PendingRestorePreferenceType.INT -> require(intValue != null)
        PendingRestorePreferenceType.LONG -> require(longValue != null)
        PendingRestorePreferenceType.FLOAT -> require(floatValue != null)
        PendingRestorePreferenceType.DOUBLE -> require(doubleValue != null)
        PendingRestorePreferenceType.STRING_SET -> require(stringSetValue != null)
    }
}

/** validated entryをMutablePreferencesへ書き込む。 */
private fun PendingRestorePreferenceEntry.writeTo(target: MutablePreferences) {
    when (type) {
        PendingRestorePreferenceType.STRING -> target[stringPreferencesKey(key)] = requireNotNull(stringValue)
        PendingRestorePreferenceType.BOOLEAN -> target[booleanPreferencesKey(key)] = requireNotNull(booleanValue)
        PendingRestorePreferenceType.INT -> target[intPreferencesKey(key)] = requireNotNull(intValue)
        PendingRestorePreferenceType.LONG -> target[longPreferencesKey(key)] = requireNotNull(longValue)
        PendingRestorePreferenceType.FLOAT -> target[floatPreferencesKey(key)] = requireNotNull(floatValue)
        PendingRestorePreferenceType.DOUBLE -> target[doublePreferencesKey(key)] = requireNotNull(doubleValue)
        PendingRestorePreferenceType.STRING_SET -> {
            target[stringSetPreferencesKey(key)] = requireNotNull(stringSetValue).toSet()
        }
    }
}
