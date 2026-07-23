package com.websarva.wings.android.slevo.data.backup.restore

import android.database.sqlite.SQLiteDatabase
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.datasource.local.DATABASE_VERSION
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * バックアップ対象 DB ファイルの schema compatibility を検証するインターフェース。
 *
 * [BackupReader] から呼び出し、テスト時に fake で置き換え可能にする。
 * 検証は SQLite ファイルを開いて read-only で行い、DB への書き込みは行わない。
 *
 * [validate] は Room migration 後の厳密検証（post-migration validation）に使う。
 * [preValidate] は migration 前の事前検証（pre-migration validation）に使う。
 * pre-migration では現在 schema の Room identity hash と全必須 table 一致を
 * 要求しないが、version-aware な application table 存在確認と、
 * current version DB の strict validation は行う。
 */
interface BackupDatabaseValidator {

    /**
     * Room migration 後の厳密検証（post-migration validation）。
     *
     * `PRAGMA integrity_check = ok`、`PRAGMA user_version == current`、
     * 現在 Room identity hash 一致、全必須 application table 存在を確認する。
     *
     * @param dbFile 検証対象の SQLite DB ファイル。
     * @return 検証成功時 null、失敗時エラーメッセージ。
     */
    fun validate(dbFile: File): String?

    /**
     * Room migration 前の事前検証（pre-migration validation）。
     *
     * `PRAGMA integrity_check = ok`、`PRAGMA user_version` が対応範囲内、
     * `PRAGMA user_version == manifest.databaseVersion`、migration path 存在を確認する。
     * current version の DB は strict validation（identity hash + required table check）、
     * old version の DB は version-aware expected application table set の存在確認も行う。
     *
     * @param dbFile 検証対象の SQLite DB ファイル。
     * @param manifestDatabaseVersion manifest.json の `databaseVersion`。
     * @return 検証成功時 null、失敗時エラーメッセージ。
     */
    fun preValidate(dbFile: File, manifestDatabaseVersion: Int): String?
}

/**
 * [BackupDatabaseValidator] の本番実装。
 *
 * [validate] (post-migration) は以下の検証を行う:
 * - `PRAGMA integrity_check` が `ok` を返すこと。
 * - `PRAGMA user_version` が [EXPECTED_USER_VERSION] であること。
 * - `room_master_table` に期待する identity hash が登録されていること。
 * - すべての必須 application table が存在すること。
 *
 * [preValidate] (pre-migration) は以下の検証を行う:
 * - `PRAGMA integrity_check` が `ok` を返すこと。
 * - `PRAGMA user_version` が manifest の `databaseVersion` と一致すること。
 * - `PRAGMA user_version` が対応範囲内であること。
 * - migration path が存在すること。
 * - current version の DB は strict validation（identity hash + required table check）を通すこと。
 * - old version の DB は version-aware expected application table set の存在確認を通すこと。
 */
@Singleton
class RealBackupDatabaseValidator @Inject constructor() : BackupDatabaseValidator {

    override fun preValidate(dbFile: File, manifestDatabaseVersion: Int): String? {
        val db: SQLiteDatabase
        try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
        } catch (e: Exception) {
            return "failed to open database for preValidate: ${e.message}"
        }
        try {
            // --- Integrity check ---
            val integrityResult = db.rawQuery("PRAGMA integrity_check", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            if (integrityResult != "ok") {
                return "integrity check failed: $integrityResult"
            }

            // --- User version ---
            val userVersion = db.rawQuery("PRAGMA user_version", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else -1
            }

            // --- manifest との version 一致 ---
            if (userVersion != manifestDatabaseVersion) {
                return "manifest/db version mismatch:" +
                    " manifest=$manifestDatabaseVersion, db=$userVersion"
            }

            // --- User version 範囲 ---
            if (userVersion > AppDatabase.CURRENT_DATABASE_VERSION) {
                return "db user_version is in the future:" +
                    " version=$userVersion, current=${AppDatabase.CURRENT_DATABASE_VERSION}"
            }
            if (userVersion < AppDatabase.MINIMUM_RESTORABLE_DATABASE_VERSION) {
                return "db user_version is too old:" +
                    " version=$userVersion, minimum=${AppDatabase.MINIMUM_RESTORABLE_DATABASE_VERSION}"
            }

            // --- Migration path ---
            if (!AppDatabase.hasMigrationPathForRestore(userVersion)) {
                return "no migration path from db version $userVersion" +
                    " to ${AppDatabase.CURRENT_DATABASE_VERSION}"
            }

            // --- Schema sanity check ---
            if (userVersion == AppDatabase.CURRENT_DATABASE_VERSION) {
                // current version は strict validation を通す。
                return validateExistingDb(db)
            } else {
                // old version は version-aware expected application table set を確認する。
                return validateHistoricalTables(db, userVersion)
            }
        } finally {
            db.close()
        }
    }

    /**
     * 既に open 済みの [SQLiteDatabase] に対して current version の
     * strict validation を実行する。[validate] と同等の検証を
     * preValidate 内から呼ぶための internal helper。
     *
     * caller 側で DB close を保証すること。
     */
    private fun validateExistingDb(db: SQLiteDatabase): String? {
        // --- Room identity hash ---
        val hashCursor = db.rawQuery(
            "SELECT identity_hash FROM room_master_table WHERE id = 42",
            null,
        )
        val identityHash = hashCursor.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        if (identityHash != EXPECTED_IDENTITY_HASH) {
            return "identity hash mismatch: expected=$EXPECTED_IDENTITY_HASH, actual=$identityHash"
        }

        // --- Required application tables ---
        for (table in REQUIRED_TABLES) {
            val tableCursor = db.rawQuery(
                "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(table),
            )
            val exists = tableCursor.use { c ->
                if (c.moveToFirst()) c.getInt(0) > 0 else false
            }
            if (!exists) {
                return "missing required table: $table"
            }
        }

        return null
    }

    /**
     * 既に open 済みの [SQLiteDatabase] に対して、指定 version の
     * expected application table set が存在するかを確認する。
     *
     * [EXPECTED_TABLES_BY_VERSION] に未定義の version は拒否し、
     * expected table が 1 つでも不足する場合は error を返す。
     *
     * caller 側で DB close を保証すること。
     */
    private fun validateHistoricalTables(
        db: SQLiteDatabase,
        version: Int,
    ): String? {
        val expectedTables = EXPECTED_TABLES_BY_VERSION[version]
            ?: return "no expected table set defined for db version $version"

        for (table in expectedTables) {
            val tableCursor = db.rawQuery(
                "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(table),
            )
            val exists = tableCursor.use { c ->
                if (c.moveToFirst()) c.getInt(0) > 0 else false
            }
            if (!exists) {
                return "missing expected table for version $version: $table"
            }
        }

        return null
    }

    override fun validate(dbFile: File): String? {
        val db: SQLiteDatabase
        try {
            db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
        } catch (e: Exception) {
            return "failed to open database for validate: ${e.message}"
        }
        try {
            // --- Integrity check ---
            val integrityResult = db.rawQuery("PRAGMA integrity_check", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            if (integrityResult != "ok") {
                return "integrity check failed: $integrityResult"
            }

            // --- User version ---
            val userVersion = db.rawQuery("PRAGMA user_version", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else -1
            }
            if (userVersion != EXPECTED_USER_VERSION) {
                return "user_version mismatch: expected=$EXPECTED_USER_VERSION, actual=$userVersion"
            }

            // --- Room identity hash ---
            val hashCursor = db.rawQuery(
                "SELECT identity_hash FROM room_master_table WHERE id = 42",
                null,
            )
            val identityHash = hashCursor.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            if (identityHash != EXPECTED_IDENTITY_HASH) {
                return "identity hash mismatch: expected=$EXPECTED_IDENTITY_HASH, actual=$identityHash"
            }

            // --- Required application tables ---
            for (table in REQUIRED_TABLES) {
                val tableCursor = db.rawQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                    arrayOf(table),
                )
                val exists = tableCursor.use { c ->
                    if (c.moveToFirst()) c.getInt(0) > 0 else false
                }
                if (!exists) {
                    return "missing required table: $table"
                }
            }

            return null
        } finally {
            db.close()
        }
    }

    /** 定数。 */
    companion object {
        /** Room DB の期待する `PRAGMA user_version`。 */
        const val EXPECTED_USER_VERSION = DATABASE_VERSION

        /** Room の期待する `room_master_table` の identity hash。 */
        const val EXPECTED_IDENTITY_HASH = "f87f9edff16faf278567dbb60497a466"

        /** Room DB に必須の application table 一覧（current version 用）。 */
        val REQUIRED_TABLES = listOf(
            "services",
            "categories",
            "boards",
            "board_category_cross_ref",
            "groups",
            "bookmark_boards",
            "bookmark_threads",
            "thread_bookmark_groups",
            "open_board_tabs",
            "open_thread_tabs",
            "thread_histories",
            "thread_history_accesses",
            "ng_entries",
            "thread_summaries",
            "board_visits",
            "board_fetch_meta",
            "post_histories",
            "post_identity_histories",
            "post_last_identities",
            "thread_states",
        )

        /**
         * Room DB version ごとの expected application table set。
         *
         * `app/schemas/com.websarva.wings.android.slevo.data.datasource.local.AppDatabase/`
         * の exported Room schema JSON (v2-v9) を source of truth とする。
         * `room_master_table` と `android_metadata` は含めない。
         * v1 は exported schema が存在しないため定義しない。
         *
         * 追加遷移:
         * - v4: `post_identity_histories` 追加
         * - v5: `post_last_identities` 追加
         * - v6: `thread_states` 追加（v6-v9 は同一）
         */
        private val EXPECTED_TABLES_BY_VERSION: Map<Int, Set<String>> = mapOf(
            2 to setOf(
                "services", "categories", "boards", "board_category_cross_ref",
                "groups", "bookmark_boards", "bookmark_threads", "thread_bookmark_groups",
                "open_board_tabs", "open_thread_tabs", "thread_histories",
                "thread_history_accesses", "ng_entries", "thread_summaries",
                "board_visits", "board_fetch_meta", "post_histories",
            ),
            3 to setOf(
                "services", "categories", "boards", "board_category_cross_ref",
                "groups", "bookmark_boards", "bookmark_threads", "thread_bookmark_groups",
                "open_board_tabs", "open_thread_tabs", "thread_histories",
                "thread_history_accesses", "ng_entries", "thread_summaries",
                "board_visits", "board_fetch_meta", "post_histories",
            ),
            4 to setOf(
                "services", "categories", "boards", "board_category_cross_ref",
                "groups", "bookmark_boards", "bookmark_threads", "thread_bookmark_groups",
                "open_board_tabs", "open_thread_tabs", "thread_histories",
                "thread_history_accesses", "ng_entries", "thread_summaries",
                "board_visits", "board_fetch_meta", "post_histories",
                "post_identity_histories",
            ),
            5 to setOf(
                "services", "categories", "boards", "board_category_cross_ref",
                "groups", "bookmark_boards", "bookmark_threads", "thread_bookmark_groups",
                "open_board_tabs", "open_thread_tabs", "thread_histories",
                "thread_history_accesses", "ng_entries", "thread_summaries",
                "board_visits", "board_fetch_meta", "post_histories",
                "post_identity_histories",
                "post_last_identities",
            ),
            6 to setOf(
                "services", "categories", "boards", "board_category_cross_ref",
                "groups", "bookmark_boards", "bookmark_threads", "thread_bookmark_groups",
                "open_board_tabs", "open_thread_tabs", "thread_histories",
                "thread_history_accesses", "ng_entries", "thread_summaries",
                "board_visits", "board_fetch_meta", "post_histories",
                "post_identity_histories", "post_last_identities",
                "thread_states",
            ),
            7 to setOf(
                "services", "categories", "boards", "board_category_cross_ref",
                "groups", "bookmark_boards", "bookmark_threads", "thread_bookmark_groups",
                "open_board_tabs", "open_thread_tabs", "thread_histories",
                "thread_history_accesses", "ng_entries", "thread_summaries",
                "board_visits", "board_fetch_meta", "post_histories",
                "post_identity_histories", "post_last_identities",
                "thread_states",
            ),
            8 to setOf(
                "services", "categories", "boards", "board_category_cross_ref",
                "groups", "bookmark_boards", "bookmark_threads", "thread_bookmark_groups",
                "open_board_tabs", "open_thread_tabs", "thread_histories",
                "thread_history_accesses", "ng_entries", "thread_summaries",
                "board_visits", "board_fetch_meta", "post_histories",
                "post_identity_histories", "post_last_identities",
                "thread_states",
            ),
            9 to setOf(
                "services", "categories", "boards", "board_category_cross_ref",
                "groups", "bookmark_boards", "bookmark_threads", "thread_bookmark_groups",
                "open_board_tabs", "open_thread_tabs", "thread_histories",
                "thread_history_accesses", "ng_entries", "thread_summaries",
                "board_visits", "board_fetch_meta", "post_histories",
                "post_identity_histories", "post_last_identities",
                "thread_states",
            ),
        )
    }
}
