package com.websarva.wings.android.bbsviewer.data.datasource.remote.impl

import com.websarva.wings.android.bbsviewer.data.datasource.remote.PostRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.repository.ConfirmationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URLEncoder
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Named

class PostRemoteDataSourceImpl @Inject constructor(
    private val client: OkHttpClient,
    @Named("UserAgent") private val userAgent: String
) : PostRemoteDataSource {

    override suspend fun postFirstPhase(
        host: String,
        board: String,
        threadKey: String,
        name: String,
        mail: String,
        message: String
    ): Response? = withContext(Dispatchers.IO) {
        try {
            val postUrl = "https://$host/test/bbs.cgi?guid=ON"
            val referer = "https://$host/test/read.cgi/$board/$threadKey"
            val time = (System.currentTimeMillis() / 1000).toString()

            val formBody = FormBody.Builder(Charset.forName("Shift_JIS"))
                .addEncoded("bbs", board)
                .addEncoded("key", threadKey)
                .addEncoded("time", time)
                .addEncoded("FROM", URLEncoder.encode(name, "Shift_JIS"))
                .addEncoded("mail", URLEncoder.encode(mail, "Shift_JIS"))
                .addEncoded("MESSAGE", URLEncoder.encode(message, "Shift_JIS"))
                .addEncoded("submit", URLEncoder.encode("書き込む", "Shift_JIS"))
                .build()

            val request = Request.Builder()
                .url(postUrl)
                .header("Referer", referer)
                .header("User-Agent", userAgent)
                .post(formBody)
                .build()

            client.newCall(request).execute()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun postSecondPhase(
        host: String,
        board: String,
        threadKey: String,
        confirmationData: ConfirmationData
    ): Response? = withContext(Dispatchers.IO) {
        try {
            val url = "https://$host/test/bbs.cgi?guid=ON"
            val referer = "https://$host/test/read.cgi/$board/$threadKey"

            val formBuilder = FormBody.Builder(Charset.forName("Shift_JIS"))
            confirmationData.hiddenParams.forEach { (key, value) ->
                formBuilder.addEncoded(key, URLEncoder.encode(value, "Shift_JIS"))
            }
            formBuilder.addEncoded(
                "submit",
                URLEncoder.encode("上記全てを承諾して書き込む", "Shift_JIS")
            )

            val request = Request.Builder()
                .url(url)
                .header("Referer", referer)
                .header("User-Agent", userAgent)
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun createThreadFirstPhase(
        host: String,
        board: String,
        subject: String,
        name: String,
        mail: String,
        message: String,
    ): Response? = withContext(Dispatchers.IO) {
        try {
            val postUrl = "https://$host/test/bbs.cgi?guid=ON"
            val referer = "https://$host/test/read.cgi/$board/"
            val time = (System.currentTimeMillis() / 1000).toString()

            val formBody = FormBody.Builder(Charset.forName("Shift_JIS"))
                .addEncoded("bbs", board)
                .addEncoded("time", time)
                .addEncoded("subject", URLEncoder.encode(subject, "Shift_JIS"))
                .addEncoded("FROM", URLEncoder.encode(name, "Shift_JIS"))
                .addEncoded("mail", URLEncoder.encode(mail, "Shift_JIS"))
                .addEncoded("MESSAGE", URLEncoder.encode(message, "Shift_JIS"))
                .addEncoded("submit", URLEncoder.encode("新規スレッド作成", "Shift_JIS"))
                .build()

            val request = Request.Builder()
                .url(postUrl)
                .header("Referer", referer)
                .header("User-Agent", userAgent)
                .post(formBody)
                .build()

            client.newCall(request).execute()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun createThreadSecondPhase(
        host: String,
        board: String,
        confirmationData: ConfirmationData,
    ): Response? = withContext(Dispatchers.IO) {
        try {
            val url = "https://$host/test/bbs.cgi?guid=ON"
            val referer = "https://$host/test/read.cgi/$board/"

            val formBuilder = FormBody.Builder(Charset.forName("Shift_JIS"))
            confirmationData.hiddenParams.forEach { (key, value) ->
                formBuilder.addEncoded(key, URLEncoder.encode(value, "Shift_JIS"))
            }
            formBuilder.addEncoded(
                "submit",
                URLEncoder.encode("上記全てを承諾して書き込む", "Shift_JIS")
            )

            val request = Request.Builder()
                .url(url)
                .header("Referer", referer)
                .header("User-Agent", userAgent)
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute()
        } catch (e: Exception) {
            null
        }
    }
}
