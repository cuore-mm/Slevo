package com.websarva.wings.android.slevo.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.websarva.wings.android.slevo.data.database.DatabaseWriteGate
import com.websarva.wings.android.slevo.data.datasource.local.AppDatabase
import com.websarva.wings.android.slevo.data.model.OwnPostThreadScope
import com.websarva.wings.android.slevo.data.model.PostReceipt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/** [PendingOwnPostRepository] のscope限定取得と状態保守を検証する。 */
class PendingOwnPostRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: PendingOwnPostRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = PendingOwnPostRepository(
            dao = database.pendingOwnPostDao(),
            database = database,
            postHistoryRepository = PostHistoryRepository(
                dao = database.postHistoryDao(),
                identityDao = database.postIdentityHistoryDao(),
                lastIdentityDao = database.postLastIdentityDao(),
                gate = DatabaseWriteGate(),
            ),
            gate = DatabaseWriteGate(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pendingRows_areLimitedToExactThreadScope_andExpire() = runBlocking {
        val target = OwnPostThreadScope("provider", "board", "target")
        val other = OwnPostThreadScope("provider", "board", "other")
        repository.createPending(target, "target", "", "", 10, 1L, expiresAt = 100L)
        repository.createPending(other, "other", "", "", 10, 1L, expiresAt = 100L)

        assertEquals(1, repository.findPending(target).size)
        assertTrue(repository.findPending(OwnPostThreadScope("other-provider", "board", "target")).isEmpty())

        repository.expirePending(target, 100L)

        assertTrue(repository.findPending(target).isEmpty())
        assertEquals(1, database.pendingOwnPostDao().findPending(
            "provider", "board", "other", "PENDING"
        ).size)
    }

    @Test
    fun cleanupTerminal_deletesOnlyOldTerminalRows() = runBlocking {
        val scope = OwnPostThreadScope("provider", "board", "target")
        repository.createPending(scope, "pending", "", "", 0, 1L, expiresAt = 10_000L)
        database.pendingOwnPostDao().insert(
            com.websarva.wings.android.slevo.data.datasource.local.entity.history.PendingOwnPostEntity(
                providerId = scope.providerId,
                boardKey = scope.boardKey,
                threadKey = scope.threadKey,
                status = "MATCHED",
                content = "matched",
                name = "",
                email = "",
                baseResCount = 0,
                lastCheckedResNum = 1,
                submittedAt = 1L,
                expiresAt = 10_000L,
                matchedResNum = 1,
            )
        )

        repository.cleanupTerminal(submittedBefore = 2L)

        assertEquals(1, repository.findPending(scope).size)
        assertEquals(0, database.pendingOwnPostDao().findPending(
            scope.providerId, scope.boardKey, scope.threadKey, "MATCHED"
        ).size)
    }

    @Test
    fun createPending_persistsReceiptEvidence() = runBlocking {
        val scope = OwnPostThreadScope("provider", "board", "target")
        repository.createPending(
            scope = scope,
            content = "message",
            name = "name",
            email = "mail",
            baseResCount = 10,
            submittedAt = 1L,
            receipt = PostReceipt(
                confirmedResNum = 11,
                serverPostDateMillis = 123_456L,
                posterIdHint = "ABC",
            ),
        )

        val pending = repository.findPending(scope).single()
        assertEquals(11, pending.confirmedResNum)
        assertEquals(123_456L, pending.serverPostDateMillis)
        assertEquals("ABC", pending.posterIdHint)
    }
}
