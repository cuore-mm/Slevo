package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * [ThreadRouteViewModel] のタブ key 単位 UiState 提供と更新委譲を検証するテスト。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThreadRouteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiStateFor_sameKeyReusesCachedFlow() {
        val threadId = ThreadId.of("example.com", "test", "111")
        val tab = ThreadTabInfo(
            id = threadId,
            title = "title",
            boardName = "board",
            boardUrl = "https://example.com/test/",
            boardId = 1L,
        )
        val store = mockStore(
            openTabs = MutableStateFlow(listOf(tab)),
            selectedKey = MutableStateFlow(threadId.value),
            viewModels = mapOf(threadId.value to mockThreadViewModel()),
        )
        val viewModel = ThreadRouteViewModel(store)

        val first = viewModel.uiStateFor(threadId.value)
        val second = viewModel.uiStateFor(threadId.value)

        assertSame(first, second)
        verify(exactly = 1) { store.getOrCreateThreadViewModel(threadId.value) }
    }

    @Test
    fun selectedUiState_switchesTabsWithoutRecreatingExistingFlow() = runTest {
        val firstId = ThreadId.of("example.com", "test", "111")
        val secondId = ThreadId.of("example.com", "test", "222")
        val openTabs = MutableStateFlow(
            listOf(
                ThreadTabInfo(firstId, "first", "board", "https://example.com/test/", 1L),
                ThreadTabInfo(secondId, "second", "board", "https://example.com/test/", 1L),
            )
        )
        val selectedKey = MutableStateFlow<String?>(firstId.value)
        val firstVm = mockThreadViewModel(title = "first")
        val secondVm = mockThreadViewModel(title = "second")
        val store = mockStore(
            openTabs = openTabs,
            selectedKey = selectedKey,
            viewModels = mapOf(firstId.value to firstVm, secondId.value to secondVm),
        )
        val viewModel = ThreadRouteViewModel(store)
        val collected = mutableListOf<String>()

        val job = launch {
            viewModel.selectedUiState.collect { state ->
                collected += state.threadInfo.title
            }
        }
        advanceUntilIdle()

        selectedKey.value = secondId.value
        advanceUntilIdle()
        selectedKey.value = firstId.value
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("", "first", "second", "first"), collected)
        verify(exactly = 1) { store.getOrCreateThreadViewModel(firstId.value) }
        verify(exactly = 1) { store.getOrCreateThreadViewModel(secondId.value) }
    }

    @Test
    fun reloadThread_usesCachedViewModelForTargetTab() {
        val threadId = ThreadId.of("example.com", "test", "111")
        val tab = ThreadTabInfo(threadId, "title", "board", "https://example.com/test/", 1L)
        val legacy = mockThreadViewModel()
        val store = mockStore(
            openTabs = MutableStateFlow(listOf(tab)),
            selectedKey = MutableStateFlow(threadId.value),
            viewModels = mapOf(threadId.value to legacy),
        )
        val viewModel = ThreadRouteViewModel(store)

        viewModel.uiStateFor(threadId.value)
        viewModel.reloadThread(threadId.value)

        verify(exactly = 1) { store.getOrCreateThreadViewModel(threadId.value) }
        verify(exactly = 1) { legacy.reloadThread() }
    }

    @Test
    fun onAutoScrollReachedBottom_updatesOnlySelectedTab() {
        val firstId = ThreadId.of("example.com", "test", "111")
        val secondId = ThreadId.of("example.com", "test", "222")
        val firstVm = mockThreadViewModel()
        val secondVm = mockThreadViewModel()
        val store = mockStore(
            openTabs = MutableStateFlow(
                listOf(
                    ThreadTabInfo(firstId, "first", "board", "https://example.com/test/", 1L),
                    ThreadTabInfo(secondId, "second", "board", "https://example.com/test/", 1L),
                )
            ),
            selectedKey = MutableStateFlow(firstId.value),
            viewModels = mapOf(firstId.value to firstVm, secondId.value to secondVm),
        )
        val viewModel = ThreadRouteViewModel(store)

        viewModel.onAutoScrollReachedBottom(secondId.value)
        viewModel.onAutoScrollReachedBottom(firstId.value)

        verify(exactly = 0) { secondVm.onAutoScrollReachedBottom() }
        verify(exactly = 1) { firstVm.onAutoScrollReachedBottom() }
    }

    @Test
    fun refreshOpenThreads_delegatesToStore() {
        val store = mockStore(
            openTabs = MutableStateFlow(emptyList()),
            selectedKey = MutableStateFlow(null),
            viewModels = emptyMap(),
        )
        val viewModel = ThreadRouteViewModel(store)

        viewModel.refreshOpenThreads()
        viewModel.cancelRefreshOpenThreads()

        verify(exactly = 1) { store.refreshOpenThreads() }
        verify(exactly = 1) { store.cancelRefreshOpenThreads() }
    }

    /**
     * テスト用の `TabSessionStore` を構成する。
     */
    private fun mockStore(
        openTabs: MutableStateFlow<List<ThreadTabInfo>>,
        selectedKey: MutableStateFlow<String?>,
        viewModels: Map<String, ThreadViewModel>,
    ): TabSessionStore {
        val store = mockk<TabSessionStore>(relaxed = true)
        every { store.openThreadTabs } returns openTabs
        every { store.selectedThreadTabKey } returns selectedKey
        viewModels.forEach { (key, viewModel) ->
            every { store.getOrCreateThreadViewModel(key) } returns viewModel
        }
        return store
    }

    /**
     * テスト用の `ThreadViewModel` モックを作る。
     */
    private fun mockThreadViewModel(title: String = ""): ThreadViewModel {
        val viewModel = mockk<ThreadViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState(
                threadInfo = com.websarva.wings.android.slevo.data.model.ThreadInfo(title = title),
            )
        )
        return viewModel
    }
}
