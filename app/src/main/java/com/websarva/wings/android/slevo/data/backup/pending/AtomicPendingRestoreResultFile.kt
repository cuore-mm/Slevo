package com.websarva.wings.android.slevo.data.backup.pending

import android.util.AtomicFile
import com.squareup.moshi.JsonAdapter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * pending restore result JSON を atomic に publish する file abstraction。
 *
 * [AtomicFile] の backup recovery と同期済み temporary write を使い、result consumer が
 * partial JSON を読むことを防ぐ。書き込み失敗時は直前の valid payload を維持する。
 */
internal class AtomicPendingRestoreResultFile(
    private val resultFile: File,
    private val adapter: JsonAdapter<PendingRestoreResultFile>,
    private val atomicFile: AtomicFile = AtomicFile(resultFile),
) {
    private val backupFile = File("${resultFile.path}.bak")
    private val temporaryFile = File("${resultFile.path}.new")

    /**
     * 最後に publish された result JSON を raw text で読む。
     *
     * base file がない場合の backup recovery は [AtomicFile.openRead] に委譲する。
     * result が存在しない、または読めない場合は null を返す。
     */
    fun readRaw(): String? {
        return try {
            if (!resultFile.exists() && backupFile.exists() && !backupFile.renameTo(resultFile)) {
                return null
            }
            atomicFile.openRead().use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
        } catch (_: FileNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /** result JSON の存在を返す。backup だけが残る interrupted write も含む。 */
    fun exists(): Boolean = resultFile.exists() || backupFile.exists() || temporaryFile.exists()

    /**
     * result を temporary write、sync、atomic replace の順で publish する。
     *
     * [AtomicFile.finishWrite] 前に sync を完了し、失敗時は [AtomicFile.failWrite] で
     * 旧 payload を復元する。
     */
    fun write(result: PendingRestoreResultFile) {
        val parent = resultFile.parentFile
            ?: throw IllegalStateException("result has no parent directory: $resultFile")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("failed to create result directory: $parent")
        }
        if (!parent.isDirectory) {
            throw IllegalStateException("result parent is not a directory: $parent")
        }

        val output: FileOutputStream = atomicFile.startWrite()
        try {
            output.write(adapter.toJson(result).toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            try {
                atomicFile.failWrite(output)
            } catch (cleanupError: Exception) {
                error.addSuppressed(cleanupError)
            }
            throw error
        }
    }

    /** result と interrupted write の backup を削除する。 */
    fun delete(): Boolean {
        var deleted = true
        if (temporaryFile.exists() && !temporaryFile.delete()) deleted = false
        if (!deleted) return false
        if (resultFile.exists() && !resultFile.delete()) deleted = false
        if (backupFile.exists() && !backupFile.delete()) deleted = false
        return deleted && !resultFile.exists() && !backupFile.exists() && !temporaryFile.exists()
    }
}
