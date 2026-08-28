package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.model.ReplyInfo
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [ThreadContentLoadUseCase] の変換結果を検証するテスト。
 */
class ThreadContentLoadUseCaseTest {

    @Test
    fun load_returnsDerivedResultFromThreadData() = runTest {
        val refreshUseCase = mockk<ThreadRefreshUseCase>()
        coEvery { refreshUseCase.refresh(any()) } returns ThreadRefreshResult(
            posts = listOf(
                ReplyInfo(name = "name1", email = "", date = "2024/01/01 00:00:00", id = "id1", content = "root"),
                ReplyInfo(name = "name2", email = "", date = "2024/01/01 00:00:01", id = "id2", content = ">>1 child"),
            ),
            title = "title",
            previousResCount = null,
        )

        val result = ThreadContentLoadUseCase(refreshUseCase).load(
            boardUrl = "https://example.com/test/",
            threadKey = "123",
            onProgress = {},
        )

        requireNotNull(result)
        assertEquals("title", result.threadTitle)
        assertEquals(2, result.resCount)
        assertEquals(mapOf("id1" to 1, "id2" to 1), result.idCountMap)
        assertEquals(listOf(1, 2), result.treeOrder)
        assertEquals(mapOf(1 to 0, 2 to 1), result.treeDepthMap)
    }

    @Test
    fun load_returnsNullWhenRepositoryReturnsNull() = runTest {
        val refreshUseCase = mockk<ThreadRefreshUseCase>()
        coEvery { refreshUseCase.refresh(any()) } returns null

        val result = ThreadContentLoadUseCase(refreshUseCase).load(
            boardUrl = "https://example.com/test/",
            threadKey = "123",
            onProgress = {},
        )

        assertNull(result)
    }
}
