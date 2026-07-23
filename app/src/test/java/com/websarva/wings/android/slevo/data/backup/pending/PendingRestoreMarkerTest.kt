package com.websarva.wings.android.slevo.data.backup.pending

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import com.websarva.wings.android.slevo.data.backup.BackupMoshiFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [PendingRestoreMarker] の Moshi serialization と互換性を検証する。
 */
@OptIn(ExperimentalStdlibApi::class)
class PendingRestoreMarkerTest {

    private val moshi: Moshi = BackupMoshiFactory.create()
    private val adapter = moshi.adapter<PendingRestoreMarker>()

    @Test
    fun serializeAndDeserialize_withHadExistingLiveDbTrue_roundTrips() {
        val marker = PendingRestoreMarker(
            status = RestoreStatus.ROLLBACK_READY,
            createdAt = "2026-07-03T00:00:00Z",
            includeCookies = true,
            databaseVersion = 9,
            hadExistingLiveDb = true,
        )

        val json = adapter.toJson(marker)
        val restored = requireNotNull(adapter.fromJson(json))

        assertEquals(RestoreStatus.ROLLBACK_READY, restored.status)
        assertEquals(true, restored.hadExistingLiveDb)
    }

    @Test
    fun serializeAndDeserialize_withHadExistingLiveDbFalse_roundTrips() {
        val marker = PendingRestoreMarker(
            status = RestoreStatus.APPLYING,
            createdAt = "2026-07-03T00:00:00Z",
            includeCookies = false,
            databaseVersion = 9,
            hadExistingLiveDb = false,
        )

        val json = adapter.toJson(marker)
        val restored = requireNotNull(adapter.fromJson(json))

        assertEquals(RestoreStatus.APPLYING, restored.status)
        assertEquals(false, restored.hadExistingLiveDb)
    }

    @Test
    fun serializeAndDeserialize_withMigrationAttemptStartedTrue_roundTrips() {
        val marker = PendingRestoreMarker(
            status = RestoreStatus.MIGRATION_PENDING,
            createdAt = "2026-07-03T00:00:00Z",
            includeCookies = false,
            databaseVersion = 8,
            migrationAttemptStarted = true,
        )

        val restored = requireNotNull(adapter.fromJson(adapter.toJson(marker)))

        assertEquals(true, restored.migrationAttemptStarted)
    }

    @Test
    fun deserialize_legacyJsonWithoutMigrationAttemptStarted_defaultsToFalse() {
        val legacyJson = """
            {
                "status": "MIGRATION_PENDING",
                "createdAt": "2026-07-03T00:00:00Z",
                "includeCookies": false,
                "databaseVersion": 8
            }
        """.trimIndent()

        val restored = requireNotNull(adapter.fromJson(legacyJson))

        assertEquals(false, restored.migrationAttemptStarted)
    }

    @Test
    fun deserialize_legacyJsonWithoutHadExistingLiveDb_parsesAsNull() {
        val legacyJson = """
            {
                "status": "APPLYING",
                "createdAt": "2026-07-03T00:00:00Z",
                "includeCookies": false,
                "databaseVersion": 9
            }
        """.trimIndent()

        val restored = requireNotNull(adapter.fromJson(legacyJson))

        assertEquals(RestoreStatus.APPLYING, restored.status)
        assertNull(restored.hadExistingLiveDb)
    }

    @Test
    fun serialize_rollbackReadyStatus_serializesCorrectly() {
        val marker = PendingRestoreMarker(
            status = RestoreStatus.ROLLBACK_READY,
            createdAt = "2026-07-03T00:00:00Z",
            includeCookies = false,
            databaseVersion = 9,
            hadExistingLiveDb = true,
        )

        val json = adapter.toJson(marker)

        assertEquals(
            RestoreStatus.ROLLBACK_READY,
            requireNotNull(adapter.fromJson(json)).status,
        )
    }
}
