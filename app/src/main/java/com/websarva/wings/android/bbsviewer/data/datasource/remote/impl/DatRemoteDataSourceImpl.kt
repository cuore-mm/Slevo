package com.websarva.wings.android.bbsviewer.data.datasource.remote.impl

import com.websarva.wings.android.bbsviewer.data.datasource.remote.DatRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.util.DataUsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class DatRemoteDataSourceImpl @Inject constructor(
    private val client: OkHttpClient,
    @Named("UserAgent") private val userAgent: String
) : DatRemoteDataSource {

    private data class CacheEntry(
        var bytes: ByteArray,
        var lastModified: String?
    )

    private val cache = mutableMapOf<String, CacheEntry>()

    override suspend fun fetchDatString(datUrl: String, onProgress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url(datUrl)
                .header("User-Agent", userAgent)

            val cacheEntry = cache[datUrl]
            if (cacheEntry != null) {
                cacheEntry.lastModified?.let { builder.header("If-Modified-Since", it) }
                // ローカルサイズ - 1 を指定することであぼーんを検出しやすくする
                builder.header("Range", "bytes=${cacheEntry.bytes.size - 1}-")
                builder.header("Accept-Encoding", "identity")
            }

            val request = builder.build()
            client.newCall(request).execute().use { response ->
                return@withContext handleResponse(datUrl, response, cacheEntry, onProgress)
            }
        } catch (e: IOException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun handleResponse(
        datUrl: String,
        response: Response,
        cacheEntry: CacheEntry?,
        onProgress: (Float) -> Unit,
    ): String? {
        when (response.code) {
            200 -> {
                val body = response.body ?: return null
                val bytes = readBytesWithProgress(body, onProgress)
                DataUsageTracker.addBytes(bytes.size.toLong())
                val lastMod = response.header("Last-Modified")
                cache[datUrl] = CacheEntry(bytes, lastMod)
                return String(bytes, Charset.forName("Shift_JIS"))
            }
            206 -> {
                val body = response.body ?: return null
                val newBytes = readBytesWithProgress(body, onProgress)
                DataUsageTracker.addBytes(newBytes.size.toLong())
                val lastMod = response.header("Last-Modified")
                if (cacheEntry != null) {
                    if (newBytes.isNotEmpty()) {
                        return if (newBytes[0].toInt() == '\n'.code) {
                            val appendBytes = newBytes.copyOfRange(1, newBytes.size)
                            cacheEntry.bytes += appendBytes
                            cacheEntry.lastModified = lastMod ?: cacheEntry.lastModified
                            String(cacheEntry.bytes, Charset.forName("Shift_JIS"))
                        } else {
                            cache.remove(datUrl)
                            fetchFullDat(datUrl, onProgress)
                        }
                    }
                    cacheEntry.lastModified = lastMod ?: cacheEntry.lastModified
                    return String(cacheEntry.bytes, Charset.forName("Shift_JIS"))
                }
                cache[datUrl] = CacheEntry(newBytes, lastMod)
                return String(newBytes, Charset.forName("Shift_JIS"))
            }
            304 -> {
                return null
            }
            416 -> {
                cache.remove(datUrl)
                return fetchFullDat(datUrl, onProgress)
            }
            else -> {
                return null
            }
        }
    }

    private fun fetchFullDat(datUrl: String, onProgress: (Float) -> Unit): String? {
        val request = Request.Builder()
            .url(datUrl)
            .header("User-Agent", userAgent)
            .header("Accept-Encoding", "identity")
            .build()
        client.newCall(request).execute().use { res ->
            if (res.code != 200) return null
            val body = res.body ?: return null
            val bytes = readBytesWithProgress(body, onProgress)
            DataUsageTracker.addBytes(bytes.size.toLong())
            val lastMod = res.header("Last-Modified")
            cache[datUrl] = CacheEntry(bytes, lastMod)
            return String(bytes, Charset.forName("Shift_JIS"))
        }
    }
    private fun readBytesWithProgress(body: okhttp3.ResponseBody, onProgress: (Float) -> Unit): ByteArray {
        val contentLength = body.contentLength()
        val input = body.byteStream()
        val buffer = ByteArray(8 * 1024)
        var bytesRead: Int
        var total = 0L
        val output = java.io.ByteArrayOutputStream()
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            total += bytesRead
            if (contentLength > 0) {
                onProgress(total.toFloat() / contentLength)
            }
        }
        onProgress(1f)
        return output.toByteArray()
    }
}
