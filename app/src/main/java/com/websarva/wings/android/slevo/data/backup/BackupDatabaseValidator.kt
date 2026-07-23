package com.websarva.wings.android.slevo.data.backup

import android.database.sqlite.SQLiteDatabase
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * バックアップ対象 DB ファイルの schema compatibility を検を検証するインターフェース。
 *
 * [BackupReader] から呼び出し、テスト時に fake で置き換え可能にする。
 * 検証は SQLite ファイルを開いて read-only で行い、DB への書き込みは行わない。
 */
interface BackupDatabaseValidator {

    /**
     * DB ファイルの整合性と schema compatibility を検証する。
     *
     * @param dbFile 検証対象の SQLite DB ファイル。
     * @return 検証成功時 null、失敗時エラーメッセージ。
     */
    fun validate(dbFile: File): String?
}

/**
 * [BackupDatabaseValidator] の本番実装。
 *
 * 以下の検証を行う:
 * - `PRAGMA integrity_check` が `ok` を返すこと。
 * - `PRAGMA user_version` が [EXPECTED_USER_VERSION] であること。
 * - `room_master_table` に期待する identity hash が登録されていること。
 * - すべての必須 application table が存在すること。
 */
@Singleton
class RealBackupDatabaseValidator @Inject constructor() : BackupDatabaseValidator {

    override fun validate(dbFile: File): String? {
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
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

    companion object {
        /** Room DB の期待する `PRAGMA user_version`。 */
        const val EXPECTED_USER_VERSION = 9

        /** Room の期待する `room_master_table` の identity hash。 */
        const val EXPECTED_IDENTITY_HASH = "f87f9edff16faf278567dbb60497a466"

        /** Room DB に必須の application table 一覧。 */
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
    }
}
