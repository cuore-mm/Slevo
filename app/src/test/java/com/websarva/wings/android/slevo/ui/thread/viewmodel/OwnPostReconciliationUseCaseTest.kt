package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PendingOwnPostEntity
import com.websarva.wings.android.slevo.data.model.OwnPostThreadScope
import com.websarva.wings.android.slevo.data.repository.PendingOwnPostRepository
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** [OwnPostReconciliationUseCase] の範囲限定と状態遷移を検証する。 */
class OwnPostReconciliationUseCaseTest {
    private val scope = OwnPostThreadScope("provider", "board", "thread")

    @Test
    fun reconcile_withoutNewPosts_doesNotCompareOrUpdate() = runTest {
        val repository = mockk<PendingOwnPostRepository>(relaxed = true)
        coEvery { repository.findPending(scope) } returns listOf(pending(lastCheckedResNum = 2))
        val useCase = OwnPostReconciliationUseCase(repository, OwnPostMatcher())

        useCase.reconcile(scope, posts = listOf(post("message"), post("old")), 1L, 1L, 100L)

        coVerify(exactly = 0) { repository.updateLastCheckedResNum(any(), any()) }
        coVerify(exactly = 0) {
            repository.completeMatch(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun reconcile_uniqueCandidate_completesAndClaimsResponseNumber() = runTest {
        val repository = mockk<PendingOwnPostRepository>(relaxed = true)
        coEvery { repository.findPending(scope) } returns listOf(pending())
        coEvery {
            repository.completeMatch(any(), any(), any(), any(), any(), any(), any(), any())
        } returns true
        val useCase = OwnPostReconciliationUseCase(repository, OwnPostMatcher())

        useCase.reconcile(scope, posts = listOf(post("other"), post("message")), 1L, 1L, 100L)

        coVerify(exactly = 1) {
            repository.completeMatch(
                pending = any(),
                matchedResNum = 2,
                date = any(),
                historyId = 1L,
                boardId = 1L,
                name = "name",
                email = "mail",
                postId = "id-2",
            )
        }
    }

    @Test
    fun reconcile_multipleCandidates_keepsPendingWithoutAdvancingRange() = runTest {
        val repository = mockk<PendingOwnPostRepository>(relaxed = true)
        coEvery { repository.findPending(scope) } returns listOf(pending())
        val useCase = OwnPostReconciliationUseCase(repository, OwnPostMatcher())

        useCase.reconcile(scope, posts = listOf(post("message"), post("message")), 1L, 1L, 100L)

        coVerify(exactly = 0) { repository.updateLastCheckedResNum(any(), any()) }
        coVerify(exactly = 0) {
            repository.completeMatch(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun reconcile_expiredPending_doesNotMatch() = runTest {
        val repository = mockk<PendingOwnPostRepository>(relaxed = true)
        coEvery { repository.findPending(scope) } returns listOf(pending(expiresAt = 100L))
        val useCase = OwnPostReconciliationUseCase(repository, OwnPostMatcher())

        useCase.reconcile(scope, posts = listOf(post("message")), 1L, 1L, 100L)

        coVerify(exactly = 1) { repository.expirePending(scope, 100L) }
        coVerify(exactly = 0) { repository.completeMatch(any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    private fun pending(
        content: String = "message",
        lastCheckedResNum: Int = 0,
        expiresAt: Long = 1_000L,
    ) = PendingOwnPostEntity(
        providerId = scope.providerId,
        boardKey = scope.boardKey,
        threadKey = scope.threadKey,
        content = content,
        name = "name",
        email = "mail",
        baseResCount = 0,
        lastCheckedResNum = lastCheckedResNum,
        submittedAt = 1L,
        expiresAt = expiresAt,
    )

    private fun post(content: String) = ThreadPostUiModel(
        header = ThreadPostUiModel.Header(
            name = "name",
            email = "mail",
            date = "2024/01/01 00:00:00",
            id = if (content == "message") "id-2" else "id-1",
        ),
        body = ThreadPostUiModel.Body(content),
    )
}
