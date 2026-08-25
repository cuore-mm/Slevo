package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.remote.PostRemoteDataSource
import com.websarva.wings.android.slevo.data.model.PostReceipt
import com.websarva.wings.android.slevo.data.util.PostParser
import com.websarva.wings.android.slevo.data.util.PostReceiptParser
import com.websarva.wings.android.slevo.di.PersistentCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Response
import com.websarva.wings.android.slevo.core.log.AppLogger
import javax.inject.Inject

/** 投稿フォームのHTTP処理結果を表すsealed hierarchy。 */
sealed class PostResult {
    /** 投稿成功と、取得できた任意の照合証拠を表す。 */
    data class Success(val receipt: PostReceipt = PostReceipt()) : PostResult()

    /** サーバーが返した投稿確認画面を表す。 */
    data class Confirm(val confirmationData: ConfirmationData) : PostResult()

    /** その他のエラーとWebView表示用HTMLを表す。 */
    data class Error(val html: String, val message: String) : PostResult()
}

/** 既存スレッドへの投稿を実行し、成功応答の照合証拠を上位層へ渡すRepository。 */
class PostRepository @Inject constructor(
    private val remoteDataSource: PostRemoteDataSource, // DIでDataSourceを受け取る
    private val cookieJar: PersistentCookieJar,
    private val logger: AppLogger,
    private val postReceiptParser: PostReceiptParser,
) {
    /** 投稿成功レスポンスを閉じる前に本文と照合証拠を抽出する。 */
    private suspend fun handlePostResponse(
        response: okhttp3.Response?,
        expectedPostPlace: String,
    ): PostResult {
        if (response == null) {
            return PostResult.Error("", "ネットワークエラーが発生しました。")
        }
        return response.use {
            val receipt = postReceiptParser.parse(it.headers, expectedPostPlace)
            val html = it.body?.string() ?: return PostResult.Error("", "空のレスポンスです。")
            if (!it.isSuccessful) {
                return PostResult.Error(html, "サーバーエラー: ${it.code}")
            }
            when (val result = PostParser.parseWriteResponse(html)) {
                is PostResult.Success -> PostResult.Success(receipt)
                else -> result
            }
        }
    }

    private val brokenTicketRegex =
        Regex("""Broken\s*MonaTicket""", RegexOption.IGNORE_CASE)

    /** MonaTicketの失効を示すレスポンスヘッダーまたはcookieを検出する。 */
    private fun Response.isBrokenMonaTicket(): Boolean {
        val headerHit = headers("x-chx-error")
            .any { brokenTicketRegex.containsMatchIn(it) }
        val cookieHit = headers("set-cookie")
            .any { sc ->
                sc.startsWith("MonaTicket=", ignoreCase = true) &&
                        sc.contains("Expires=", ignoreCase = true) // 過去期限で失効させている合図
            }
        logger.d("headerHit: $headerHit, cookieHit: $cookieHit")
        return headerHit || cookieHit
    }

    /** 返信投稿の確認画面または成功応答を取得する。 */
    suspend fun postTo5chFirstPhase(
        host: String,
        board: String,
        threadKey: String,
        name: String,
        mail: String,
        message: String
    ): PostResult = withContext(Dispatchers.IO) {
        try {
            val response = remoteDataSource.postFirstPhase(host, board, threadKey, name, mail, message)

            if (response != null && response.isBrokenMonaTicket()) {
                response.close() // いったん閉じる
                cookieJar.clear(host) // MonaTicket だけ消す実装ならなお良い（clearMonaTicket 等）
                val retry = remoteDataSource.postFirstPhase(host, board, threadKey, name, mail, message)
                handlePostResponse(retry, "$board/$threadKey")
            } else {
                handlePostResponse(response, "$board/$threadKey")
            }
        } catch (e: Exception) {
            logger.e(message = "初回投稿リクエスト失敗", throwable = e)
            PostResult.Error("", e.message ?: "不明なエラー")
        }
    }

    /** 確認画面のhidden parameterを送信して返信投稿を確定する。 */
    suspend fun postTo5chSecondPhase(
        host: String,
        board: String,
        threadKey: String,
        confirmationData: ConfirmationData
    ): PostResult = withContext(Dispatchers.IO) {
        try {
            val response = remoteDataSource.postSecondPhase(host, board, threadKey, confirmationData)
            handlePostResponse(response, "$board/$threadKey")
        } catch (e: Exception) {
            logger.e(message = "2回目投稿リクエスト失敗", throwable = e)
            PostResult.Error("", e.message ?: "不明なエラー")
        }
    }

}

/** 投稿確認画面から次の書き込み要求へ引き継ぐデータ。 */
data class ConfirmationData(
    val html: String,
    val hiddenParams: Map<String, String>
)
