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
 * スレッドのディープリンク処理における suspend の順序と失敗境界を検証する。
 * Deferred barrier により、準備完了、登録、正規状態の確認を個別に制御できる。
 */
class DeepLinkHandlerTest {

    /** 準備が完了するまで、後続の副作用をすべてブロックする。 */
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

    /** 登録の完了だけでは不十分であり、正規状態の確認後に選択と遷移を行う。 */
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

    /** 選択に失敗した場合は遷移を抑止し、表示中の画面を維持する。 */
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

    /** 登録を正規状態として確認できない場合は、選択と遷移を抑止する。 */
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

    /** 登録失敗は遷移せず、既存の handler のエラー境界へ伝播する。 */
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

    /** cancellation により古い対象への遷移副作用を発生させない。 */
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

    /** 初期の準備状態を固定した store のテスト用データを作成する。 */
    private fun mockStore(): TabSessionStore {
        val store = mockk<TabSessionStore>(relaxed = true)
        every { store.threadTabState } returns MutableStateFlow(ThreadTabsLoadState.Loading)
        return store
    }

    /** 処理連携テスト用に安定したスレッド route を組み立てる。 */
    private fun testRoute(): AppRoute.Thread = AppRoute.Thread(
        threadKey = "123",
        boardUrl = "https://example.com/test/",
        boardName = "test",
        threadTitle = "Thread",
    )
}
