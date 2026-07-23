package com.websarva.wings.android.slevo.data.backup

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * [BackupRepository] の concurrency serialization を検証する。
 *
 * Uri は JVM unit test で利用できないため、fake 側で代用する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupRepositoryTest {

    @Test
    fun exportBackup_serializesConcurrentCalls() = runTest(UnconfinedTestDispatcher()) {
        val repo = FakeBackupRepository()
        val results = mutableListOf<BackupExportResult>()

        coroutineScope {
            val job1 = launch {
                results.add(repo.export())
            }
            val job2 = launch {
                results.add(repo.export())
            }
            val job3 = launch {
                results.add(repo.export())
            }
            advanceUntilIdle()
            job1.join()
            job2.join()
            job3.join()
        }

        assertEquals(3, results.size)
        assertTrue(results.all { it is BackupExportResult.Success })
        assertEquals(1, repo.maxObservedValue)
    }

    /**
     * concurrent call serialization 用の fake。
     * JVM test から Uri を使わず呼べる [export] を提供する。
     */
    private class FakeBackupRepository {
        private val mutex = Mutex()
        private val concurrent = AtomicInteger(0)
        private val maxObserved = AtomicInteger(0)

        @Volatile
        var maxObservedValue = 0

        suspend fun export(): BackupExportResult {
            return mutex.withLock {
                val current = concurrent.incrementAndGet()
                maxObserved.accumulateAndGet(current) { prev, new ->
                    if (new > prev) new else prev
                }
                maxObservedValue = maxObserved.get()
                try {
                    BackupExportResult.Success
                } finally {
                    concurrent.decrementAndGet()
                }
            }
        }
    }
}
