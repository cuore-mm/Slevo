package com.websarva.wings.android.slevo.data.backup.export

import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.OutputStream
import java.util.Collections

/**
 * [BackupOutputWriter] のSAF mode、null open、stream closeの契約を検証する。
 *
 * internal opener seamへtest doubleを渡し、Android frameworkの実装差を経由せずに
 * `"wt"`指定とfallback禁止のアプリ側責務を固定する。
 */
class BackupOutputWriterTest {

    // Uri.parse() はJVM testでStub!例外になるため、openerが使用しないmockを渡す。
    private fun testUri(): Uri = mockk(relaxed = true)

    /**
     * 既存内容をlogical size付きで保持し、open modeに応じてtruncateを模擬するstorage。
     *
     * `t`を含むmodeではlogical sizeだけを0にし、その後の書き込みを新しい内容として扱う。
     * `t`を含まないmodeでは先頭から上書きしても旧logical sizeを維持するため、旧末尾が残る。
     */
    private class ModeSensitiveStorage(initialContent: ByteArray) {
        private var bytes = initialContent.copyOf()
        private var logicalSize = initialContent.size

        /** modeに応じたlogical truncate状態で先頭から書き込むstreamを開く。 */
        fun open(mode: String): OutputStream {
            if ('t' in mode) {
                // Truncate modeは既存内容を論理的に消去してから書き込む。
                logicalSize = 0
            }
            return StorageOutputStream()
        }

        /** logical sizeまでの保存内容を返す。 */
        fun snapshot(): ByteArray = bytes.copyOf(logicalSize)

        /** 指定位置へbyteを書き込み、保存領域とlogical sizeを更新する。 */
        private fun writeAt(position: Int, value: Int) {
            if (position >= bytes.size) {
                bytes = bytes.copyOf(position + 1)
            }
            bytes[position] = value.toByte()
            if (position >= logicalSize) {
                logicalSize = position + 1
            }
        }

        /** [ModeSensitiveStorage]の先頭から書き込む出力ストリーム。 */
        private inner class StorageOutputStream : OutputStream() {
            private var position = 0

            override fun write(b: Int) {
                writeAt(position, b)
                position++
            }

            /** 指定範囲のbyteを先頭から順にstorageへ書き込む。 */
            override fun write(b: ByteArray, off: Int, len: Int) {
                for (index in off until off + len) {
                    write(b[index].toInt())
                }
            }
        }
    }

    /** close回数を記録して[BackupOutputWriter]のfinally処理を検証するstream。 */
    private class CloseTrackingOutputStream : OutputStream() {
        var closeCount = 0
            private set

        override fun write(b: Int) = Unit

        override fun close() {
            closeCount++
        }
    }

    @Test
    fun writeToUri_opensOutputStreamWithWriteTruncateMode() = runTest {
        val modes = mutableListOf<String>()
        val writer = BackupOutputWriter.forTest { _, mode ->
            modes += mode
            CloseTrackingOutputStream()
        }

        writer.writeToUri(testUri()) { it.write(42) }

        assertEquals(listOf("wt"), modes)
        assertFalse(modes.contains("w"))
        assertFalse(modes.contains("rw"))
        assertFalse(modes.contains("rwt"))
    }

    @Test
    fun writeToUri_withSmallerContentInWtMode_clearsOldTail() = runTest {
        val oldContent = ByteArray(2048) { (it % 256).toByte() }
        val newContent = ByteArray(512) { ((255 - it) % 256).toByte() }
        val storage = ModeSensitiveStorage(oldContent)
        val modes = mutableListOf<String>()
        val writer = BackupOutputWriter.forTest { _, mode ->
            modes += mode
            storage.open(mode)
        }

        writer.writeToUri(testUri()) { it.write(newContent) }

        assertEquals(listOf("wt"), modes)
        assertArrayEquals(newContent, storage.snapshot())
    }

    @Test
    fun modeSensitiveStorage_withWMode_retainsOldTail() {
        val oldContent = ByteArray(2048) { (it % 256).toByte() }
        val newContent = ByteArray(512) { ((255 - it) % 256).toByte() }
        val storage = ModeSensitiveStorage(oldContent)

        storage.open("w").use { it.write(newContent) }

        val expected = newContent + oldContent.copyOfRange(newContent.size, oldContent.size)
        assertArrayEquals(expected, storage.snapshot())
    }

    @Test
    fun writeToUri_nullOutputStream_throwsBackupOutputException() = runTest {
        val modes = Collections.synchronizedList(mutableListOf<String>())
        var blockExecuted = false
        val writer = BackupOutputWriter.forTest { _, mode ->
            modes += mode
            null
        }

        try {
            writer.writeToUri(testUri()) { blockExecuted = true }
            fail("expected BackupOutputException")
        } catch (_: BackupOutputException) {
            // expected
        }

        assertFalse(blockExecuted)
        assertEquals(listOf("wt"), modes.toList())
    }

    @Test
    fun writeToUri_blockThrows_closesOutputStream() = runTest {
        val stream = CloseTrackingOutputStream()
        val writer = BackupOutputWriter.forTest { _, _ -> stream }

        try {
            writer.writeToUri(testUri()) { throw IllegalStateException("block failed") }
            fail("expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }

        assertEquals(1, stream.closeCount)
    }

    @Test
    fun writeToUri_blockSucceeds_closesOutputStream() = runTest {
        val stream = CloseTrackingOutputStream()
        val writer = BackupOutputWriter.forTest { _, _ -> stream }

        writer.writeToUri(testUri()) { it.write(1) }

        assertEquals(1, stream.closeCount)
    }
}
