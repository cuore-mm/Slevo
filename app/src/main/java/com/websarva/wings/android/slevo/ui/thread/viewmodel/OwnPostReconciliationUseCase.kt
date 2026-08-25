package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PendingOwnPostEntity
import com.websarva.wings.android.slevo.data.model.OwnPostThreadScope
import com.websarva.wings.android.slevo.data.repository.PendingOwnPostRepository
import com.websarva.wings.android.slevo.data.util.OwnPostDateParser
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
            val availableRange = (start..end)
                .filter { resNum -> resNum !in claimedResNumbers }
            val confirmedResNum = pending.confirmedResNum
            if (confirmedResNum != null) {
                // サーバーが返したレス番号はdat反映前なら次回取得まで待機する。
                if (confirmedResNum in 1..end && confirmedResNum !in claimedResNumbers && completeCandidate(
                        pending = pending,
                        resNum = confirmedResNum,
                        posts = posts,
                        historyId = historyId,
                        boardId = boardId,
                    )
                ) {
                    claimedResNumbers += confirmedResNum
                }
                return@forEach
            }

            // --- Content candidate ---
            var candidates = availableRange.filter { resNum ->
                matcher.matchesContent(pending, posts[resNum - 1])
            }
            if (candidates.isEmpty()) {
                pendingRepository.updateLastCheckedResNum(pending.id, end)
                return@forEach
            }

            // --- Server date evidence ---
            if (pending.serverPostDateMillis != null) {
                candidates = candidates.filter { resNum ->
                    matcher.matchesServerDate(pending, posts[resNum - 1])
                }
                if (candidates.isEmpty()) {
                    // 本文は一致したが日時証拠と矛盾する場合は確認位置を進めない。
                    return@forEach
                }
            }

            if (candidates.size == 1) {
                if (completeCandidate(pending, candidates.single(), posts, historyId, boardId)) {
                    claimedResNumbers += candidates.single()
                }
                return@forEach
            }

            // --- Poster ID tie-breaker ---
            val posterIdCandidates = if (pending.posterIdHint?.trim().isNullOrEmpty()) {
                candidates
            } else {
                candidates.filter { resNum -> matcher.matchesPosterId(pending, posts[resNum - 1]) }
                    .ifEmpty { candidates }
            }
            candidates = posterIdCandidates
            if (candidates.size == 1) {
                if (completeCandidate(pending, candidates.single(), posts, historyId, boardId)) {
                    claimedResNumbers += candidates.single()
                }
                return@forEach
            }

            // --- Input identity tie-breaker ---
            val identityCandidates = candidates.filter { resNum ->
                matcher.matchesIdentity(pending, posts[resNum - 1])
            }
            if (identityCandidates.size == 1) {
                if (completeCandidate(pending, identityCandidates.single(), posts, historyId, boardId)) {
                    claimedResNumbers += identityCandidates.single()
                }
            } else {
                // 同一本文の複数候補は推測で確定せず、次回ロードで再検証する。
            }
        }
    }

    /** 一意に選ばれた取得レスを投稿履歴へ原子的に確定する。 */
    private suspend fun completeCandidate(
        pending: PendingOwnPostEntity,
        resNum: Int,
        posts: List<ThreadPostUiModel>,
        historyId: Long,
        boardId: Long,
    ): Boolean {
        val post = posts[resNum - 1]
        return pendingRepository.completeMatch(
            pending = pending,
            matchedResNum = resNum,
            date = OwnPostDateParser.parseDatDate(post.header.date)
                ?: parseDateToUnix(post.header.date),
            historyId = historyId,
            boardId = boardId,
            name = pending.name,
            email = pending.email,
            postId = post.header.id,
        )
    }
}
