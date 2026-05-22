package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.remote.PostRemoteDataSource
import com.websarva.wings.android.slevo.data.util.PostParser
import com.websarva.wings.android.slevo.di.PersistentCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Response
import com.websarva.wings.android.slevo.core.log.AppLogger
import javax.inject.Inject

class PostRepository @Inject constructor(
    private val remoteDataSource: PostRemoteDataSource, // DIでDataSourceを受け取る
    private val cookieJar: PersistentCookieJar,
    private val logger: AppLogger
) {
    private suspend fun handlePostResponse(response: okhttp3.Response?): PostResult {
        if (response == null) {
            return PostResult.Error("", "ネットワークエラーが発生しました。")
        }
        return response.use {
            val html = it.body?.string() ?: return PostResult.Error("", "空のレスポンスです。")
            if (!it.isSuccessful) {
                return PostResult.Error(html, "サーバーエラー: ${it.code}")
            }
            val resNum = it.header("x-resnum")?.toIntOrNull()
            when (val result = PostParser.parseWriteResponse(html)) {
                is PostResult.Success -> PostResult.Success(resNum)
                else -> result
            }
        }
    }

    private val brokenTicketRegex =
        Regex("""Broken\s*MonaTicket""", RegexOption.IGNORE_CASE)

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
                handlePostResponse(retry)
            } else {
                handlePostResponse(response)
            }
        } catch (e: Exception) {
            logger.e(message = "初回投稿リクエスト失敗", throwable = e)
            PostResult.Error("", e.message ?: "不明なエラー")
        }
    }

    suspend fun postTo5chSecondPhase(
        host: String,
        board: String,
        threadKey: String,
        confirmationData: ConfirmationData
    ): PostResult = withContext(Dispatchers.IO) {
        try {
            val response = remoteDataSource.postSecondPhase(host, board, threadKey, confirmationData)
            handlePostResponse(response)
        } catch (e: Exception) {
            logger.e(message = "2回目投稿リクエスト失敗", throwable = e)
            PostResult.Error("", e.message ?: "不明なエラー")
        }
    }

}

data class ConfirmationData(
    val html: String,
    val hiddenParams: Map<String, String>
)
