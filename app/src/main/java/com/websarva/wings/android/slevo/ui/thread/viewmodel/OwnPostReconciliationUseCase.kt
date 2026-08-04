package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.model.OwnPostThreadScope
import com.websarva.wings.android.slevo.data.repository.PendingOwnPostRepository
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import javax.inject.Inject

/**
 * 指定スレッドの未確定投稿を、取得済みレスの未確認範囲だけで照合するユースケース。
 *
 * 別スレッドのpendingを読み込まず、一意な候補だけを既存投稿履歴へ確定する。
 */
class OwnPostReconciliationUseCase @Inject constructor(
    private val pendingRepository: PendingOwnPostRepository,
    private val matcher: OwnPostMatcher,
) {
    /** 指定スレッドのPENDING投稿を取得結果へ照合する。 */
    suspend fun reconcile(
        scope: OwnPostThreadScope,
        posts: List<ThreadPostUiModel>,
        historyId: Long,
        boardId: Long,
        nowMillis: Long,
    ) {
        // --- State maintenance ---
        pendingRepository.expirePending(scope, nowMillis)
        pendingRepository.cleanupTerminal(
            submittedBefore = nowMillis - PendingOwnPostRepository.TERMINAL_RETENTION_MILLIS,
        )
        val pendingPosts = pendingRepository.findPending(scope)
        val claimedResNumbers = mutableSetOf<Int>()

        // --- Candidate matching ---
        pendingPosts.forEach { pending ->
            if (nowMillis >= pending.expiresAt) {
                return@forEach
            }
            val start = maxOf(pending.baseResCount + 1, pending.lastCheckedResNum + 1)
            val end = posts.size
            if (start > end) {
                return@forEach
            }
            val candidates = (start..end)
                .filter { resNum -> resNum !in claimedResNumbers }
                .filter { resNum -> matcher.matches(pending, posts[resNum - 1]) }
            when (candidates.size) {
                0 -> pendingRepository.updateLastCheckedResNum(pending.id, end)
                1 -> {
                    val resNum = candidates.single()
                    val post = posts[resNum - 1]
                    val completed = pendingRepository.completeMatch(
                        pending = pending,
                        matchedResNum = resNum,
                        date = parseDateToUnix(post.header.date),
                        historyId = historyId,
                        boardId = boardId,
                        name = pending.name,
                        email = pending.email,
                        postId = post.header.id,
                    )
                    if (completed) {
                        claimedResNumbers += resNum
                    }
                }
                else -> {
                    // 同一本文の複数候補は推測で確定せず、次回ロードで再検証する。
                }
            }
        }
    }
}
