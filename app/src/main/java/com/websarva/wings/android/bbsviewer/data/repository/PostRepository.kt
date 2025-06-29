package com.websarva.wings.android.bbsviewer.data.repository

import android.util.Log
import com.websarva.wings.android.bbsviewer.data.datasource.remote.PostRemoteDataSource
import com.websarva.wings.android.bbsviewer.data.util.PostParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

// 書き込み結果の表現
sealed class PostResult {
    object Success : PostResult()
    data class Error(val message: String) : PostResult()
}

class PostRepository @Inject constructor(
    private val remoteDataSource: PostRemoteDataSource // DIでDataSourceを受け取る
) {
    suspend fun postTo5chFirstPhase(
        host: String,
        board: String,
        threadKey: String,
        name: String,
        mail: String,
        message: String
    ): ConfirmationData? = withContext(Dispatchers.IO) {
        try {
            remoteDataSource.postFirstPhase(host, board, threadKey, name, mail, message)
                ?.use { response ->
                    if (!response.isSuccessful) return@withContext null

                    val body = response.body ?: return@withContext null
                    val html = body.string()

                    // Cookieの抽出は不要になる
                    val hiddenParams = PostParser.extractHiddenParams(html)

                    Log.i("PostRepository", "html1: $html")
                    Log.i("PostRepository", "hiddenParams: $hiddenParams")

                    ConfirmationData(
                        html = html,
                        hiddenParams = hiddenParams
                    )
                }
        } catch (e: Exception) {
            Log.e("PostRepository", "初回投稿リクエスト失敗", e)
            null
        }
    }

    suspend fun postTo5chSecondPhase(
        host: String,
        board: String,
        threadKey: String,
        confirmationData: ConfirmationData
    ): PostResult = withContext(Dispatchers.IO) {
        try {
            // 第2引数のcookieは不要になる
            remoteDataSource.postSecondPhase(host, board, threadKey, confirmationData)
                ?.use { response ->
                    val body = response.body?.string()
                        ?: return@withContext PostResult.Error("空レスポンス")
                    if (PostParser.isSuccess(body)) PostResult.Success
                    else PostResult.Error("書き込み失敗")
                } ?: PostResult.Error("レスポンスがありません")
        } catch (e: Exception) {
            Log.e("PostRepository", "2回目投稿リクエスト失敗", e)
            PostResult.Error(e.message ?: "不明なエラー")
        }
    }
}

data class ConfirmationData(
    val html: String,
    val hiddenParams: List<PostParser.HiddenParam>
)
