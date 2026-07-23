package com.websarva.wings.android.slevo.data.backup.export

import android.content.Context
import android.net.Uri
import com.squareup.moshi.Moshi
import com.websarva.wings.android.slevo.data.backup.BackupResourceLimitExceededException
import com.websarva.wings.android.slevo.data.backup.BackupResourceLimits
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
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
 *
 * [resourceLimits] によりエントリbyte数・合計byte数・エントリ数を制限し、
 * 復元時に拒否されるサイズのバックアップを成功扱いしない。
 *
 * @param outputStream 出力先ストリーム。
 * @param moshi JSON シリアライズ用 Moshi インスタンス。
 * @param resourceLimits 展開サイズ上限policy。テストでは小さい値を注入できる。
 */
class BackupZipWriter(
    private val outputStream: OutputStream,
    private val moshi: Moshi = Moshi.Builder().build(),
    private val resourceLimits: BackupResourceLimits = BackupResourceLimits(),
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

    /** file/directoryを含む全エントリ（putNextEntry済み）のカウンタ。 */
    private var entryCount = 0
    /** 書き込まれたuncompressed byteの合計。 */
    private var totalUncompressed: Long = 0

    /** source file input stream の作成方法。testからactual stream sizeを注入できる。 */
    internal var fileInputProvider: (File) -> InputStream = { file ->
        FileInputStream(file)
    }

    // --- entry writers ---

    /** JSON エントリを追加する。 */
    @OptIn(ExperimentalStdlibApi::class)
    fun writeJsonEntry(name: String, value: Any) {
        val adapter = moshi.adapter(value.javaClass)
        val json = adapter.toJson(value)
        writeEntry(name, json.toByteArray(Charsets.UTF_8))
    }

    /**
     * ファイルを ZIP エントリとして streaming 追加する。
     *
     * [File.length()] が対応する上限を明らかに超えている場合は早期拒否する。
     * 実際のstreaming中にもbyte数を計測し、事前確認値を超えた分は上限超過として扱う。
     */
    fun writeFileEntry(name: String, file: File) {
        if (closed || writeFailed) return
        // --- resource limit check ---
        val limit = resourceLimits.limitForEntry(name)
        checkEntryCount(name)
        checkKnownEntryLimit(name, file.length(), limit)
        checkTotalLimit(file.length())
        try {
            fileInputProvider(file).use { input ->
                zip.putNextEntry(ZipEntry(name))
                val written = copyWithLimit(input, limit, name)
                totalUncompressed += written
                entryCount++
                zip.closeEntry()
            }
        } catch (e: BackupResourceLimitExceededException) {
            writeFailed = true
            throw e
        } catch (e: Exception) {
            writeFailed = true
            throw e
        }
    }

    /** 生 byte 配列をエントリとして追加する。 */
    fun writeEntry(name: String, content: ByteArray) {
        if (closed || writeFailed) return
        // --- resource limit check ---
        val limit = resourceLimits.limitForEntry(name)
        checkEntryCount(name)
        checkKnownEntryLimit(name, content.size.toLong(), limit)
        checkTotalLimit(content.size.toLong())
        try {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
            entryCount++
            totalUncompressed += content.size.toLong()
        } catch (e: Exception) {
            writeFailed = true
            throw e
        }
    }

    /**
     * entry count上限をputNextEntry前に検証する。
     *
     * 上限超過時は [BackupResourceLimitExceededException] を投げ、
     * エントリのZIPへの追加を防ぐ。
     */
    private fun checkEntryCount(entryName: String) {
        // --- entry count ---
        if (entryCount >= resourceLimits.entryCount) {
            writeFailed = true
            throw BackupResourceLimitExceededException(
                entryName = entryName,
                actual = (entryCount + 1).toLong(),
                limit = resourceLimits.entryCount.toLong(),
                target = "entry-count",
            )
        }
    }

    /** 既知entryだけに個別size上限を適用する。 */
    private fun checkKnownEntryLimit(
        entryName: String,
        entrySize: Long,
        entryLimit: Long?,
    ) {
        // --- per-entry limit ---
        if (entryLimit != null && entrySize > entryLimit) {
            writeFailed = true
            throw BackupResourceLimitExceededException(
                entryName = entryName,
                actual = entrySize,
                limit = entryLimit,
                target = "entry",
            )
        }
    }

    /** 全entryにuncompressed total上限を適用する。 */
    private fun checkTotalLimit(entrySize: Long) {
        // --- total limit ---
        if (totalUncompressed > resourceLimits.totalBytes - entrySize) {
            writeFailed = true
            throw BackupResourceLimitExceededException(
                entryName = null,
                actual = totalUncompressed + entrySize,
                limit = resourceLimits.totalBytes,
                target = "total",
            )
        }
    }

    /**
     * [FileInputStream] から固定バッファで読み、[zip] へ転送する bounded copy。
     *
     * known entryのentry上限と、全entry共通のtotal上限を実測byte数で検証する。
     * 超過byteをZIPへ書かず、超過時は [BackupResourceLimitExceededException] を投げる。
     *
     * @return 書き込んだbyte数。
     */
    private fun copyWithLimit(
        input: InputStream,
        entryLimit: Long?,
        entryName: String,
    ): Long {
        val buffer = ByteArray(8192)
        var written: Long = 0
        while (true) {
            val entryRemaining = entryLimit?.let { it - written } ?: Long.MAX_VALUE
            val totalRemaining = resourceLimits.totalBytes - totalUncompressed - written
            if (entryRemaining < 0) {
                writeFailed = true
                throw BackupResourceLimitExceededException(
                    entryName = entryName,
                    actual = written,
                    limit = entryLimit ?: Long.MAX_VALUE,
                    target = "entry",
                )
            }
            if (totalRemaining < 0) {
                writeFailed = true
                throw BackupResourceLimitExceededException(
                    entryName = null,
                    actual = totalUncompressed + written,
                    limit = resourceLimits.totalBytes,
                    target = "total",
                )
            }
            val remaining = minOf(entryRemaining, totalRemaining)
            val readLength = if (remaining >= buffer.size.toLong()) buffer.size else (remaining + 1).toInt()
            val bytesRead = input.read(buffer, 0, readLength)
            if (bytesRead == -1) break
            if (bytesRead > entryRemaining) {
                writeFailed = true
                throw BackupResourceLimitExceededException(
                    entryName = entryName,
                    actual = written + bytesRead,
                    limit = entryLimit ?: Long.MAX_VALUE,
                    target = "entry",
                )
            }
            if (bytesRead > totalRemaining) {
                writeFailed = true
                throw BackupResourceLimitExceededException(
                    entryName = null,
                    actual = totalUncompressed + written + bytesRead,
                    limit = resourceLimits.totalBytes,
                    target = "total",
                )
            }
            zip.write(buffer, 0, bytesRead)
            written += bytesRead
        }
        return written
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
 * `ContentResolver.openOutputStream(uri, "wt")` により write-only truncate mode で
 * 保存先を開き、FileProvider や外部ストレージ権限なしで既存内容を上書きする。
 */
@Singleton
class BackupOutputWriter private constructor(
    private val outputStreamOpener: (Uri, String) -> OutputStream?,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        outputStreamOpener = { uri, mode -> context.contentResolver.openOutputStream(uri, mode) },
    )

    /**
     * [BackupOutputWriter] の SAF opener を差し替える test-only factory。
     *
     * JVM test から mode、null、open例外を決定し、SAF framework method へ依存せず
     * [writeToUri] の契約を検証するために使用する。
     */
    companion object {
        internal fun forTest(
            outputStreamOpener: (Uri, String) -> OutputStream?,
        ): BackupOutputWriter = BackupOutputWriter(outputStreamOpener)
    }

    /**
     * SAF URI への出力ストリームを write-only truncate mode (`"wt"`) で開いて
     * [block] に渡し、ブロック完了後にストリームを close する。
     *
     * `"w"` は provider によって truncate しない可能性があるため、
     * `"wt"` により既存ドキュメントの内容を明示的に消去する。
     * 非 truncate mode への fallback は行わず、open 例外はそのまま伝播する。
     *
     * @param uri SAF の保存先 URI。
     * @param block 出力ストリームを受け取って ZIP 書き込みを行うブロック。
     * @throws BackupOutputException ストリームを開けず null が返った場合。
     */
    suspend fun writeToUri(uri: Uri, block: suspend (OutputStream) -> Unit) {
        withContext(Dispatchers.IO) {
            val output = outputStreamOpener(uri, "wt")
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
