package com.websarva.wings.android.slevo.ui.navigation

import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsLoadState
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Verifies the suspend ordering and failure boundaries of thread deep-link handling.
 * Deferred barriers make readiness, registration, and canonical confirmation independently controllable.
 */
class DeepLinkHandlerTest {

    /** Readiness blocks every later side effect. */
    @Test
    fun threadDeepLink_waitsForReadinessBeforeRegistering() = runTest {
        val readiness = CompletableDeferred<ThreadTabsLoadState.Loaded>()
        val store = mockStore()
        val events = mutableListOf<String>()
        coEvery { store.awaitThreadTabsReady() } coAnswers {
            readiness.await()
            events += "ready"
            ThreadTabsLoadState.Loaded(emptyList())
        }
        coEvery { store.registerThreadRoute(any()) } answers {
            events += "register"
            0
        }
        val job = async {
            handleThreadDeepLinkRoute(testRoute(), store) { events += "navigate" }
        }
        runCurrent()

        assertTrue(job.isActive)
        assertEquals(emptyList<String>(), events)
        readiness.complete(ThreadTabsLoadState.Loaded(emptyList()))
        runCurrent()
        assertTrue(events.indexOf("ready") >= 0)
        assertTrue(events.indexOf("register") > events.indexOf("ready"))
        job.cancelAndJoin()
    }

    /** Registration completion alone is insufficient; canonical confirmation precedes selection and navigation. */
    @Test
    fun threadDeepLink_ordersRegistrationConfirmationSelectionAndNavigation() = runTest {
        val registration = CompletableDeferred<Unit>()
        val store = mockStore()
        val events = mutableListOf<String>()
        coEvery { store.awaitThreadTabsReady() } coAnswers { events += "ready"; ThreadTabsLoadState.Loaded(emptyList()) }
        coEvery { store.registerThreadRoute(any()) } coAnswers {
            events += "register-start"
            registration.await()
            events += "register-complete"
            0
        }
        every { store.isCanonicalThreadTab(any()) } answers {
            events += "canonical-check"
            true
        }
        every { store.selectThreadTab(any()) } answers {
            events += "select"
            true
        }
        val job = async {
            handleThreadDeepLinkRoute(testRoute(), store) { events += "navigate" }
        }
        runCurrent()
        assertEquals(listOf("ready", "register-start"), events)
        registration.complete(Unit)
        runCurrent()

        assertTrue(job.await())
        assertEquals(
            listOf("ready", "register-start", "register-complete", "canonical-check", "select", "navigate"),
            events,
        )
    }

    /** A selection failure preserves the existing screen by suppressing navigation. */
    @Test
    fun threadDeepLink_selectionFailureDoesNotNavigate() = runTest {
        val store = mockStore()
        coEvery { store.awaitThreadTabsReady() } returns ThreadTabsLoadState.Loaded(emptyList())
        coEvery { store.registerThreadRoute(any()) } returns 0
        every { store.isCanonicalThreadTab(any()) } returns true
        every { store.selectThreadTab(any()) } returns false
        var navigated = false

        assertFalse(handleThreadDeepLinkRoute(testRoute(), store) { navigated = true })
        assertFalse(navigated)
    }

    /** A registration that cannot be confirmed canonically suppresses selection and navigation. */
    @Test
    fun threadDeepLink_missingCanonicalTargetDoesNotNavigate() = runTest {
        val store = mockStore()
        coEvery { store.awaitThreadTabsReady() } returns ThreadTabsLoadState.Loaded(emptyList())
        coEvery { store.registerThreadRoute(any()) } returns 0
        every { store.isCanonicalThreadTab(any()) } returns false
        var navigated = false

        assertFalse(handleThreadDeepLinkRoute(testRoute(), store) { navigated = true })
        assertFalse(navigated)
    }

    /** Registration failure is propagated to the existing handler error boundary without navigation. */
    @Test
    fun threadDeepLink_registrationFailureDoesNotNavigate() = runTest {
        val store = mockStore()
        coEvery { store.awaitThreadTabsReady() } returns ThreadTabsLoadState.Loaded(emptyList())
        coEvery { store.registerThreadRoute(any()) } throws IllegalStateException("registration failed")
        var navigated = false

        try {
            handleThreadDeepLinkRoute(testRoute(), store) { navigated = true }
            fail("registration failure should be propagated to the handler boundary")
        } catch (exception: IllegalStateException) {
            assertEquals("registration failed", exception.message)
        }
        assertFalse(navigated)
    }

    /** Cancellation does not produce an old target navigation side effect. */
    @Test
    fun threadDeepLink_cancellationStopsBeforeSelection() = runTest {
        val readiness = CompletableDeferred<ThreadTabsLoadState.Loaded>()
        val store = mockStore()
        coEvery { store.awaitThreadTabsReady() } coAnswers { readiness.await() }
        val events = mutableListOf<String>()
        val job = async {
            handleThreadDeepLinkRoute(testRoute(), store) { events += "navigate" }
        }
        runCurrent()
        job.cancelAndJoin()

        assertEquals(emptyList<String>(), events)
    }

    /** Creates a store fixture with a deterministic initial readiness state. */
    private fun mockStore(): TabSessionStore {
        val store = mockk<TabSessionStore>(relaxed = true)
        every { store.threadTabState } returns MutableStateFlow(ThreadTabsLoadState.Loading)
        return store
    }

    /** Builds a stable thread route for orchestration tests. */
    private fun testRoute(): AppRoute.Thread = AppRoute.Thread(
        threadKey = "123",
        boardUrl = "https://example.com/test/",
        boardName = "test",
        threadTitle = "Thread",
    )
}
