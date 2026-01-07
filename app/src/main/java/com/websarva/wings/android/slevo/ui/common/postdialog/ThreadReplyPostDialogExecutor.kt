package com.websarva.wings.android.slevo.ui.common.postdialog

import com.websarva.wings.android.slevo.data.repository.PostRepository
import com.websarva.wings.android.slevo.data.repository.PostResult
import javax.inject.Inject

/**
 * スレッド返信用のPostDialogExecutor実装。
 *
 * 返信投稿の1段階/2段階処理をPostRepositoryへ委譲する。
 */
class ThreadReplyPostDialogExecutor @Inject constructor(
    private val postRepository: PostRepository,
) : PostDialogExecutor {

    /**
     * 返信投稿の第一段階リクエストを実行する。
     */
    override suspend fun postFirstPhase(request: PostDialogFirstPhaseRequest): PostResult {
        val threadKey = request.threadKey
        if (threadKey.isNullOrBlank()) {
            // 投稿先スレッドが特定できない場合はエラー扱いにする。
            return PostResult.Error("", "投稿先スレッドが不明です。")
        }
        val form = request.formState
        return postRepository.postTo5chFirstPhase(
            request.host,
            request.board,
            threadKey,
            form.name,
            form.mail,
            form.message,
        )
    }

    /**
     * 返信投稿の第二段階リクエストを実行する。
     */
    override suspend fun postSecondPhase(request: PostDialogSecondPhaseRequest): PostResult {
        val threadKey = request.threadKey
        if (threadKey.isNullOrBlank()) {
            // 投稿先スレッドが特定できない場合はエラー扱いにする。
            return PostResult.Error("", "投稿先スレッドが不明です。")
        }
        return postRepository.postTo5chSecondPhase(
            request.host,
            request.board,
            threadKey,
            request.confirmationData,
        )
    }
}
