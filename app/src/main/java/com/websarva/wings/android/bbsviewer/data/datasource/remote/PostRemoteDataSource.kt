package com.websarva.wings.android.bbsviewer.data.datasource.remote

import com.websarva.wings.android.bbsviewer.data.repository.ConfirmationData
import okhttp3.Response

/**
 * 投稿に関するリモートデータソース
 */
interface PostRemoteDataSource {
    /**
     * 初回投稿（確認画面取得）リクエスト
     */
    suspend fun postFirstPhase(
        host: String,
        board: String,
        threadKey: String,
        name: String,
        mail: String,
        message: String
    ): Response?

    /**
     * 2回目投稿（書き込み実行）リクエスト
     */
    suspend fun postSecondPhase(
        host: String,
        board: String,
        threadKey: String,
        confirmationData: ConfirmationData
    ): Response?
}
