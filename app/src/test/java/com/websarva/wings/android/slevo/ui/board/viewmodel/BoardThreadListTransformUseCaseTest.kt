package com.websarva.wings.android.slevo.ui.board.viewmodel

import com.websarva.wings.android.slevo.data.datasource.local.dao.history.ThreadHistoryDao
import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.board.state.ThreadSortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BoardThreadListTransformUseCase] の検索・ソート・履歴統合を検証するテスト。
 */
class BoardThreadListTransformUseCaseTest {

    @Test
    fun filterAndSort_appliesSearchNgAndSort() {
        val useCase = BoardThreadListTransformUseCase()
        val threads = listOf(
            ThreadInfo(title = "あいう", key = "100", resCount = 10, momentum = 2.0),
            ThreadInfo(title = "テスト", key = "200", resCount = 30, momentum = 4.0),
            ThreadInfo(title = "除外", key = "300", resCount = 20, momentum = 3.0),
        )

        val result = useCase.filterAndSort(
            allThreads = threads,
            searchQuery = "てすと",
            threadTitleNg = listOf(null to Regex("除外")),
            boardId = 1L,
            sortKey = ThreadSortKey.RES_COUNT,
            ascending = false,
        )

        assertEquals(listOf("テスト"), result.map { it.title })
    }

    @Test
    fun mergeHistory_marksVisitedAndCalculatesNewRes() {
        val useCase = BoardThreadListTransformUseCase()
        val thread = ThreadInfo(title = "thread", key = "100", resCount = 20)
        val history = ThreadHistoryDao.HistorySimple(
            threadId = ThreadId.of("example.com", "test", "100"),
            resCount = 10,
            readState = ThreadReadState(prevResCount = 10, lastReadResNo = 10, firstNewResNo = 11),
        )

        val result = useCase.mergeHistory(listOf(thread), mapOf("100" to history))

        assertTrue(result.single().isVisited)
        assertEquals(10, result.single().newResCount)
    }

    @Test
    fun mergeHistory_usesLastReadResNo_whenFirstNewResNoIsMissingOrMismatched() {
        val useCase = BoardThreadListTransformUseCase()
        val threads = listOf(
            ThreadInfo(title = "missing", key = "100", resCount = 20),
            ThreadInfo(title = "mismatched", key = "200", resCount = 20),
        )
        val histories = mapOf(
            "100" to ThreadHistoryDao.HistorySimple(
                threadId = ThreadId.of("example.com", "test", "100"),
                resCount = 10,
                readState = ThreadReadState(lastReadResNo = 10, firstNewResNo = null),
            ),
            "200" to ThreadHistoryDao.HistorySimple(
                threadId = ThreadId.of("example.com", "test", "200"),
                resCount = 10,
                readState = ThreadReadState(lastReadResNo = 10, firstNewResNo = 15),
            ),
        )

        val result = useCase.mergeHistory(threads, histories)

        assertEquals(listOf(10, 10), result.map { it.newResCount })
    }
}
