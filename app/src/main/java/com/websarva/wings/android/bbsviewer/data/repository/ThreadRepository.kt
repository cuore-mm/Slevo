package com.websarva.wings.android.bbsviewer.data.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import com.websarva.wings.android.bbsviewer.data.util.ThreadListParser.parseSubjectTxt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.charset.Charset
import javax.inject.Inject

class ThreadRepository @Inject constructor(
    private val client: OkHttpClient
) {
    private var lastModified: String? = null
    private var eTag: String? = null

    @Throws(IOException::class)
    suspend fun fetchSubjectTxt(url: String): SubjectTxtResult = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Monazilla/1.00")
        // 前回取得済みなら条件付きGET用のヘッダーを追加
        lastModified?.let { builder.header("If-Modified-Since", it) }
        eTag?.let { builder.header("If-None-Match", it) }
        val request = builder.build()

        client.newCall(request).execute().use { response ->
            when (response.code) {
                304 -> SubjectTxtResult.NotModified
                200 -> {
                    // レスポンスヘッダーから更新チェック用情報を更新
                    response.header("Last-Modified")?.let { lastModified = it }
                    response.header("ETag")?.let { eTag = it }
                    // Shift_JISでデコード
                    val responseBody = response.body ?: throw IOException("Empty body")
                    val decodedText = String(responseBody.bytes(), Charset.forName("Shift_JIS"))
                    SubjectTxtResult.Updated(decodedText)
                }
                else -> throw IOException("Unexpected response code ${response.code}")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getThreadList(url: String): ThreadListResult = withContext(Dispatchers.IO) {
        when (val subjectResult = fetchSubjectTxt(url)) {
            is SubjectTxtResult.Updated -> {
                val threads = parseSubjectTxt(subjectResult.content)
                ThreadListResult.Updated(threads)
            }
            is SubjectTxtResult.NotModified -> ThreadListResult.NotModified
        }
    }
}

// 更新結果を表すsealed class
sealed class SubjectTxtResult {
    data class Updated(val content: String) : SubjectTxtResult()
    data object NotModified : SubjectTxtResult()
}

// ViewModelに返すスレッド一覧の結果を表すsealed class
sealed class ThreadListResult {
    data class Updated(val threads: List<ThreadInfo>) : ThreadListResult()
    data object NotModified : ThreadListResult()
}
