package com.websarva.wings.android.slevo.ui.common.postdialog

import com.websarva.wings.android.slevo.data.repository.ConfirmationData
import com.websarva.wings.android.slevo.data.repository.PostResult

/**
 * PostDialogの投稿処理を差し替えるための実行インターフェース。
 */
interface PostDialogExecutor {
    /**
     * 投稿の第一段階（確認画面取得）を実行する。
     */
    suspend fun postFirstPhase(request: PostDialogFirstPhaseRequest): PostResult

    /**
     * 投稿の第二段階（書き込み実行）を実行する。
     */
    suspend fun postSecondPhase(request: PostDialogSecondPhaseRequest): PostResult
}

/**
 * PostDialogの第一段階投稿リクエスト。
 */
data class PostDialogFirstPhaseRequest(
    val host: String,
    val board: String,
    val threadKey: String?,
    val title: String?,
    val formState: PostFormState,
)

/**
 * PostDialogの第二段階投稿リクエスト。
 */
data class PostDialogSecondPhaseRequest(
    val host: String,
    val board: String,
    val threadKey: String?,
    val confirmationData: ConfirmationData,
)
