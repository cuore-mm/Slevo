package com.websarva.wings.android.slevo.ui.common.postdialog

import com.websarva.wings.android.slevo.data.repository.PostResult
import com.websarva.wings.android.slevo.data.repository.ThreadCreateRepository
import javax.inject.Inject

/**
 * スレ立て用のPostDialogExecutor実装。
 *
 * スレ立ての1段階/2段階処理をThreadCreateRepositoryへ委譲する。
 */
class ThreadCreatePostDialogExecutor @Inject constructor(
    private val threadCreateRepository: ThreadCreateRepository,
) : PostDialogExecutor {

    /**
     * スレ立ての第一段階リクエストを実行する。
     */
    override suspend fun postFirstPhase(request: PostDialogFirstPhaseRequest): PostResult {
        val form = request.formState
        val title = request.title ?: form.title
        return threadCreateRepository.createThreadFirstPhase(
            request.host,
            request.board,
            title,
            form.name,
            form.mail,
            form.message,
        )
    }

    /**
     * スレ立ての第二段階リクエストを実行する。
     */
    override suspend fun postSecondPhase(request: PostDialogSecondPhaseRequest): PostResult {
        return threadCreateRepository.createThreadSecondPhase(
            request.host,
            request.board,
            request.confirmationData,
        )
    }
}
