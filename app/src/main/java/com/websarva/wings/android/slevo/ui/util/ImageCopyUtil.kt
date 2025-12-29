package com.websarva.wings.android.slevo.ui.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
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
                val cacheDir = File(context.cacheDir, CACHE_DIR_NAME).apply { mkdirs() }
                val extension = resolveExtension(response.header("Content-Type"), url)
                val fileName = "image_${url.hashCode()}.$extension"
                val targetFile = File(cacheDir, fileName)
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
}
