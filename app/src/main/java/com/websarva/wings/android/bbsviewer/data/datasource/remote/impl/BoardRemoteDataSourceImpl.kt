package com.websarva.wings.android.bbsviewer.data.datasource.remote.impl

import android.util.Log
import com.websarva.wings.android.bbsviewer.data.datasource.remote.BoardRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.datasource.remote.SubjectFetchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class BoardRemoteDataSourceImpl @Inject constructor(
    private val client: OkHttpClient,
    @Named("UserAgent") private val userAgent: String
) : BoardRemoteDataSource {

    override suspend fun fetchSubjectTxt(url: String, etag: String?, lastModified: String?): SubjectFetchResult? =
        withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .cacheControl(CacheControl.FORCE_NETWORK)

            if (etag != null) {
                requestBuilder.header("If-None-Match", etag)
            }
            if (lastModified != null) {
                requestBuilder.header("If-Modified-Since", lastModified)
            }

            val request = requestBuilder.build()

            try {
                val response = client.newCall(request).execute()
                val newEtag = response.header("ETag")
                val newLastModified = response.header("Last-Modified")
                return@withContext when (response.code) {
                    200 -> {
                        val body = response.body?.bytes()?.toString(Charset.forName("Shift_JIS"))
                        SubjectFetchResult(body, newEtag, newLastModified, 200)
                    }
                    304 -> {
                        SubjectFetchResult(null, newEtag, newLastModified, 304)
                    }
                    else -> {
                        Log.e("BoardRemoteDataSource", "Unexpected response code ${response.code} for $url")
                        null
                    }
                }
            } catch (e: IOException) {
                Log.e("BoardRemoteDataSource", "IOException for $url: ${e.message}", e)
                null
            } catch (e: Exception) {
                Log.e("BoardRemoteDataSource", "Exception for $url: ${e.message}", e)
                null
            }
        }

    override suspend fun fetchSettingTxt(url: String): String? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                response.body?.bytes()?.toString(Charset.forName("Shift_JIS"))
            } catch (e: IOException) {
                Log.e("BoardRemoteDataSource", "IOException for $url: ${e.message}", e)
                null
            } catch (e: Exception) {
                Log.e("BoardRemoteDataSource", "Exception for $url: ${e.message}", e)
                null
            }
        }
}
