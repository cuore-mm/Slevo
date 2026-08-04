package com.websarva.wings.android.slevo.data.datasource.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `ALL_REGISTERED_MIGRATIONS` と `hasMigrationPathForRestore` の一貫性テスト。 */
class AppDatabaseMigrationTest {

    // --- 1. ALL_REGISTERED_MIGRATIONS と個別 migration の一致 ---

    @Test
    fun allRegisteredMigrations_containsExpectedMigrations() {
        val expected = listOf(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
        )
        assertEquals(expected.size, AppDatabase.ALL_REGISTERED_MIGRATIONS.size)
        expected.zip(AppDatabase.ALL_REGISTERED_MIGRATIONS).forEach { (exp, actual) ->
            assertEquals(
                "migration (${exp.startVersion}->${exp.endVersion}) should match",
                exp.startVersion, actual.startVersion
            )
            assertEquals(
                "migration (${exp.startVersion}->${exp.endVersion}) should match",
                exp.endVersion, actual.endVersion
            )
        }
    }

    @Test
    fun allRegisteredMigrations_sameInstanceAsCompanion() {
        val names = listOf(
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
        )
        names.zip(AppDatabase.ALL_REGISTERED_MIGRATIONS).forEach { (companionVal, listVal) ->
            assertEquals(
                "ALL_REGISTERED_MIGRATIONS は companion の個別 val と同じ instance を参照すること",
                companionVal, listVal,
            )
        }
    }

    // --- 2. migration chain の連続性 ---

    @Test
    fun migrationChainIsContinuousFromMinimumToCurrent() {
        val edges: Map<Int, Int> = AppDatabase.ALL_REGISTERED_MIGRATIONS
            .associate { it.startVersion to it.endVersion }

        var current = AppDatabase.MINIMUM_RESTORABLE_DATABASE_VERSION
        while (current < AppDatabase.CURRENT_DATABASE_VERSION) {
            val next = edges[current]
                ?: error(
                    "migration chain に欠けがあります。version $current から ${
                        current + 1
                    } への migration が存在しません。ALL_REGISTERED_MIGRATIONS を確認してください。"
                )
            assertEquals(
                "migration ($current->$next) は連続していること (next = current + 1)",
                current + 1, next,
            )
            current = next
        }
        assertEquals(
            "migration chain の最終 version は CURRENT_DATABASE_VERSION と一致すること",
            AppDatabase.CURRENT_DATABASE_VERSION, current,
        )
    }

    @Test
    fun everyRegisteredMigrationIsInExpectedRange() {
        for (m in AppDatabase.ALL_REGISTERED_MIGRATIONS) {
            // startVersion は最低 1 以上（lightweight backup restore の最小バージョンとは別）。
            // v1→v2 は通常 Room migration 用に残すが、restore では受け付けない。
            assertTrue(
                "migration startVersion (${m.startVersion}) >= 1",
                m.startVersion >= 1,
            )
            assertTrue(
                "migration endVersion (${m.endVersion}) <= ${
                    AppDatabase.CURRENT_DATABASE_VERSION
                }",
                m.endVersion <= AppDatabase.CURRENT_DATABASE_VERSION,
            )
        }
    }

    // --- 3. hasMigrationPathForRestore の正しさ ---

    @Test
    fun hasMigrationPathForRestore_currentVersion_returnsTrue() {
        assertTrue(
            "current version から current version は path あり",
            AppDatabase.hasMigrationPathForRestore(AppDatabase.CURRENT_DATABASE_VERSION),
        )
    }

    @Test
    fun hasMigrationPathForRestore_minimumVersion_returnsTrue() {
        assertTrue(
            "minimum version から current version は path あり",
            AppDatabase.hasMigrationPathForRestore(AppDatabase.MINIMUM_RESTORABLE_DATABASE_VERSION),
        )
    }

    @Test
    fun hasMigrationPathForRestore_supportedOldVersions_returnsTrue() {
        for (version in AppDatabase.MINIMUM_RESTORABLE_DATABASE_VERSION until AppDatabase.CURRENT_DATABASE_VERSION) {
            assertTrue(
                "version $version から ${AppDatabase.CURRENT_DATABASE_VERSION} は path あり",
                AppDatabase.hasMigrationPathForRestore(version),
            )
        }
    }

    @Test
    fun hasMigrationPathForRestore_belowMinimum_returnsFalse() {
        assertFalse(
            "minimum より小さい version は path なし",
            AppDatabase.hasMigrationPathForRestore(AppDatabase.MINIMUM_RESTORABLE_DATABASE_VERSION - 1),
        )
    }

    @Test
    fun hasMigrationPathForRestore_futureVersion_returnsFalse() {
        assertFalse(
            "未来 version は path なし",
            AppDatabase.hasMigrationPathForRestore(AppDatabase.CURRENT_DATABASE_VERSION + 1),
        )
    }

    @Test
    fun hasMigrationPathForRestore_negativeVersion_returnsFalse() {
        assertFalse(
            "負の version は path なし",
            AppDatabase.hasMigrationPathForRestore(-1),
        )
    }
}
