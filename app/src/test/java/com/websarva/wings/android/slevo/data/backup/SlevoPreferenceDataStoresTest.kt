package com.websarva.wings.android.slevo.data.backup

import android.content.Context
import com.websarva.wings.android.slevo.data.datasource.local.impl.SlevoPreferenceDataStores
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * [SlevoPreferenceDataStores] の初回生成同期と applicationContext 使用方針を検証する。
 */
class SlevoPreferenceDataStoresTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun settings_returnsSingleInstanceUnderConcurrentFirstAccess() {
        SlevoPreferenceDataStores.resetForTest()
        val context = testContext(tempFolder.newFolder("settings-first"))
        assertConcurrentSingleton { SlevoPreferenceDataStores.settings(context) }
    }

    @Test
    fun tabs_returnsSingleInstanceUnderConcurrentFirstAccess() {
        SlevoPreferenceDataStores.resetForTest()
        val context = testContext(tempFolder.newFolder("tabs-first"))
        assertConcurrentSingleton { SlevoPreferenceDataStores.tabs(context) }
    }

    @Test
    fun cookies_returnsSingleInstanceUnderConcurrentFirstAccess() {
        SlevoPreferenceDataStores.resetForTest()
        val context = testContext(tempFolder.newFolder("cookies-first"))
        assertConcurrentSingleton { SlevoPreferenceDataStores.cookies(context) }
    }

    @Test
    fun allProviders_returnSingleInstanceForRepeatedCalls() {
        SlevoPreferenceDataStores.resetForTest()
        val context = testContext(tempFolder.newFolder("all-providers"))

        val settings1 = SlevoPreferenceDataStores.settings(context)
        val settings2 = SlevoPreferenceDataStores.settings(context)
        val tabs1 = SlevoPreferenceDataStores.tabs(context)
        val tabs2 = SlevoPreferenceDataStores.tabs(context)
        val cookies1 = SlevoPreferenceDataStores.cookies(context)
        val cookies2 = SlevoPreferenceDataStores.cookies(context)

        assertSame(settings1, settings2)
        assertSame(tabs1, tabs2)
        assertSame(cookies1, cookies2)

        SlevoPreferenceDataStores.resetForTest()
    }

    @Test
    fun source_usesApplicationContextAndSynchronizedInitialization() {
        val source = sourceFile(
            "app/src/main/java/com/websarva/wings/android/slevo/data/datasource/local/impl/SlevoPreferenceDataStores.kt",
        ).readText()

        assertTrue(source.contains("synchronized(this)"))
        assertTrue(source.contains("applicationContext ?: context"))
    }

    private fun testContext(filesDir: File): Context {
        filesDir.mkdirs()
        val appContext = mockk<Context>(relaxed = true)
        every { appContext.applicationContext } returns appContext
        every { appContext.filesDir } returns filesDir

        val outerContext = mockk<Context>(relaxed = true)
        every { outerContext.applicationContext } returns appContext
        every { outerContext.filesDir } returns File(filesDir.parentFile, "outer")
        return outerContext
    }

    private fun <T> assertConcurrentSingleton(block: () -> T) {
        val executor = Executors.newFixedThreadPool(8)
        try {
            val futures = (1..16).map { executor.submit(Callable { block() }) }
            val first = futures.first().get(5, TimeUnit.SECONDS)
            futures.drop(1).forEach { future ->
                assertSame(first, future.get(5, TimeUnit.SECONDS))
            }
        } finally {
            executor.shutdownNow()
            SlevoPreferenceDataStores.resetForTest()
        }
    }

    private fun sourceFile(projectRelativePath: String): File {
        val cwd = File(System.getProperty("user.dir"))
        val direct = File(cwd, projectRelativePath)
        if (direct.exists()) return direct

        val moduleRelative = File(cwd.parentFile ?: cwd, projectRelativePath)
        if (moduleRelative.exists()) return moduleRelative

        error("source file not found: $projectRelativePath")
    }
}
