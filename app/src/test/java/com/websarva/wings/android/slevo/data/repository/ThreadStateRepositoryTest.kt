package com.websarva.wings.android.slevo.data.repository

import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.repository.fake.FakeThreadStateDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ThreadStateRepository` の `*Ungated` helper が DAO を直接呼び、
 * public API も内部で同じ DAO 呼び出しを行うことを検証する。
 *
 * Phase 3 で public 書き込み method に `DatabaseWriteGate.withWritePermit { ... }` を
 * 追加する前提として、内側 helper が DAO を直接叩くこと（二重 gate を起こさないこと）
 * を確認する。
 */
class ThreadStateRepositoryTest {
    @Test
    fun saveThreadStateUngated_callsDaoDirectly() = runTest {
        val dao = FakeThreadStateDao()
        val repo = ThreadStateRepository(dao, DatabaseWriteGate())

        val update = ThreadStateRepository.ThreadStateUpdate(
            threadId = threadIdOf("https://example.test/board/", "1"),
            boardId = 1L,
            boardUrl = "https://example.test/board/",
            boardName = "板A",
            title = "thread 1",
            latestResCount = 10,
            updatedAt = 100L,
        )

        // --- Act ---
        repo.saveThreadStateUngated(update)

        // --- Assert ---
        val stored = dao.snapshot()
        assertEquals(1, stored.size)
        assertEquals(10, stored.single().latestResCount)
        assertEquals("板A", stored.single().boardName)
    }

    @Test
    fun saveThreadStatesUngated_skipsEmptyList() = runTest {
        val dao = FakeThreadStateDao()
        val repo = ThreadStateRepository(dao, DatabaseWriteGate())

        // --- Act ---
        repo.saveThreadStatesUngated(emptyList())

        // --- Assert ---
        assertTrue(dao.snapshot().isEmpty())
    }

    @Test
    fun saveThreadStatesUngated_persistsAll() = runTest {
        val dao = FakeThreadStateDao()
        val repo = ThreadStateRepository(dao, DatabaseWriteGate())

        val updates = listOf(
            ThreadStateRepository.ThreadStateUpdate(
                threadId = threadIdOf("https://example.test/board/", "1"),
                boardId = 1L,
                boardUrl = "https://example.test/board/",
                boardName = "板A",
                title = "t1",
                latestResCount = 10,
                updatedAt = 100L,
            ),
            ThreadStateRepository.ThreadStateUpdate(
                threadId = threadIdOf("https://example.test/board/", "2"),
                boardId = 1L,
                boardUrl = "https://example.test/board/",
                boardName = "板A",
                title = "t2",
                latestResCount = 20,
                updatedAt = 200L,
            ),
        )

        // --- Act ---
        repo.saveThreadStatesUngated(updates)

        // --- Assert ---
        val stored = dao.snapshot()
        assertEquals(2, stored.size)
    }

    @Test
    fun collectGarbageUngated_removesOldEntries() = runTest {
        val dao = FakeThreadStateDao()
        val repo = ThreadStateRepository(dao, DatabaseWriteGate())

        // 2 件追加（1 件は古く、1 件は新しい）
        val old = ThreadStateRepository.ThreadStateUpdate(
            threadId = threadIdOf("https://example.test/board/", "old"),
            boardId = 1L,
            boardUrl = "https://example.test/board/",
            boardName = "板A",
            title = "old",
            latestResCount = 1,
            updatedAt = 0L,
        )
        val recent = ThreadStateRepository.ThreadStateUpdate(
            threadId = threadIdOf("https://example.test/board/", "new"),
            boardId = 1L,
            boardUrl = "https://example.test/board/",
            boardName = "板A",
            title = "new",
            latestResCount = 1,
            updatedAt = 10_000_000_000L,
        )
        repo.saveThreadStatesUngated(listOf(old, recent))

        // now = 30 日 + 1 ms 経過とする → old のみ削除候補
        val now = 30L * 24 * 60 * 60 * 1000 + 1L
        val deleted = repo.collectGarbageUngated(nowMillis = now, limit = 10)
        assertEquals(1, deleted)
        val stored = dao.snapshot()
        assertEquals(1, stored.size)
        assertEquals("new", stored.single().title)
    }

    @Test
    fun collectGarbageUngated_zeroLimitDoesNothing() = runTest {
        val dao = FakeThreadStateDao()
        val repo = ThreadStateRepository(dao, DatabaseWriteGate())
        val deleted = repo.collectGarbageUngated(nowMillis = 1L, limit = 0)
        assertEquals(0, deleted)
    }

    @Test
    fun collectStartupGarbageUngated_usesSmallLimit() = runTest {
        val dao = FakeThreadStateDao()
        val repo = ThreadStateRepository(dao, DatabaseWriteGate())
        // 候補は 0 件なので削除 0 を返す。
        val deleted = repo.collectStartupGarbageUngated()
        assertEquals(0, deleted)
    }

    @Test
    fun observeThreadStateMapByBoard_groupsByThreadKey() = runTest {
        val dao = FakeThreadStateDao()
        val repo = ThreadStateRepository(dao, DatabaseWriteGate())
        repo.saveThreadStatesUngated(
            listOf(
                ThreadStateRepository.ThreadStateUpdate(
                    threadId = threadIdOf("https://example.test/b/", "1"),
                    boardId = 1L,
                    boardUrl = "https://example.test/b/",
                    boardName = "板",
                    title = "t1",
                    latestResCount = 1,
                ),
            )
        )
        val map = repo.observeThreadStateMapByBoard(1L).first()
        assertEquals(1, map.size)
        assertEquals("t1", map.values.single().title)
    }

    /**
     * テスト用 ThreadId を組み立てる。
     *
     * `ThreadId` モデルのコンストラクタ直接利用はモジュール外公開状況を踏まえ、
     * `threadKey` 文字列と boardUrl から安全なテスト用インスタンスを生成する。
     */
    private fun threadIdOf(boardUrl: String, threadKey: String): com.websarva.wings.android.slevo.data.model.ThreadId {
        val parts = boardUrl.removeSuffix("/").split("/")
        val service = parts[parts.size - 2]
        val board = parts[parts.size - 1]
        return com.websarva.wings.android.slevo.data.model.ThreadId.of(service, board, threadKey)
    }
}
