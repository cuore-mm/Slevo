package com.websarva.wings.android.bbsviewer.data.repository

import android.util.Log
import com.websarva.wings.android.bbsviewer.data.datasource.remote.PostRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.util.PostParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

// 書き込み結果の表現
sealed class PostResult {
    data class Success(val resNum: Int? = null) : PostResult() // 書き込み成功（レス番号付き）
    data class Confirm(val confirmationData: ConfirmationData) : PostResult() // 書き込み確認画面
    data class Error(val html: String, val message: String) : PostResult() // その他のエラー（WebView表示用）
}

class PostRepository @Inject constructor(
    private val remoteDataSource: PostRemoteDataSource // DIでDataSourceを受け取る
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
            handlePostResponse(response)
        } catch (e: Exception) {
            Log.e("PostRepository", "初回投稿リクエスト失敗", e)
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
            Log.e("PostRepository", "2回目投稿リクエスト失敗", e)
            PostResult.Error("", e.message ?: "不明なエラー")
        }
    }

}

data class ConfirmationData(
    val html: String,
    val hiddenParams: Map<String, String>
)
