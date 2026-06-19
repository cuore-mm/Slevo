package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.repository.DatRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.session.PendingThreadPostState
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `ThreadTabsCoordinator` のタブ生成方針を検証するテスト。
 *
 * URL由来でタイトル未取得のケースと、5ch.net/5ch.io の重複タブ許容挙動を確認する。
 */
class ThreadTabsCoordinatorTest {

    /**
     * タイトル未取得（null）の route でタブを作成した場合、
     * 正規化後 boardUrl と threadKey から構築したURLをタイトルとして保存することを確認する。
     */
    @Test
    fun ensureThreadTab_savesThreadUrlWhenThreadTitleIsNull() {
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val coordinator = createCoordinator(tabsRepository)

        val index = coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "1723111700",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = null,
            )
        )

        assertEquals(0, index)
        assertEquals(1, coordinator.openThreadTabs.value.size)
        assertEquals(
            "https://medaka.5ch.io/test/read.cgi/mmominor/1723111700/",
            coordinator.openThreadTabs.value.first().title
        )
        coVerify(exactly = 0) { tabsRepository.saveOpenThreadTabs(any()) }
    }

    /**
     * host が異なる同一 board/thread は別タブとして保存されることを確認する。
     */
    @Test
    fun ensureThreadTab_createsSeparateTabsForNetAndIoHosts() {
        val coordinator = createCoordinator(mockk(relaxed = true))

        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "1723111700",
                boardUrl = "https://medaka.5ch.net/mmominor/",
                boardName = "mmominor",
                threadTitle = "old",
            )
        )
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "1723111700",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "new",
            )
        )

        assertEquals(2, coordinator.openThreadTabs.value.size)
        val threadIds = coordinator.openThreadTabs.value.map { it.id.value }.toSet()
        assertTrue(threadIds.any { it.contains("medaka.5ch.net") })
        assertTrue(threadIds.any { it.contains("medaka.5ch.io") })
    }

    /**
     * `togglePinThreadTab` で対象スレッドタブの固定状態を切り替えることを確認する。
     */
    @Test
    fun togglePinThreadTab_togglesPinnedState() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "1723111700",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "test",
            )
        )
        val threadId = coordinator.openThreadTabs.value.first().id

        assertEquals(false, coordinator.openThreadTabs.value.first().isPinned)

        coordinator.togglePinThreadTab(threadId)

        assertEquals(true, coordinator.openThreadTabs.value.first().isPinned)

        coordinator.togglePinThreadTab(threadId)

        assertEquals(false, coordinator.openThreadTabs.value.first().isPinned)
    }

    /**
     * スレッドタブ選択時に selected key が ThreadId で更新されることを確認する。
     */
    @Test
    fun selectThreadTab_updatesSelectedThreadTabKey() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "1723111700",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "test",
            )
        )
        val threadId = coordinator.openThreadTabs.value.first().id

        coordinator.selectThreadTab(threadId)

        assertEquals(threadId.value, coordinator.selectedThreadTabKey.value)
    }

    /**
     * 選択中スレッドタブを閉じた場合、selected key が隣接タブへ補正されることを確認する。
     */
    @Test
    fun closeThreadTab_updatesSelectedKeyToAdjacentTab() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "111",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "first",
            )
        )
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "222",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "second",
            )
        )
        val first = coordinator.openThreadTabs.value.first()
        val second = coordinator.openThreadTabs.value.last()
        coordinator.selectThreadTab(first.id)

        coordinator.closeThreadTab(first)

        assertEquals(second.id.value, coordinator.selectedThreadTabKey.value)
    }

    /**
     * 最後のスレッドタブを閉じた場合、selected key が null になることを確認する。
     */
    @Test
    fun closeLastThreadTab_clearsSelectedThreadTabKey() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "111",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "first",
            )
        )
        val tab = coordinator.openThreadTabs.value.first()
        coordinator.selectThreadTab(tab.id)

        coordinator.closeThreadTab(tab)

        assertNull(coordinator.selectedThreadTabKey.value)
    }

    /**
     * タブを閉じたときに対象タブのセッション状態だけが削除されることを確認する。
     */
    @Test
    fun closeThreadTab_removesOnlyTargetSessionState() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "111",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "first",
            )
        )
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "222",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "second",
            )
        )
        val first = coordinator.openThreadTabs.value.first()
        val second = coordinator.openThreadTabs.value.last()
        coordinator.updateThreadSessionState(first.id) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("first"))
        }
        coordinator.updateThreadSessionState(second.id) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("second"))
        }

        coordinator.closeThreadTab(first)

        assertFalse(coordinator.threadSessionStates.value.containsKey(first.id.value))
        assertEquals("second", coordinator.getThreadSessionState(second.id).searchQuery)
    }

    /**
     * セッション状態更新が永続タブ保存を呼ばないことを確認する。
     */
    @Test
    fun updateThreadSessionState_doesNotPersistTabs() {
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val coordinator = createCoordinator(tabsRepository)
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "111",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "first",
            )
        )
        val tab = coordinator.openThreadTabs.value.first()

        coordinator.updateThreadSessionState(tab.id) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("query"))
        }

        assertEquals("query", coordinator.getThreadSessionState(tab.id).searchQuery)
        coVerify(exactly = 0) { tabsRepository.saveOpenThreadTabs(any()) }
    }

    @Test
    fun closeThreadTab_removesTargetRuntimeState() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "111",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "first",
            )
        )
        val tab = coordinator.openThreadTabs.value.first()
        coordinator.updateThreadRuntimeState(tab.id) {
            it.copy(pendingPost = PendingThreadPostState(10, "message", "name", "mail"))
        }

        coordinator.closeThreadTab(tab)

        assertEquals(null, coordinator.threadRuntimeStates.value[tab.id.value])
    }

    /**
     * 解決済み boardId を反映しても、既存の表示メタ情報とスクロール位置を保持することを確認する。
     */
    @Test
    fun updateThreadResolvedBoardInfo_updatesBoardIdAndPreservesThreadTabFields() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.ensureThreadTab(
            AppRoute.Thread(
                threadKey = "111",
                boardUrl = "https://medaka.5ch.io/mmominor/",
                boardName = "mmominor",
                threadTitle = "first",
                boardId = 0L,
                resCount = 10,
            )
        )
        val original = coordinator.openThreadTabs.value.first().copy(
            newResCount = 2,
            prevResCount = 8,
            lastReadResNo = 7,
            firstNewResNo = 9,
            firstVisibleItemIndex = 12,
            firstVisibleItemScrollOffset = 34,
            bookmarkColorName = "blue",
            isPinned = true,
        )
        coordinator.closeThreadTab(coordinator.openThreadTabs.value.first())
        coordinator.openThreadTab(original)

        coordinator.updateThreadResolvedBoardInfo(
            threadId = original.id,
            boardId = 42L,
            boardName = "resolved",
        )

        val actual = coordinator.openThreadTabs.value.first()
        assertEquals(42L, actual.boardId)
        assertEquals("resolved", actual.boardName)
        assertEquals(original.title, actual.title)
        assertEquals(10, actual.resCount)
        assertEquals(2, actual.newResCount)
        assertEquals(8, actual.prevResCount)
        assertEquals(7, actual.lastReadResNo)
        assertEquals(9, actual.firstNewResNo)
        assertEquals(12, actual.firstVisibleItemIndex)
        assertEquals(34, actual.firstVisibleItemScrollOffset)
        assertEquals("blue", actual.bookmarkColorName)
        assertEquals(true, actual.isPinned)
    }

    /**
     * テスト用に依存を差し替えた `ThreadTabsCoordinator` を生成する。
     */
    private fun createCoordinator(tabsRepository: TabsRepository): ThreadTabsCoordinator {
        return ThreadTabsCoordinator(
            tabsRepository = tabsRepository,
            threadBookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true),
            datRepository = mockk<DatRepository>(relaxed = true),
            threadStateRepository = mockk<ThreadStateRepository>(relaxed = true),
        )
    }
}
