package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.remote.PostRemoteDataSource
import com.websarva.wings.android.slevo.data.util.PostParser
import com.websarva.wings.android.slevo.di.PersistentCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Response
import timber.log.Timber
import javax.inject.Inject

// 書き込み結果の表現
sealed class PostResult {
    data class Success(val resNum: Int? = null) : PostResult() // 書き込み成功（レス番号付き）
    data class Confirm(val confirmationData: ConfirmationData) : PostResult() // 書き込み確認画面
    data class Error(val html: String, val message: String) : PostResult() // その他のエラー（WebView表示用）
}

class PostRepository @Inject constructor(
    private val remoteDataSource: PostRemoteDataSource, // DIでDataSourceを受け取る
    private val cookieJar: PersistentCookieJar,
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

    private val BROKEN_TICKET_REGEX =
        Regex("""Broken\s*MonaTicket""", RegexOption.IGNORE_CASE)

    private fun Response.isBrokenMonaTicket(): Boolean {
        val headerHit = headers("x-chx-error")
            .any { BROKEN_TICKET_REGEX.containsMatchIn(it) }
        val cookieHit = headers("set-cookie")
            .any { sc ->
                sc.startsWith("MonaTicket=", ignoreCase = true) &&
                        sc.contains("Expires=", ignoreCase = true) // 過去期限で失効させている合図
            }
        Timber.d("headerHit: $headerHit, cookieHit: $cookieHit")
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
            Timber.e(e, "初回投稿リクエスト失敗")
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
            Timber.e(e, "2回目投稿リクエスト失敗")
            PostResult.Error("", e.message ?: "不明なエラー")
        }
    }

}

data class ConfirmationData(
    val html: String,
    val hiddenParams: Map<String, String>
)
