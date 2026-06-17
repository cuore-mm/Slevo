package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.ui.tabs.coordinator.BoardTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.registry.TabViewModelRegistry
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `BoardTabsCoordinator` のタブ管理と固定切替を検証するテスト。
 */
class BoardTabsCoordinatorTest {

    /**
     * `togglePinBoardTab` で対象板タブの固定状態を切り替えることを確認する。
     */
    @Test
    fun togglePinBoardTab_togglesPinnedState() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        coordinator.openBoardTab(
            BoardTabInfo(
                boardId = 1,
                boardName = "Test Board",
                boardUrl = "https://example.com/test/",
                serviceName = "example.com",
            )
        )
        val boardUrl = coordinator.openBoardTabs.value.first().boardUrl

        assertEquals(false, coordinator.openBoardTabs.value.first().isPinned)

        coordinator.togglePinBoardTab(boardUrl)

        assertEquals(true, coordinator.openBoardTabs.value.first().isPinned)

        coordinator.togglePinBoardTab(boardUrl)

        assertEquals(false, coordinator.openBoardTabs.value.first().isPinned)
    }

    /**
     * 既存の板タブを上書きした場合でも固定状態が維持されることを確認する。
     */
    @Test
    fun openBoardTab_preservesPinnedStateOnUpsert() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val originalTab = BoardTabInfo(
            boardId = 1,
            boardName = "Test Board",
            boardUrl = "https://example.com/test/",
            serviceName = "example.com",
            isPinned = true,
        )
        coordinator.openBoardTab(originalTab)

        val updatedTab = originalTab.copy(
            boardName = "Updated Board",
            isPinned = false,
        )
        coordinator.openBoardTab(updatedTab)

        val actual = coordinator.openBoardTabs.value.first()
        assertEquals(true, actual.isPinned)
        assertEquals("Updated Board", actual.boardName)
    }

    /**
     * 板タブ選択時に selected key が boardUrl で更新されることを確認する。
     */
    @Test
    fun selectBoardTab_updatesSelectedBoardTabKey() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val tab = BoardTabInfo(
            boardId = 1,
            boardName = "Test Board",
            boardUrl = "https://example.com/test/",
            serviceName = "example.com",
        )
        coordinator.openBoardTab(tab)

        coordinator.selectBoardTab(tab.boardUrl)

        assertEquals(tab.boardUrl, coordinator.selectedBoardTabKey.value)
    }

    /**
     * 選択中タブを閉じた場合、selected key が隣接タブへ補正されることを確認する。
     */
    @Test
    fun closeBoardTab_updatesSelectedKeyToAdjacentTab() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val first = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        val second = BoardTabInfo(2, "B", "https://example.com/b/", "example.com")
        coordinator.openBoardTab(first)
        coordinator.openBoardTab(second)
        coordinator.selectBoardTab(first.boardUrl)

        coordinator.closeBoardTab(first)

        assertEquals(second.boardUrl, coordinator.selectedBoardTabKey.value)
    }

    /**
     * 最後の板タブを閉じた場合、selected key が null になることを確認する。
     */
    @Test
    fun closeLastBoardTab_clearsSelectedBoardTabKey() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val tab = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        coordinator.openBoardTab(tab)
        coordinator.selectBoardTab(tab.boardUrl)

        coordinator.closeBoardTab(tab)

        assertNull(coordinator.selectedBoardTabKey.value)
    }

    /**
     * タブを閉じたときに対象板タブのセッション状態だけが削除されることを確認する。
     */
    @Test
    fun closeBoardTab_removesOnlyTargetSessionState() {
        val coordinator = createCoordinator(mockk(relaxed = true))
        val first = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        val second = BoardTabInfo(2, "B", "https://example.com/b/", "example.com")
        coordinator.openBoardTab(first)
        coordinator.openBoardTab(second)
        coordinator.updateBoardSessionState(first.boardUrl) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("first"))
        }
        coordinator.updateBoardSessionState(second.boardUrl) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("second"))
        }

        coordinator.closeBoardTab(first)

        assertFalse(coordinator.boardSessionStates.value.containsKey(first.boardUrl))
        assertEquals("second", coordinator.getBoardSessionState(second.boardUrl).searchQuery)
    }

    /**
     * セッション状態更新が永続タブ保存を呼ばないことを確認する。
     */
    @Test
    fun updateBoardSessionState_doesNotPersistTabs() {
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val coordinator = createCoordinator(tabsRepository)
        val tab = BoardTabInfo(1, "A", "https://example.com/a/", "example.com")
        coordinator.openBoardTab(tab)

        coordinator.updateBoardSessionState(tab.boardUrl) {
            it.copy(searchInputValue = androidx.compose.ui.text.input.TextFieldValue("query"))
        }

        assertEquals("query", coordinator.getBoardSessionState(tab.boardUrl).searchQuery)
        coVerify(exactly = 0) { tabsRepository.saveOpenBoardTabs(any()) }
    }

    private fun createCoordinator(tabsRepository: TabsRepository): BoardTabsCoordinator {
        return BoardTabsCoordinator(
            tabsRepository = tabsRepository,
            bookmarkBoardRepository = mockk<BookmarkBoardRepository>(relaxed = true),
            tabViewModelRegistry = mockk<TabViewModelRegistry>(relaxed = true),
        )
    }
}
