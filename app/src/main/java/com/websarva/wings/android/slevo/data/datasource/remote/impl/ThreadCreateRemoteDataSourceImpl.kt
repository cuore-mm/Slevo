package com.websarva.wings.android.slevo.data.datasource.remote.impl

import com.websarva.wings.android.slevo.data.datasource.remote.ThreadCreateRemoteDataSource
import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.util.PostReplacer.replaceEmojisWithNCR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Remote data source implementation for thread creation requests.
 *
 * This class builds Shift_JIS form requests for thread creation first and second phases.
 */
@Singleton
class ThreadCreateRemoteDataSourceImpl @Inject constructor(
    private val client: OkHttpClient,
    @Named("UserAgent") private val userAgent: String
) : ThreadCreateRemoteDataSource {

    /**
     * Sends the first-phase thread creation request.
     */
    override suspend fun createThreadFirstPhase(
        host: String,
        board: String,
        subject: String,
        name: String,
        mail: String,
        message: String,
    ): Response? = withContext(Dispatchers.IO) {
        try {
            // --- Request setup ---
            val postUrl = "https://$host/test/bbs.cgi?guid=ON"
            val referer = "https://$host/test/read.cgi/$board/"
            val time = (System.currentTimeMillis() / 1000).toString()

            val formBody = FormBody.Builder(Charset.forName("Shift_JIS"))
                .add("bbs", replaceEmojisWithNCR(board))
                .add("time", replaceEmojisWithNCR(time))
                .add("subject", replaceEmojisWithNCR(subject))
                .add("FROM", replaceEmojisWithNCR(name))
                .add("mail", replaceEmojisWithNCR(mail))
                .add("MESSAGE", replaceEmojisWithNCR(message))
                .add("submit", "新規スレッド作成")
                .build()

            val request = Request.Builder()
                .url(postUrl)
                .header("Referer", referer)
                .header("User-Agent", userAgent)
                .post(formBody)
                .build()

            // --- Request execution ---
            client.newCall(request).execute()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Sends the confirmation-phase thread creation request.
     */
    override suspend fun createThreadSecondPhase(
        host: String,
        board: String,
        confirmationData: ConfirmationData,
    ): Response? = withContext(Dispatchers.IO) {
        try {
            // --- Request setup ---
            val url = "https://$host/test/bbs.cgi?guid=ON"
            val referer = "https://$host/test/read.cgi/$board/"

            val formBuilder = FormBody.Builder(Charset.forName("Shift_JIS"))
            confirmationData.hiddenParams.forEach { (key, value) ->
                formBuilder.add(key, replaceEmojisWithNCR(value))
            }
            formBuilder.add(
                "submit",
                "上記全てを承諾して書き込む"
            )

            val request = Request.Builder()
                .url(url)
                .header("Referer", referer)
                .header("User-Agent", userAgent)
                .post(formBuilder.build())
                .build()

            // --- Request execution ---
            client.newCall(request).execute()
        } catch (e: Exception) {
            null
        }
    }
}
