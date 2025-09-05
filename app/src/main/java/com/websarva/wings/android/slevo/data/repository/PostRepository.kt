package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.remote.PostRemoteDataSource
import com.websarva.wings.android.slevo.data.util.PostParser
import com.websarva.wings.android.slevo.di.PersistentCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

// 書き込み結果の表現
sealed class PostResult {
    data class Success(val resNum: Int? = null) : PostResult() // 書き込み成功（レス番号付き）
    data class Confirm(val confirmationData: ConfirmationData) : PostResult() // 書き込み確認画面
    data class Error(val html: String, val message: String) : PostResult() // その他のエラー（WebView表示用）
    object Retry : PostResult() // MonaTicket エラー時の再試行
}

class PostRepository @Inject constructor(
    private val remoteDataSource: PostRemoteDataSource, // DIでDataSourceを受け取る
    private val cookieJar: PersistentCookieJar,
) {
    private suspend fun handlePostResponse(
        host: String,
        response: okhttp3.Response?,
    ): PostResult {
        if (response == null) {
            return PostResult.Error("", "ネットワークエラーが発生しました。")
        }
        return response.use {
            val errorHeader = it.header("x-chx-error")
            if (errorHeader == "0000 Confirmation[Broken MonaTicket]") {
                cookieJar.clearCookiesFor(host)
                return PostResult.Retry
            }
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

    suspend fun postTo5chFirstPhase(
        host: String,
        board: String,
        threadKey: String,
        name: String,
        mail: String,
        message: String
    ): PostResult = withContext(Dispatchers.IO) {
        try {
            var response = remoteDataSource.postFirstPhase(host, board, threadKey, name, mail, message)
            var result = handlePostResponse(host, response)
            if (result is PostResult.Retry) {
                response = remoteDataSource.postFirstPhase(host, board, threadKey, name, mail, message)
                result = handlePostResponse(host, response)
            }
            result
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
            handlePostResponse(host, response)
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
