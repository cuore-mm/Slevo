package com.websarva.wings.android.slevo.data.backup.pending

import android.util.AtomicFile
import com.squareup.moshi.JsonAdapter
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * pending restore marker JSONをatomicに読み書きするfile abstraction。
 *
 * [AtomicFile]のbackup recoveryを使い、書き込み途中のmarkerを有効なstateとして公開しない。
 * markerが存在しない場合またはJSONをparseできない場合は`null`を返し、write failureはcallerへ伝播する。
 */
internal class AtomicPendingRestoreMarkerFile(
    private val markerFile: File,
    private val adapter: JsonAdapter<PendingRestoreMarker>,
) {
    private val atomicFile = AtomicFile(markerFile)
    private val backupFile = File("${markerFile.path}.bak")

    /**
     * 確定済みmarkerを読み取る。
     *
     * base fileがなくbackupだけ残る場合は、[AtomicFile.openRead]に回復を委譲する。
     * malformed JSONやmarker未作成はpending restoreなしとして扱う。
     */
    fun read(): PendingRestoreMarker? {
        return try {
            // AtomicFile.openRead() normally restores this backup. Keep an explicit fallback for
            // framework/Robolectric versions that do not restore when the base file is missing.
            if (!markerFile.exists() && backupFile.exists() && !backupFile.renameTo(markerFile)) {
                return null
            }
            val json = atomicFile.openRead().use { input ->
                input.readBytes().toString(StandardCharsets.UTF_8)
            }
            adapter.fromJson(json)
        } catch (_: FileNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * markerを一時的な書き込み状態からatomicにpublishする。
     *
     * [AtomicFile.finishWrite]より前にoutputをcloseしない。writeまたはfinishに失敗した場合は
     * [AtomicFile.failWrite]で直前のbackupを復元し、元の例外をcallerへ再throwする。
     */
    fun write(marker: PendingRestoreMarker) {
        // --- Parent directory ---
        val parent = markerFile.parentFile
            ?: throw IllegalStateException("marker has no parent directory: $markerFile")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("failed to create marker directory: $parent")
        }
        if (!parent.isDirectory) {
            throw IllegalStateException("marker parent is not a directory: $parent")
        }

        // --- Atomic publication ---
        val output: FileOutputStream = atomicFile.startWrite()
        try {
            output.write(adapter.toJson(marker).toByteArray(StandardCharsets.UTF_8))
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
}
