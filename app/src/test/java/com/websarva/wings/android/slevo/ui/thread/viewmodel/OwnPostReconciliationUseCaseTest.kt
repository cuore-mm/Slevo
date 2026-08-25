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

    @Test
    fun reconcile_confirmedResNum_completesWithoutContentOrIdentityMatch() = runTest {
        val repository = mockk<PendingOwnPostRepository>(relaxed = true)
        coEvery { repository.findPending(scope) } returns listOf(pending().copy(confirmedResNum = 2))
        coEvery {
            repository.completeMatch(any(), any(), any(), any(), any(), any(), any(), any())
        } returns true
        val useCase = OwnPostReconciliationUseCase(repository, OwnPostMatcher())

        useCase.reconcile(
            scope,
            posts = listOf(post("unrelated", name = "other", email = "other", id = "other"), post("anything")),
            historyId = 1L,
            boardId = 1L,
            nowMillis = 100L,
        )

        coVerify(exactly = 1) {
            repository.completeMatch(
                pending = any(),
                matchedResNum = 2,
                date = any(),
                historyId = 1L,
                boardId = 1L,
                name = "name",
                email = "mail",
                postId = any(),
            )
        }
    }

    @Test
    fun reconcile_confirmedResNumNotInLoadedPosts_waitsWithoutAdvancingRange() = runTest {
        val repository = mockk<PendingOwnPostRepository>(relaxed = true)
        coEvery { repository.findPending(scope) } returns listOf(pending().copy(confirmedResNum = 3))
        val useCase = OwnPostReconciliationUseCase(repository, OwnPostMatcher())

        useCase.reconcile(scope, posts = listOf(post("anything")), 1L, 1L, 100L)

        coVerify(exactly = 0) { repository.updateLastCheckedResNum(any(), any()) }
        coVerify(exactly = 0) {
            repository.completeMatch(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun reconcile_posterIdZeroMatchesFallsBackToOriginalCandidatesAndIdentity() = runTest {
        val repository = mockk<PendingOwnPostRepository>(relaxed = true)
        coEvery { repository.findPending(scope) } returns listOf(
            pending().copy(name = "mine", posterIdHint = "missing"),
        )
        coEvery {
            repository.completeMatch(any(), any(), any(), any(), any(), any(), any(), any())
        } returns true
        val useCase = OwnPostReconciliationUseCase(repository, OwnPostMatcher())

        useCase.reconcile(
            scope,
            posts = listOf(
                post("message", name = "other", id = "id-1"),
                post("message", name = "mine", id = "id-2"),
            ),
            historyId = 1L,
            boardId = 1L,
            nowMillis = 100L,
        )

        coVerify(exactly = 1) {
            repository.completeMatch(
                pending = any(),
                matchedResNum = 2,
                date = any(),
                historyId = any(),
                boardId = any(),
                name = "mine",
                email = "mail",
                postId = any(),
            )
        }
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

    private fun post(
        content: String,
        name: String = "name",
        email: String = "mail",
        id: String = if (content == "message") "id-2" else "id-1",
    ) = ThreadPostUiModel(
        header = ThreadPostUiModel.Header(
            name = name,
            email = email,
            date = "2024/01/01 00:00:00",
            id = id,
        ),
        body = ThreadPostUiModel.Body(content),
    )
}
