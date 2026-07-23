package com.websarva.wings.android.slevo.data.backup.export

import android.content.Context
import android.net.Uri
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * バックアップ ZIP を出力ストリームへ書き込む writer。
 *
 * SAF 経由の [Uri] 出力と fake stream テストの両方に対応するため、
 * 出力先は [OutputStream] で抽象化する。ZIP close と output stream close の
 * エラーは区別してハンドリングし、すべて成功してから成功扱いにする。
 */
class BackupZipWriter(
    private val outputStream: OutputStream,
    private val moshi: Moshi = Moshi.Builder().build(),
) : Closeable {

    private val zip = ZipOutputStream(outputStream)

    /**
     * 完了状態。close 後にのみ確定する。
     * `writeEntry*` の呼び出し前にアクセスしてはならない。
     */
    val isClosed: Boolean get() = closed

    private var closed = false
    private var writeFailed = false
    private var zipCloseFailed = false
    private var outputCloseFailed = false

    // --- entry writers ---

    /** JSON エントリを追加する。 */
    @OptIn(ExperimentalStdlibApi::class)
    fun writeJsonEntry(name: String, value: Any) {
        val adapter = moshi.adapter(value.javaClass)
        val json = adapter.toJson(value)
        writeEntry(name, json.toByteArray(Charsets.UTF_8))
    }

    /** ファイルをエントリとして追加する。 */
    fun writeFileEntry(name: String, file: File) {
        FileInputStream(file).use { input ->
            val bytes = input.readBytes()
            writeEntry(name, bytes)
        }
    }

    /** 生 byte 配列をエントリとして追加する。 */
    fun writeEntry(name: String, content: ByteArray) {
        if (closed || writeFailed) return
        try {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        } catch (e: Exception) {
            writeFailed = true
            throw e
        }
    }

    /**
     * ZIP stream と output stream を順に close し、完了状態を返す。
     *
     * ZipOutputStream.close() は内部で finish() → outputStream.close() まで行う。
     * finish() に失敗すると output stream が中途状態で残る可能性があるため、
     * close 失敗も検出する。
     */
    override fun close() {
        if (closed) return
        try {
            try {
                zip.finish()
            } catch (e: Exception) {
                zipCloseFailed = true
            }
            zip.close()
        } catch (e: Exception) {
            zipCloseFailed = true
        }
        try {
            outputStream.close()
        } catch (e: Exception) {
            outputCloseFailed = true
        }
        closed = true
    }

    /**
     * 書き込みが完全に成功したか。
     * close 後にのみ有効な結果を返す。
     */
    fun isSuccessful(): Boolean =
        closed && !writeFailed && !zipCloseFailed && !outputCloseFailed

    /**
     * 失敗の原因を説明する文字列。
     */
    fun failureReason(): String? {
        if (isSuccessful()) return null
        val reasons = mutableListOf<String>()
        if (writeFailed) reasons.add("entry write failed")
        if (zipCloseFailed) reasons.add("ZIP finish/close failed")
        if (outputCloseFailed) reasons.add("output stream close failed")
        if (!closed) reasons.add("not closed")
        return reasons.joinToString("; ")
    }
}

// --- SAF output helper ---

/**
 * SAF の [Uri] へ ZIP を出力するヘルパー。
 *
 * `ContentResolver.openOutputStream(uri)` を使い、
 * `FileProvider` や外部ストレージ権限なしで書き込む。
 */
@Singleton
class BackupOutputWriter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * SAF URI への出力ストリームを開いて [block] に渡し、
     * ブロック完了後にストリームを close する。
     *
     * @param uri SAF の保存先 URI。
     * @param block 出力ストリームを受け取って ZIP 書き込みを行うブロック。
     * @return 成功時 Unit、失敗時例外。
     * @throws BackupOutputException ストリームを開けない場合。
     */
    suspend fun writeToUri(uri: Uri, block: suspend (OutputStream) -> Unit) {
        withContext(Dispatchers.IO) {
            val output = context.contentResolver.openOutputStream(uri, "w")
                ?: throw BackupOutputException("failed to open output stream for URI: $uri")
            try {
                block(output)
            } finally {
                try {
                    output.close()
                } catch (_: Exception) {
                    // best-effort close
                }
            }
        }
    }
}

/** SAF 出力失敗を表す例外。 */
class BackupOutputException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
