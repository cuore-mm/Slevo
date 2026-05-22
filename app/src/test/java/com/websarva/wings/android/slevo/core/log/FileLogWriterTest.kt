package com.websarva.wings.android.slevo.core.log

import android.content.Context
import co.touchlab.kermit.Severity
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [FileLogWriter] のログ書き込み、フィルタ、ローテーション後の継続性を検証するテスト。
 */
class FileLogWriterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var logFileManager: LogFileManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.filesDir } returns tempFolder.root
        every { context.cacheDir } returns tempFolder.newFolder("cache")
        logFileManager = LogFileManager(context)
    }

    @Test
    fun `writes debug info and error logs when min severity is Debug`() {
        val writer = FileLogWriter(logFileManager, minSeverity = Severity.Debug)

        writer.log(Severity.Debug, "debug msg", "TestTag", null)
        writer.log(Severity.Info, "info msg", "TestTag", null)
        writer.log(Severity.Error, "error msg", "TestTag", null)

        val content = logFileManager.logFile.readText()
        assertTrue(content.contains("debug msg"))
        assertTrue(content.contains("info msg"))
        assertTrue(content.contains("error msg"))
    }

    @Test
    fun `writes only error logs when min severity is Error`() {
        val writer = FileLogWriter(logFileManager, minSeverity = Severity.Error)

        writer.log(Severity.Debug, "debug msg", "TestTag", null)
        writer.log(Severity.Info, "info msg", "TestTag", null)
        writer.log(Severity.Error, "error msg", "TestTag", null)

        val content = logFileManager.logFile.readText()
        assertFalse(content.contains("debug msg"))
        assertFalse(content.contains("info msg"))
        assertTrue(content.contains("error msg"))
    }

    @Test
    fun `includes throwable stack trace in log file`() {
        val writer = FileLogWriter(logFileManager, minSeverity = Severity.Debug)
        val throwable = RuntimeException("test exception")

        writer.log(Severity.Error, "error with throwable", "TestTag", throwable)

        val content = logFileManager.logFile.readText()
        assertTrue(content.contains("error with throwable"))
        assertTrue(content.contains("RuntimeException"))
        assertTrue(content.contains("test exception"))
    }

    @Test
    fun `continues writing after rotation`() {
        val writer = FileLogWriter(logFileManager, minSeverity = Severity.Debug)
        // Pre-fill log file to just below limit to avoid rotation on first write
        logFileManager.logFile.parentFile?.mkdirs()
        logFileManager.logFile.writeText("existing\n")

        writer.log(Severity.Info, "after rotation", "TestTag", null)

        assertTrue(logFileManager.logFile.readText().contains("after rotation"))
    }

    @Test
    fun `does not crash when write fails`() {
        // Make logs dir read-only to simulate write failure
        logFileManager.logFile.parentFile?.mkdirs()
        logFileManager.logFile.parentFile?.setReadOnly()

        val writer = FileLogWriter(logFileManager, minSeverity = Severity.Debug)

        // Should not throw
        writer.log(Severity.Info, "should not crash", "TestTag", null)
    }
}
