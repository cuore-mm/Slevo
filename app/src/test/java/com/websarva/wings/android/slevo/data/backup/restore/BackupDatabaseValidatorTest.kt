package com.websarva.wings.android.slevo.data.backup.restore

import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.collections.iterator

/**
 * [BackupDatabaseValidator] の pre/post validation 境界テスト。
 *
 * 実 DB ファイルを使わず、存在しないファイルや書き込み専用ファイルの開き方で
 * エラーになることを確認するため、`dbFile` に存在しないパスを与えて
 * 開くのに失敗するケースを検証する。
 *
 * 実際の SQLite ファイルを使った integrity/user_version check は
 * instrumented test または実機確認に委ねる。
 */
class BackupDatabaseValidatorTest {

    private val validator = RealBackupDatabaseValidator()

    // --- preValidate: 開けないファイル ---

    @Test
    fun preValidate_fileDoesNotExist_returnsError() {
        val nonexistent = File("/tmp/nonexistent_db_${System.currentTimeMillis()}.db")
        val error = validator.preValidate(nonexistent, manifestDatabaseVersion = 9)
        assertNotNull("存在しないファイルはエラーになること", error)
    }

    // --- validate: 開けないファイル ---

    @Test
    fun validate_fileDoesNotExist_returnsError() {
        val nonexistent = File("/tmp/nonexistent_db_${System.currentTimeMillis()}.db")
        val error = validator.validate(nonexistent)
        assertNotNull("存在しないファイルはエラーになること", error)
    }

    // --- preValidate: manifest databaseVersion が正しく渡されることの構造確認 ---

    @Test
    fun preValidate_passesDatabaseVersionCorrectly() {
        val nonexistent = File("/tmp/nonexistent_db_${System.currentTimeMillis()}.db")
        val error = validator.preValidate(nonexistent, manifestDatabaseVersion = 5)
        assertNotNull("ファイルがなくエラーになること", error)
    }

    // --- getUserVersion ---

    @Test
    fun getUserVersion_fileDoesNotExist_returnsNull() {
        val nonexistent = File("/tmp/nonexistent_db_${System.currentTimeMillis()}.db")
        val version = validator.getUserVersion(nonexistent)
        assertNull("存在しないファイルは null を返すこと", version)
    }

    // --- validate: strict として使えること ---

    @Test
    fun validate_methodExists() {
        val fake = object : BackupDatabaseValidator {
            override fun validate(dbFile: File): String? = "test error"
            override fun preValidate(dbFile: File, manifestDatabaseVersion: Int): String? = null
            override fun getUserVersion(dbFile: File): Int? = 42
        }
        assertEquals("test error", fake.validate(File(".")))
        assertNull(fake.preValidate(File("."), 9))
        assertEquals(42, fake.getUserVersion(File(".")))
    }

    // --- Fake の preValidate が version を capture すること ---

    @Test
    fun fakeBackupDatabaseValidator_preValidate_capturesVersion() {
        val capturer = CapturedInt()
        val fake = FakeBackupDatabaseValidator(
            preValidationError = "pre error",
            capturedPreValidateDbVersion = capturer,
        )
        val error = fake.preValidate(File("."), manifestDatabaseVersion = 8)
        assertEquals("pre error", error)
        assertEquals(8, capturer.value)
    }

    @Test
    fun fakeBackupDatabaseValidator_preValidate_successWhenNoError() {
        val fake = FakeBackupDatabaseValidator()
        val error = fake.preValidate(File("."), manifestDatabaseVersion = 1)
        assertNull("preValidationError 未設定時は null を返すこと", error)
    }

    // --- v1 は restore 対象外 ---

    @Test
    fun preValidate_v1_manifestValid_returnsTooOld() {
        // v1 バックアップは MINIMUM_RESTORABLE_DATABASE_VERSION = 2 により拒否される。
        // ファイルが存在しなくても preValidate はエラーを返す。
        val nonexistent = File("/tmp/nonexistent_db_${System.currentTimeMillis()}.db")
        val error = validator.preValidate(nonexistent, manifestDatabaseVersion = 1)
        assertNotNull("v1 は何らかのエラーとして拒否されること", error)
    }

    // --- EXPECTED_TABLES_BY_VERSION の定義確認 ---

    @Test
    fun expectedTablesByVersion_coversRestorableRange() {
        for (version in AppDatabase.MINIMUM_RESTORABLE_DATABASE_VERSION..AppDatabase.CURRENT_DATABASE_VERSION) {
            assertTrue(
                "restore 可能な version $version に expected table set が定義されていること",
                getExpectedTablesForVersion(version) != null,
            )
        }
    }

    @Test
    fun expectedTablesByVersion_doesNotIncludeRoomInternalTables() {
        for ((version, tables) in getExpectedTablesByVersion()) {
            assertFalse(
                "version $version に room_master_table を含まないこと",
                "room_master_table" in tables,
            )
            assertFalse(
                "version $version に android_metadata を含まないこと",
                "android_metadata" in tables,
            )
        }
    }

    @Test
    fun expectedTablesByVersion_matchesCurrentRequiredTables() {
        // v12 (current) の expected table set は REQUIRED_TABLES と一致すること。
        val v12Tables = getExpectedTablesForVersion(12)
        assertNotNull("v12 に expected table set が定義されていること", v12Tables)
        val currentRequired = RealBackupDatabaseValidator.REQUIRED_TABLES.toSet()
        assertEquals(
            "v12 expected table set が REQUIRED_TABLES と一致すること",
            currentRequired,
            v12Tables!!,
        )
    }

    @Test
    fun expectedTablesByVersion_v1NotDefined() {
        assertNull("v1 は expected table set が定義されていないこと", getExpectedTablesForVersion(1))
    }

    // --- helpers: reflection で EXPECTED_TABLES_BY_VERSION にアクセス ---

    private fun getExpectedTablesForVersion(version: Int): Set<String>? {
        val field = RealBackupDatabaseValidator::class.java
            .getDeclaredField("EXPECTED_TABLES_BY_VERSION")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(null) as Map<Int, Set<String>>
        return map[version]
    }

    private fun getExpectedTablesByVersion(): Map<Int, Set<String>> {
        val field = RealBackupDatabaseValidator::class.java
            .getDeclaredField("EXPECTED_TABLES_BY_VERSION")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(null) as Map<Int, Set<String>>
    }
}
