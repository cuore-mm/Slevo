package com.websarva.wings.android.bbsviewer.data.datasource.remote.impl

import com.websarva.wings.android.bbsviewer.data.datasource.remote.BoardRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private var lastModified: String? = null
    private var eTag: String? = null

    override suspend fun fetchSubjectTxt(url: String): String? = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)

        // 差分取得かどうかを判定
        val isDifferential = lastModified != null || eTag != null

        if (isDifferential) {
            // 差分取得の場合：ヘッダーを追加し、gzipを無効化
            lastModified?.let { builder.header("If-Modified-Since", it) }
            eTag?.let         { builder.header("If-None-Match", it) }
            builder.header("Accept-Encoding", "identity") // gzipを無効にする
        } else {
            // 通常取得の場合：OkHttpのデフォルト (gzip有効) に任せる
            // 明示的に builder.header("Accept-Encoding", "gzip") を追加しても良いが、
            // OkHttpが自動で行うため必須ではない。
        }

        val resp = client.newCall(builder.build()).execute()

        return@withContext when (resp.code) {
            304 -> {
                // 304 Not Modified - 更新なし、nullを返す
                null
            }
            200 -> resp.use {
                // 200 OK - 更新あり
                // lastModified と eTag を更新
                it.header("Last-Modified")?.also { lm -> lastModified = lm }
                it.header("ETag")?.also          { et -> eTag = et  }

                // レスポンスボディを取得してデコード
                // OkHttpが自動でgzipを解凍してくれる
                it.body!!.bytes().toString(Charset.forName("Shift_JIS"))
            }
            else -> {
                // その他のエラー
                // 差分取得に失敗した可能性があるので、次回はフル取得するようにヘッダー情報をクリア
                lastModified = null
                eTag = null
                throw IOException("Unexpected response code ${resp.code}")
            }
        }
    }
}
