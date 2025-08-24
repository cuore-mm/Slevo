package com.websarva.wings.android.slevo.data.datasource.remote

import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import okhttp3.Response

/**
 * スレッド作成に関するリモートデータソース
 */
interface ThreadCreateRemoteDataSource {
    suspend fun createThreadFirstPhase(
        host: String,
        board: String,
        subject: String,
        name: String,
        mail: String,
        message: String,
    ): Response?

    suspend fun createThreadSecondPhase(
        host: String,
        board: String,
        confirmationData: ConfirmationData,
    ): Response?
}
