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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val refreshUseCase = mockk<ThreadRefreshUseCase>(relaxed = true)
        every { tabsRepository.observeOpenThreadTabs() } returns flowOf(listOf(firstTab, secondTab))
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

        coordinator.bind(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        coordinator.refreshOpenThreads()

        coVerify(exactly = 2) { refreshUseCase.refresh(any()) }
        coVerifyOrder {
            refreshUseCase.refresh(match { it.threadId == firstTab.id })
            refreshUseCase.refresh(match { it.threadId == secondTab.id })
        }
        coordinator.close()
    }

    /** 取得開始前に閉じられたタブをスキップし、残りのタブだけを取得することを確認する。 */
    @Test
    fun refreshOpenThreads_skipsTabClosedBeforeItsRefreshStarts() = runTest {
        val firstTab = tab("first")
        val secondTab = tab("second")
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val refreshUseCase = mockk<ThreadRefreshUseCase>(relaxed = true)
        val openTabs = MutableStateFlow(listOf(firstTab, secondTab))
        every { tabsRepository.observeOpenThreadTabs() } returns openTabs
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        coEvery { refreshUseCase.refresh(match { it.threadId == firstTab.id }) } coAnswers {
            firstStarted.complete(Unit)
            releaseFirst.await()
            refreshResult()
        }
        coEvery { refreshUseCase.refresh(match { it.threadId == secondTab.id }) } returns refreshResult()
        val coordinator = ThreadTabsCoordinator(
            tabsRepository = tabsRepository,
            threadBookmarkRepository = bookmarkRepository,
            threadRefreshUseCase = refreshUseCase,
        )

        coordinator.bind(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        coordinator.refreshOpenThreads()
        firstStarted.await()
        openTabs.value = listOf(firstTab)
        assertEquals(listOf(firstTab), coordinator.openThreadTabs.value)
        releaseFirst.complete(Unit)

        coVerify(exactly = 1) { refreshUseCase.refresh(match { it.threadId == firstTab.id }) }
        coVerify(exactly = 0) { refreshUseCase.refresh(match { it.threadId == secondTab.id }) }
        assertEquals(2, coordinator.refreshProgress.value?.totalCount)
        assertEquals(2, coordinator.refreshProgress.value?.completedCount)
        coordinator.close()
    }

    /** 取得開始後にタブが閉じられても、共通取得処理を中断せず完了することを確認する。 */
    @Test
    fun refreshOpenThreads_continuesRefreshAfterTabCloses() = runTest {
        val onlyTab = tab("only")
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        val bookmarkRepository = mockk<ThreadBookmarkRepository>(relaxed = true)
        val refreshUseCase = mockk<ThreadRefreshUseCase>(relaxed = true)
        val openTabs = MutableStateFlow(listOf(onlyTab))
        every { tabsRepository.observeOpenThreadTabs() } returns openTabs
        every { bookmarkRepository.observeSortedGroupsWithThreadBookmarks() } returns flowOf(emptyList())
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val refreshCompleted = CompletableDeferred<Unit>()
        coEvery { refreshUseCase.refresh(any()) } coAnswers {
            refreshStarted.complete(Unit)
            releaseRefresh.await()
            refreshCompleted.complete(Unit)
            refreshResult()
        }
        val coordinator = ThreadTabsCoordinator(
            tabsRepository = tabsRepository,
            threadBookmarkRepository = bookmarkRepository,
            threadRefreshUseCase = refreshUseCase,
        )

        coordinator.bind(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        coordinator.refreshOpenThreads()
        refreshStarted.await()
        openTabs.value = emptyList()
        releaseRefresh.complete(Unit)
        refreshCompleted.await()

        coVerify(exactly = 1) { refreshUseCase.refresh(match { it.threadId == onlyTab.id }) }
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

    private fun refreshResult() = ThreadRefreshResult(
        posts = emptyList(),
        title = null,
        previousResCount = null,
    )
}
