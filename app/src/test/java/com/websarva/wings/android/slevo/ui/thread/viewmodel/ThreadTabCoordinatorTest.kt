package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.datasource.local.entity.ThreadReadState
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.repository.TabsRepository
import com.websarva.wings.android.slevo.data.repository.ThreadReadStateRepository
import com.websarva.wings.android.slevo.ui.tabs.ThreadTabInfo
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadTabCoordinatorTest {

    @Test
    fun updateThreadTabInfo_updatesTabAndSavesReadState() = runTest {
        val threadId = ThreadId.of("host", "board", "thread")
        val initialTab = ThreadTabInfo(
            id = threadId,
            title = "Old Title",
            boardName = "Board",
            boardUrl = "https://example.com",
            boardId = 1L,
            resCount = 10,
            prevResCount = 9,
            lastReadResNo = 5,
            firstNewResNo = 6,
        )
        val tabsRepository = mockTabsRepository(initialTab)
        val readStateRepository = mockReadStateRepository()
        val coordinator = ThreadTabCoordinator(this, tabsRepository, readStateRepository)

        coordinator.updateThreadTabInfo(threadId, "New Title", 12)
        advanceUntilIdle()

        val updatedTabs = captureSavedTabs(tabsRepository)
        val updatedTab = updatedTabs.single()
        assertEquals("New Title", updatedTab.title)
        assertEquals(12, updatedTab.resCount)
        assertEquals(initialTab.resCount, updatedTab.prevResCount)
        assertEquals(initialTab.firstNewResNo, updatedTab.firstNewResNo)

        coVerify(exactly = 1) {
            readStateRepository.saveReadState(
                threadId,
                ThreadReadState(
                    prevResCount = updatedTab.prevResCount,
                    lastReadResNo = updatedTab.lastReadResNo,
                    firstNewResNo = updatedTab.firstNewResNo,
                )
            )
        }
    }

    @Test
    fun updateThreadScrollPosition_updatesScrollOffsets() = runTest {
        val threadId = ThreadId.of("host", "board", "thread")
        val initialTab = ThreadTabInfo(
            id = threadId,
            title = "Title",
            boardName = "Board",
            boardUrl = "https://example.com",
            boardId = 1L,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 0,
        )
        val tabsRepository = mockTabsRepository(initialTab)
        val readStateRepository = mockReadStateRepository()
        val coordinator = ThreadTabCoordinator(this, tabsRepository, readStateRepository)

        coordinator.updateThreadScrollPosition(threadId, firstVisibleIndex = 5, scrollOffset = 12)
        advanceUntilIdle()

        val updatedTabs = captureSavedTabs(tabsRepository)
        val updatedTab = updatedTabs.single()
        assertEquals(5, updatedTab.firstVisibleItemIndex)
        assertEquals(12, updatedTab.firstVisibleItemScrollOffset)

        coVerify { readStateRepository wasNot Called }
    }

    @Test
    fun updateThreadLastRead_savesWhenProgressed() = runTest {
        val threadId = ThreadId.of("host", "board", "thread")
        val initialTab = ThreadTabInfo(
            id = threadId,
            title = "Title",
            boardName = "Board",
            boardUrl = "https://example.com",
            boardId = 1L,
            prevResCount = 8,
            lastReadResNo = 8,
            firstNewResNo = 9,
        )
        val tabsRepository = mockTabsRepository(initialTab)
        val readStateRepository = mockReadStateRepository()
        val coordinator = ThreadTabCoordinator(this, tabsRepository, readStateRepository)

        coordinator.updateThreadLastRead(threadId, lastReadResNo = 10)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            readStateRepository.saveReadState(
                threadId,
                ThreadReadState(
                    prevResCount = initialTab.prevResCount,
                    lastReadResNo = 10,
                    firstNewResNo = initialTab.firstNewResNo,
                )
            )
        }
    }

    @Test
    fun updateThreadLastRead_ignoresWhenNotAdvanced() = runTest {
        val threadId = ThreadId.of("host", "board", "thread")
        val initialTab = ThreadTabInfo(
            id = threadId,
            title = "Title",
            boardName = "Board",
            boardUrl = "https://example.com",
            boardId = 1L,
            prevResCount = 8,
            lastReadResNo = 8,
            firstNewResNo = 9,
        )
        val tabsRepository = mockTabsRepository(initialTab)
        val readStateRepository = mockReadStateRepository()
        val coordinator = ThreadTabCoordinator(this, tabsRepository, readStateRepository)

        coordinator.updateThreadLastRead(threadId, lastReadResNo = 7)
        advanceUntilIdle()

        coVerify { readStateRepository wasNot Called }
    }

    private fun mockTabsRepository(vararg tabs: ThreadTabInfo): TabsRepository {
        val tabsRepository = mockk<TabsRepository>(relaxed = true)
        every { tabsRepository.observeOpenThreadTabs() } returns flowOf(tabs.toList())
        return tabsRepository
    }

    private fun mockReadStateRepository(): ThreadReadStateRepository {
        return mockk(relaxed = true)
    }

    private fun captureSavedTabs(tabsRepository: TabsRepository): List<ThreadTabInfo> {
        val slot = slot<List<ThreadTabInfo>>()
        coVerify { tabsRepository.saveOpenThreadTabs(capture(slot)) }
        return slot.captured
    }
}
