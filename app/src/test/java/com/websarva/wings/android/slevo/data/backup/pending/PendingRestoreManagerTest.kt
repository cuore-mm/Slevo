package com.websarva.wings.android.slevo.data.backup.pending

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [PendingRestoreManager] の marker 管理と既存 pending 扱いを検証する。
 *
 * marker の状態遷移と JSON encode/decode をテストする。
 */
@OptIn(ExperimentalStdlibApi::class)
class PendingRestoreManagerTest {

    private val moshi: Moshi = Moshi.Builder().build()

    // --- Marker encode/decode ---

    @Test
    fun marker_encodeDecode_roundTrip() {
        val marker = PendingRestoreMarker(
            status = RestoreStatus.PREPARED,
            createdAt = "2026-01-01T00:00:00Z",
            includeCookies = true,
            databaseVersion = 9,
        )
        val adapter = moshi.adapter(PendingRestoreMarker::class.java)
        val json = adapter.toJson(marker)
        val decoded = adapter.fromJson(json)

        assertNotNull(decoded)
        assertEquals(RestoreStatus.PREPARED, decoded!!.status)
        assertEquals("2026-01-01T00:00:00Z", decoded.createdAt)
        assertEquals(true, decoded.includeCookies)
        assertEquals(9, decoded.databaseVersion)
        assertNull(decoded.failureReason)
    }

    @Test
    fun marker_encodeDecode_withFailureReason() {
        val marker = PendingRestoreMarker(
            status = RestoreStatus.FAILED,
            createdAt = "2026-01-01T00:00:00Z",
            includeCookies = false,
            databaseVersion = 9,
            failureReason = "integrity check failed",
        )
        val adapter = moshi.adapter(PendingRestoreMarker::class.java)
        val json = adapter.toJson(marker)
        val decoded = adapter.fromJson(json)

        assertNotNull(decoded)
        assertEquals(RestoreStatus.FAILED, decoded!!.status)
        assertEquals("integrity check failed", decoded.failureReason)
    }

    @Test
    fun marker_encodeDecode_preservesMigrationAttemptStartedBothValues() {
        val adapter = moshi.adapter(PendingRestoreMarker::class.java)

        listOf(false, true).forEach { attemptStarted ->
            val marker = PendingRestoreMarker(
                status = RestoreStatus.MIGRATION_PENDING,
                createdAt = "2026-01-01T00:00:00Z",
                includeCookies = false,
                databaseVersion = 8,
                migrationAttemptStarted = attemptStarted,
            )

            val decoded = requireNotNull(adapter.fromJson(adapter.toJson(marker)))

            assertEquals(attemptStarted, decoded.migrationAttemptStarted)
        }
    }

    // --- State transitions ---

    @Test
    fun status_transitions() {
        val prepared = PendingRestoreMarker(
            status = RestoreStatus.PREPARED,
            createdAt = "2026-01-01T00:00:00Z",
            includeCookies = false,
            databaseVersion = 9,
        )
        val applying = prepared.copy(status = RestoreStatus.APPLYING)
        val dbSwapped = applying.copy(status = RestoreStatus.DB_SWAPPED)
        val failed = dbSwapped.copy(
            status = RestoreStatus.FAILED,
            failureReason = "DataStore reflection failed",
        )

        assertEquals(RestoreStatus.PREPARED, prepared.status)
        assertEquals(RestoreStatus.APPLYING, applying.status)
        assertEquals(RestoreStatus.DB_SWAPPED, dbSwapped.status)
        assertEquals(RestoreStatus.FAILED, failed.status)
        assertEquals("DataStore reflection failed", failed.failureReason)
    }

    // --- Result file encode/decode ---

    @Test
    fun resultFile_encodeDecode_success() {
        val result = PendingRestoreResultFile(
            success = true,
            message = "restore completed successfully",
            timestamp = "2026-01-01T00:00:00Z",
        )
        val adapter = moshi.adapter(PendingRestoreResultFile::class.java)
        val json = adapter.toJson(result)
        val decoded = adapter.fromJson(json)

        assertNotNull(decoded)
        assertEquals(true, decoded!!.success)
        assertEquals("restore completed successfully", decoded.message)
    }

    @Test
    fun resultFile_encodeDecode_failure() {
        val result = PendingRestoreResultFile(
            success = false,
            message = "post-replace integrity check failed",
            timestamp = "2026-01-01T00:00:00Z",
        )
        val adapter = moshi.adapter(PendingRestoreResultFile::class.java)
        val json = adapter.toJson(result)
        val decoded = adapter.fromJson(json)

        assertNotNull(decoded)
        assertEquals(false, decoded!!.success)
        assertEquals("post-replace integrity check failed", decoded.message)
    }
}
