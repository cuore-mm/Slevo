package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.repository.BookmarkBoardRepository
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.ui.tabs.coordinator.BoardTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
import com.websarva.wings.android.slevo.ui.tabs.registry.TabViewModelRegistry
import io.mockk.mockk
import org.junit.Assert.assertEquals
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

    private fun createCoordinator(tabsRepository: TabsRepository): BoardTabsCoordinator {
        return BoardTabsCoordinator(
            tabsRepository = tabsRepository,
            bookmarkBoardRepository = mockk<BookmarkBoardRepository>(relaxed = true),
            tabViewModelRegistry = mockk<TabViewModelRegistry>(relaxed = true),
        )
    }
}
