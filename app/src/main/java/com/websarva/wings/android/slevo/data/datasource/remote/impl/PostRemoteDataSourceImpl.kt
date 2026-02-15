package com.websarva.wings.android.slevo.data.datasource.remote.impl

import com.websarva.wings.android.slevo.data.datasource.remote.PostRemoteDataSource
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

/**
 * Remote data source implementation for reply posting requests.
 *
 * This class builds Shift_JIS form requests for both phases and executes them via OkHttp.
 */
class PostRemoteDataSourceImpl @Inject constructor(
    private val client: OkHttpClient,
    @Named("UserAgent") private val userAgent: String
) : PostRemoteDataSource {

    /**
     * Sends the first-phase reply post request.
     */
    override suspend fun postFirstPhase(
        host: String,
        board: String,
        threadKey: String,
        name: String,
        mail: String,
        message: String
    ): Response? = withContext(Dispatchers.IO) {
        try {
            // --- Request setup ---
            val postUrl = "https://$host/test/bbs.cgi?guid=ON"
            val referer = "https://$host/test/read.cgi/$board/$threadKey"
            val time = (System.currentTimeMillis() / 1000).toString()

            val formBody = FormBody.Builder(Charset.forName("Shift_JIS"))
                .add("bbs", replaceEmojisWithNCR(board))
                .add("key", replaceEmojisWithNCR(threadKey))
                .add("time", replaceEmojisWithNCR(time))
                .add("FROM", replaceEmojisWithNCR(name))
                .add("mail", replaceEmojisWithNCR(mail))
                .add("MESSAGE", replaceEmojisWithNCR(message))
                .add("submit", "書き込む")
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
     * Sends the confirmation-phase reply post request.
     */
    override suspend fun postSecondPhase(
        host: String,
        board: String,
        threadKey: String,
        confirmationData: ConfirmationData
    ): Response? = withContext(Dispatchers.IO) {
        try {
            // --- Request setup ---
            val url = "https://$host/test/bbs.cgi?guid=ON"
            val referer = "https://$host/test/read.cgi/$board/$threadKey"

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
