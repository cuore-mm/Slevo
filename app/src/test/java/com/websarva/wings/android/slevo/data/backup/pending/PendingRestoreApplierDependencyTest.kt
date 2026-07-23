package com.websarva.wings.android.slevo.data.backup.pending

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * pending restore collaborator の依存制約を検証する。
 */
class PendingRestoreApplierDependencyTest {

    @Test
    fun publicFactoryOnlyRequiresContext() {
        val constructors = PendingRestoreApplier::class.java.constructors
        assertTrue(
            constructors.any { constructor ->
                constructor.parameterTypes.contentEquals(arrayOf(Context::class.java))
            },
        )
    }

    @Test
    fun sources_doNotReferenceForbiddenDatabaseDependencies() {
        val forbiddenImports = listOf("AppDatabase", "Dao", "Repository", "EntryPoint")

        listOf(
            sourceOf("data/backup/pending/PendingRestoreApplier.kt"),
            sourceOf("data/backup/pending/PendingRestoreFileStore.kt"),
            sourceOf("data/backup/pending/PendingRestoreDbSwapper.kt"),
        ).forEach { source ->
            val importLines = source.lineSequence().filter { it.trimStart().startsWith("import ") }.toList()
            forbiddenImports.forEach { token ->
                assertFalse(importLines.any { it.contains(token) })
            }
        }
    }

    @Test
    fun bytecode_doesNotContainForbiddenTypeNames() {
        val forbidden = listOf("AppDatabase", "EntryPoint")
        listOf(
            PendingRestoreApplier::class.java,
            RealPendingRestoreFileStore::class.java,
            RealPendingRestoreDbSwapper::class.java,
            RealPendingRestoreDataStoreReflector::class.java,
        ).forEach { clazz ->
            val classBytes = requireNotNull(
                clazz.classLoader?.getResourceAsStream(clazz.name.replace('.', '/') + ".class")?.readBytes(),
            )
            val byteString = classBytes.decodeToString()
            forbidden.forEach { token ->
                assertFalse("${clazz.simpleName} should not reference $token", byteString.contains(token))
            }
        }
    }

    private fun sourceOf(relativePath: String): String {
        val root = File(System.getProperty("user.dir"))
        val direct = File(root, "app/src/main/java/com/websarva/wings/android/slevo/$relativePath")
        val file = if (direct.exists()) {
            direct
        } else {
            File(root.parentFile ?: root, "app/src/main/java/com/websarva/wings/android/slevo/$relativePath")
        }
        return file.readText()
    }
}
