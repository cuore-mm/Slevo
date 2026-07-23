package com.websarva.wings.android.slevo

import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Application startup が pending restore の durable recovery 完了後に Room phase へ進むことを検証する。
 */
class SlevoApplicationStartupOrderingTest {
    @Test
    fun pendingRestoreCompletesBeforeDatabasePhase() {
        val events = mutableListOf<String>()

        runPendingRestoreBeforeDatabase {
            events += "restore-start"
            yield()
            events += "rollback-committed"
        }
        events += "room-created"

        assertEquals(
            listOf("restore-start", "rollback-committed", "room-created"),
            events,
        )
    }
}
