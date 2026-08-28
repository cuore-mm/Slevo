package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadBookmarkRepository
import com.websarva.wings.android.slevo.ui.tabs.coordinator.ThreadTabsCoordinator
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadRefreshResult
import com.websarva.wings.android.slevo.ui.thread.viewmodel.ThreadRefreshUseCase
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** タブ一括更新がスレッド画面と同じ共通RefreshUseCaseを順番に利用することを検証する。 */
class ThreadTabsCoordinatorReplyNotificationTest {
    /** 開いている全タブが共通取得へ渡され、同じ順序で一度ずつ処理されることを確認する。 */
    @Test
    fun refreshOpenThreads_delegatesAllTabsToCommonRefreshUseCase() = runTest {
        val firstTab = tab("first")
        val secondTab = tab("second")
        val tabState = MutableSharedFlow<List<ThreadTabInfo>>(replay = 1)
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val refreshUseCase = mockk<ThreadRefreshUseCase>(relaxed = true)
        every { tabsRepository.observeOpenThreadTabs() } returns tabState
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        coEvery { refreshUseCase.refresh(any()) } returns ThreadRefreshResult(
            posts = emptyList(),
            title = null,
            previousResCount = null,
        )
        val coordinator = ThreadTabsCoordinator(
            tabsRepository = tabsRepository,
            threadBookmarkRepository = bookmarkRepository,
            threadRefreshUseCase = refreshUseCase,
        )
        tabState.emit(listOf(firstTab, secondTab))

        coordinator.bind(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
        runCurrent()
        coordinator.refreshOpenThreads()
        advanceUntilIdle()

        println("DEBUG refresh tabs=${coordinator.openThreadTabs.value.map { it.id }} refreshing=${coordinator.isRefreshing.value}")
        coVerify(exactly = 2) { refreshUseCase.refresh(any()) }
        coVerifyOrder {
            refreshUseCase.refresh(match { it.threadId == firstTab.id })
            refreshUseCase.refresh(match { it.threadId == secondTab.id })
        }
        assertEquals(false, coordinator.isRefreshing.value)
        coordinator.close()
    }

    private fun tab(key: String) = ThreadTabInfo(
        id = com.websarva.wings.android.slevo.data.model.ThreadId.of("host", "board", key),
        title = key,
        boardName = "Board",
        boardUrl = "https://host/board/",
        boardId = 1L,
        firstVisibleItemIndex = 0,
        isPinned = false,
    )
}
