package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.datasource.remote.ThreadCreateRemoteDataSource
import com.websarva.wings.android.slevo.data.util.PostParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class ThreadCreateRepository @Inject constructor(
    private val remoteDataSource: ThreadCreateRemoteDataSource
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
            PostParser.parseWriteResponse(html)
        }
    }

    suspend fun createThreadFirstPhase(
        host: String,
        board: String,
        subject: String,
        name: String,
        mail: String,
        message: String,
    ): PostResult = withContext(Dispatchers.IO) {
        try {
            val response =
                remoteDataSource.createThreadFirstPhase(host, board, subject, name, mail, message)
            handlePostResponse(response)
        } catch (e: Exception) {
            Timber.e(e, "スレ立て初回リクエスト失敗")
            PostResult.Error("", e.message ?: "不明なエラー")
        }
    }

    suspend fun createThreadSecondPhase(
        host: String,
        board: String,
        confirmationData: ConfirmationData,
    ): PostResult = withContext(Dispatchers.IO) {
        try {
            val response = remoteDataSource.createThreadSecondPhase(host, board, confirmationData)
            handlePostResponse(response)
        } catch (e: Exception) {
            Timber.e(e, "スレ立て2回目リクエスト失敗")
            PostResult.Error("", e.message ?: "不明なエラー")
        }
    }
}
