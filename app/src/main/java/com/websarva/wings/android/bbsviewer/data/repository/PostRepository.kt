package com.websarva.wings.android.bbsviewer.data.repository

import android.util.Log
import com.websarva.wings.android.bbsviewer.data.util.PostParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.Charset
import javax.inject.Inject

// 書き込み結果の表現
sealed class PostResult {
    object Success : PostResult()
    data class Error(val message: String) : PostResult()
}

class PostRepository @Inject constructor(
    private val client: OkHttpClient
) {

    /**
     * 初回投稿（確認用リクエスト）
     * リクエストを送り、確認画面HTMLから hidden パラメータと Cookie を抽出して返す。
     */
    suspend fun postTo5chFirstPhase(
        host: String,
        board: String,
        threadKey: String,
        name: String,
        mail: String,
        message: String
    ): ConfirmationData? = withContext(Dispatchers.IO) {
        try {
            val postUrl = "https://$host/test/bbs.cgi?guid=ON"
            val referer = "https://$host/test/read.cgi/$board/$threadKey"
            val time = (System.currentTimeMillis() / 1000).toString()

            val form1 = FormBody.Builder(Charset.forName("Shift_JIS"))
                .addEncoded("bbs", board)
                .addEncoded("key", threadKey)
                .addEncoded("time", time)
                .addEncoded("FROM", URLEncoder.encode(name, "Shift_JIS"))
                .addEncoded("mail", URLEncoder.encode(mail, "Shift_JIS"))
                .addEncoded("MESSAGE", URLEncoder.encode(message, "Shift_JIS"))
                .addEncoded("submit", URLEncoder.encode("書き込む", "Shift_JIS"))
                .build()

            val request1 = Request.Builder()
                .url(postUrl)
                .header("Referer", referer)
                .header("User-Agent", "Monazilla/1.00 (BBSViewer/1.00)")
                .post(form1)
                .build()

            client.newCall(request1).execute().use { response1 ->
                if (!response1.isSuccessful) {
                    return@withContext null
                }

                val body = response1.body ?: return@withContext null
                val html1 = body.string()

                // Cookie と hidden パラメータを抽出
                val cookies = PostParser.extractCookies(response1.headers.values("Set-Cookie"))
                val hiddenParams = PostParser.extractHiddenParams(html1)

                Log.i("PostRepository", "html1: $html1")
                Log.i("PostRepository", "hiddenParams: $hiddenParams")
                Log.i("PostRepository", "cookies: $cookies")


                // HTML も含めて返す
                ConfirmationData(
                    html = html1,
                    hiddenParams = hiddenParams,
                    cookies = cookies
                )
            }
        } catch (e: Exception) {
            Log.e("PostRepository", "初回投稿リクエスト失敗", e)
            null
        }
    }


    /**
     * 2回目投稿（書き込み実行）
     * 1回目の確認用リクエストから得た hidden パラメータと Cookie を使用して最終投稿を行う。
     */
    suspend fun postTo5chSecondPhase(
        host: String,
        board: String,
        threadKey: String,
        confirmationData: ConfirmationData
    ): PostResult = withContext(Dispatchers.IO) {
        val url = "https://$host/test/bbs.cgi?guid=ON"
        val referer = "https://$host/test/read.cgi/$board/$threadKey"

        // 1回目で抽出した hiddenParams をすべて追加
        val formBuilder = FormBody.Builder(Charset.forName("Shift_JIS"))
        confirmationData.hiddenParams.forEach { (key, value) ->
            formBuilder.addEncoded(key, URLEncoder.encode(value, "Shift_JIS"))
        }
        // submit ボタンの値も追加
        formBuilder.addEncoded(
            "submit",
            URLEncoder.encode("上記全てを承諾して書き込む", "Shift_JIS")
        )

        val request = Request.Builder()
            .url(url)
            .header("Referer", referer)
            .header("User-Agent", "Monazilla/1.00 (BBSViewer/1.00)")
            //.header("Cookie", confirmationData.cookies.joinToString("; "))
            .post(formBuilder.build())
            .build()

        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string() ?: return@withContext PostResult.Error("空レスポンス")
            if (PostParser.isSuccess(body)) PostResult.Success
            else PostResult.Error("書き込み失敗")
        }
    }
}

data class ConfirmationData(
    val html: String,
    val hiddenParams: List<PostParser.HiddenParam>,
    val cookies: List<String>
)
