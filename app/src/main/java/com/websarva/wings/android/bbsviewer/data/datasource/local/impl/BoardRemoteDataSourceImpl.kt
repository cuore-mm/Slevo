package com.websarva.wings.android.bbsviewer.data.datasource.local.impl

import com.websarva.wings.android.bbsviewer.data.datasource.local.BoardRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BoardRemoteDataSourceImpl @Inject constructor(
    private val client: OkHttpClient
) : BoardRemoteDataSource {
    private var lastModified: String? = null
    private var eTag: String? = null

    override suspend fun fetchSubjectTxt(url: String): String? = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Monazilla/1.00")
        lastModified?.let { builder.header("If-Modified-Since", it) }
        eTag?.let         { builder.header("If-None-Match", it) }
        val resp = client.newCall(builder.build()).execute()
        return@withContext when (resp.code) {
            304 -> null
            200 -> resp.use {
                resp.header("Last-Modified")?.also { lastModified = it }
                resp.header("ETag")?.also          { eTag = it  }
                it.body!!.bytes().toString(Charset.forName("Shift_JIS"))
            }
            else -> throw IOException("Unexpected response code ${resp.code}")
        }
    }
}
