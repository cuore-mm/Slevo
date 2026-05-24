package com.websarva.wings.android.slevo.core.log

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [LogFileManager] のファイル操作とローテーションを検証するテスト。
 */
class LogFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var logFileManager: LogFileManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.noBackupFilesDir } returns tempFolder.root
        every { context.cacheDir } returns tempFolder.newFolder("cache")
        logFileManager = LogFileManager(context)
    }

    @Test
    fun `logFile returns correct path`() {
        val logFile = logFileManager.logFile
        assertTrue(logFile.absolutePath.endsWith("logs/app.log"))
    }

    @Test
    fun `rotateIfNeeded does nothing when log is below limit`() {
        val logFile = logFileManager.logFile
        logFile.parentFile?.mkdirs()
        logFile.writeText("small log")

        logFileManager.rotateIfNeeded()

        assertTrue(logFile.exists())
        assertFalse(logFileManager.oldLogFile.exists())
    }

    @Test
    fun `rotateIfNeeded rotates when log exceeds limit`() {
        val logFile = logFileManager.logFile
        logFile.parentFile?.mkdirs()
        // 1MB + 1 byte
        val oversized = ByteArray(1024 * 1024 + 1)
        logFile.writeBytes(oversized)

        logFileManager.rotateIfNeeded()

        assertFalse(logFile.exists())
        assertTrue(logFileManager.oldLogFile.exists())
        assertEquals(1024L * 1024 + 1, logFileManager.oldLogFile.length())
    }

    @Test
    fun `createTempCopyForSharing returns null when log does not exist`() {
        val result = logFileManager.createTempCopyForSharing()
        assertNull(result)
    }

    @Test
    fun `createTempCopyForSharing returns null when log is empty`() {
        logFileManager.logFile.parentFile?.mkdirs()
        logFileManager.logFile.writeText("")

        val result = logFileManager.createTempCopyForSharing()
        assertNull(result)
    }

    @Test
    fun `createTempCopyForSharing creates temp file when log exists`() {
        logFileManager.logFile.parentFile?.mkdirs()
        logFileManager.logFile.writeText("test log content")

        val result = logFileManager.createTempCopyForSharing()

        assertNotNull(result)
        assertTrue(result!!.exists())
        assertEquals("test log content", result.readText())
    }

    @Test
    fun `clearOldSharedLogs removes existing shared files`() {
        logFileManager.logFile.parentFile?.mkdirs()
        logFileManager.logFile.writeText("test content for sharing")
        val tempFile = logFileManager.createTempCopyForSharing()
        assertNotNull(tempFile)
        assertTrue(tempFile!!.exists())

        logFileManager.clearOldSharedLogs()

        assertFalse(tempFile.exists())
    }
}
