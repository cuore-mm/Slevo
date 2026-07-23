package com.websarva.wings.android.slevo.data.backup.pending

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PendingRestoreApplier] の例外 path と I/O dispatcher 使用を source-level で検証する。
 */
class PendingRestoreApplierExceptionTest {

    /**
     * `runIfNeeded()` が例外記録も `Dispatchers.IO` 内で行う構造を持つことを確認する。
     */
    @Test
    fun runIfNeeded_recordsFailureInsideIoContext() {
        val source = pendingRestoreApplierSource()

        assertTrue(source.contains("withContext(Dispatchers.IO) {"))
        assertTrue(source.contains("recordStartupRestoreFailureOnIo(e)"))
        assertTrue(source.contains("private fun recordStartupRestoreFailureOnIo"))
    }

    /**
     * `SlevoApplication` 側の保険 catch が維持されていることを確認する。
     */
    @Test
    fun slevoApplication_keepsSafetyNetCatch() {
        val source = sourceFile(
            "app/src/main/java/com/websarva/wings/android/slevo/SlevoApplication.kt",
        ).readText()

        assertTrue(source.contains("runBlocking"))
        assertTrue(source.contains("PendingRestoreApplier(this@SlevoApplication).runIfNeeded()"))
        assertTrue(source.contains("android.util.Log.e(\"PendingRestore\""))
    }

    private fun pendingRestoreApplierSource(): String {
        return sourceFile(
            "app/src/main/java/com/websarva/wings/android/slevo/data/backup/pending/PendingRestoreApplier.kt",
        ).readText()
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
