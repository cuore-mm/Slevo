package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import io.mockk.every
import io.mockk.mockk
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [RealPendingRestoreFileStore] の marker/result I/O と cleanup を検証する。
 */
@OptIn(ExperimentalStdlibApi::class)
class PendingRestoreFileStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val moshi = Moshi.Builder().build()
    private val resultAdapter = moshi.adapter<PendingRestoreResultFile>()

    @Test
    fun marker_roundTripsSuccessfully() {
        val store = createStore()
        val marker = PendingRestoreMarker(
            status = RestoreStatus.PREPARED,
            createdAt = "2026-07-03T00:00:00Z",
            includeCookies = true,
            databaseVersion = 9,
        )

        store.writeMarker(marker)

        assertEquals(marker, store.readMarker())
    }

    @Test
    fun malformedMarker_returnsNull() {
        val store = createStore()
        File(store.pendingDir, PendingRestoreManager.MARKER_FILENAME).apply {
            parentFile?.mkdirs()
            writeText("{not-json")
        }

        assertNull(store.readMarker())
    }

    @Test
    fun writeResult_writesSuccessAndFailureFiles() {
        val store = createStore()

        store.writeResult(true, "ok", "2026-07-03T00:00:00Z")
        store.writeResult(false, "ng", "2026-07-03T00:00:01Z")

        val resultFile = File(
            tempFolder.root,
            "files/${PendingRestoreManager.RESULT_DIR_NAME}/${PendingRestoreManager.RESULT_FILENAME}",
        )
        val result = resultAdapter.fromJson(resultFile.readText())
        assertNotNull(result)
        assertEquals(false, result!!.success)
        assertEquals("ng", result.message)
    }

    @Test
    fun cleanupPending_removesPendingDirectory() {
        val store = createStore()
        File(store.pendingDir, "database/slevo.db").apply {
            parentFile?.mkdirs()
            writeText("db")
        }

        store.cleanupPending()

        assertFalse(store.pendingDir.exists())
    }

    @Test
    fun cleanupResult_removesResultDirectory() {
        val store = createStore()
        store.writeResult(true, "ok", "2026-07-03T00:00:00Z")

        store.cleanupResult()

        val resultDir = File(tempFolder.root, "files/${PendingRestoreManager.RESULT_DIR_NAME}")
        assertFalse(resultDir.exists())
    }

    private fun createStore(): RealPendingRestoreFileStore {
        val filesDir = tempFolder.newFolder("files")
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
        return RealPendingRestoreFileStore(context, moshi)
    }
}
