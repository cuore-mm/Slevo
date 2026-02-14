package com.websarva.wings.android.slevo.ui.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 画像URLからキャッシュファイルを作成し、FileProviderのURIを返すユーティリティ。
 *
 * 取得した画像はアプリのキャッシュ領域へ保存し、content:// URIとして公開する。
 */
object ImageCopyUtil {
    private const val CACHE_DIR_NAME = "image_cache"
    private const val DEFAULT_EXTENSION = "jpg"
    private const val DEFAULT_MIME_TYPE = "image/jpeg"
    private const val IMAGE_DIR_NAME = "Slevo"
    private val client = OkHttpClient()

    /**
     * 画像URLをダウンロードし、FileProviderのURIとして返却する。
     *
     * 失敗時は例外を含むResultを返し、呼び出し側で通知などを行う。
     */
    suspend fun fetchImageUri(context: Context, url: String): Result<Uri> {
        if (url.isBlank()) {
            // 空URLは処理対象外とする。
            return Result.failure(IllegalArgumentException("Image URL is blank"))
        }
        return withContext(Dispatchers.IO) {
            // --- Reuse cache ---
            val reused = fetchImageUriFromDisplayedCache(context, url)
            if (reused != null) {
                return@withContext Result.success(reused)
            }

            // --- Download ---
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to fetch image"))
                }
                val body = response.body ?: return@withContext Result.failure(
                    IOException("Image body is empty")
                )

                // --- Cache write ---
                val extension = resolveExtension(response.header("Content-Type"), url)
                val targetFile = resolveShareCacheFile(context, url, extension)
                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // --- Uri build ---
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, targetFile)
                Result.success(uri)
            }
        }
    }

    /**
     * 表示済み画像の再利用メタデータから共有用URIを生成する。
     *
     * 再利用できない場合は null を返し、呼び出し元で通常取得へフォールバックする。
     */
    private fun fetchImageUriFromDisplayedCache(context: Context, url: String): Uri? {
        val reuseEntry = ImageActionReuseRegistry.get(url) ?: return null
        return runCatching {
            // --- Snapshot lookup ---
            val diskCache = SingletonImageLoader.get(context).diskCache ?: return null
            val snapshot = diskCache.openSnapshot(reuseEntry.diskCacheKey) ?: return null

            // --- Share file materialization ---
            snapshot.use { openedSnapshot ->
                val sourceFile = File(openedSnapshot.data.toString())
                if (!sourceFile.exists()) {
                    // Fallback: 参照先ファイルが無い場合は通常取得へ切り替える。
                    return null
                }
                val extension = sourceFile.extension.ifBlank { DEFAULT_EXTENSION }
                val targetFile = resolveShareCacheFile(context, url, extension)
                sourceFile.inputStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // --- Uri build ---
                val authority = "${context.packageName}.fileprovider"
                FileProvider.getUriForFile(context, authority, targetFile)
            }
        }.getOrNull()
    }

    /**
     * 画像URLを端末の共有ストレージへ保存し、MediaStoreのURIとして返却する。
     *
     * 保存先は Pictures/Slevo とし、失敗時は例外を含むResultを返す。
     */
    suspend fun saveImageToMediaStore(context: Context, url: String): Result<Uri> {
        if (url.isBlank()) {
            // 空URLは処理対象外とする。
            return Result.failure(IllegalArgumentException("Image URL is blank"))
        }
        return withContext(Dispatchers.IO) {
            // --- Download ---
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to fetch image"))
                }
                val body = response.body ?: return@withContext Result.failure(
                    IOException("Image body is empty")
                )

                // --- Metadata ---
                val contentType = response.header("Content-Type")
                val extension = resolveExtension(contentType, url)
                val mimeType = resolveMimeType(contentType, extension)
                val fileName = "image_${System.currentTimeMillis()}_${url.hashCode()}.$extension"
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                }

                // --- Insert ---
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$IMAGE_DIR_NAME"
                    )
                    values.put(MediaStore.Images.Media.IS_PENDING, 1)
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                } else {
                    val picturesDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val targetDir = File(picturesDir, IMAGE_DIR_NAME)
                    if (!targetDir.exists() && !targetDir.mkdirs()) {
                        // 保存先ディレクトリの作成に失敗した場合は終了する。
                        return@withContext Result.failure(
                            IOException("Failed to create image directory")
                        )
                    }
                    val targetFile = File(targetDir, fileName)
                    values.put(MediaStore.Images.Media.DATA, targetFile.absolutePath)
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                } ?: return@withContext Result.failure(
                    // 登録に失敗した場合は保存を中断する。
                    IOException("Failed to create image entry")
                )

                // --- Write ---
                val written = runCatching {
                    resolver.openOutputStream(uri)?.use { output ->
                        body.byteStream().use { input ->
                            input.copyTo(output)
                        }
                    } ?: throw IOException("Failed to open output stream")
                }
                if (written.isFailure) {
                    // 書き込みに失敗した場合は登録を取り消す。
                    resolver.delete(uri, null, null)
                    return@withContext Result.failure(
                        written.exceptionOrNull() ?: IOException("Failed to write image")
                    )
                }

                // --- Finalize ---
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val finalizeValues = ContentValues().apply {
                        put(MediaStore.Images.Media.IS_PENDING, 0)
                    }
                    resolver.update(uri, finalizeValues, null, null)
                }

                Result.success(uri)
            }
        }
    }

    /**
     * Content-TypeやURLから拡張子を推定する。
     *
     * 未判定の場合は既定拡張子を返す。
     */
    private fun resolveExtension(contentType: String?, url: String): String {
        val type = contentType
            ?.substringAfter("/")
            ?.substringBefore(";")
            ?.lowercase(Locale.US)
        if (!type.isNullOrBlank()) {
            return type
        }
        val urlExtension = url.substringAfterLast('.', "")
        return if (urlExtension.isNotBlank()) {
            urlExtension.lowercase(Locale.US)
        } else {
            DEFAULT_EXTENSION
        }
    }

    /**
     * 共有アクション用のキャッシュファイルを返す。
     */
    private fun resolveShareCacheFile(context: Context, url: String, extension: String): File {
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
        val normalizedExtension = extension.ifBlank { DEFAULT_EXTENSION }
        val fileName = "image_${url.hashCode()}.$normalizedExtension"
        return File(cacheDir, fileName)
    }

    /**
     * Content-Typeと拡張子から保存用のMIMEタイプを推定する。
     *
     * 判定できない場合は既定の画像MIMEタイプを返す。
     */
    private fun resolveMimeType(contentType: String?, extension: String): String {
        val normalized = contentType
            ?.substringBefore(";")
            ?.lowercase(Locale.US)
        if (!normalized.isNullOrBlank()) {
            return normalized
        }
        val mimeFromExt = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(extension.lowercase(Locale.US))
        return mimeFromExt ?: DEFAULT_MIME_TYPE
    }
}
