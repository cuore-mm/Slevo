package com.websarva.wings.android.slevo.ui.board.viewmodel

import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import com.websarva.wings.android.slevo.ui.tabs.model.BoardTabInfo
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
 * [BoardRouteViewModel] のタブ key 単位 UiState 提供と更新委譲を検証するテスト。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BoardRouteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiStateFor_sameKeyReusesCachedFlow() {
        val tab = BoardTabInfo(1L, "board", "https://example.com/test/", "5ch")
        val store = mockStore(
            openTabs = MutableStateFlow(listOf(tab)),
            selectedKey = MutableStateFlow(tab.boardUrl),
        )
        val factory = mockFactory(mapOf(tab.boardUrl to mockBoardViewModel()))
        val viewModel = BoardRouteViewModel(store, factory)

        val first = viewModel.uiStateFor(tab.boardUrl)
        val second = viewModel.uiStateFor(tab.boardUrl)

        assertSame(first, second)
        verify(exactly = 1) { factory.create(tab.boardUrl) }
    }

    @Test
    fun selectedUiState_switchesTabsWithoutRecreatingExistingFlow() = runTest {
        val first = BoardTabInfo(1L, "first", "https://example.com/test/", "5ch")
        val second = BoardTabInfo(2L, "second", "https://example.com/other/", "5ch")
        val selectedKey = MutableStateFlow<String?>(first.boardUrl)
        val store = mockStore(
            openTabs = MutableStateFlow(listOf(first, second)),
            selectedKey = selectedKey,
        )
        val factory = mockFactory(
            mapOf(
                first.boardUrl to mockBoardViewModel(title = "first"),
                second.boardUrl to mockBoardViewModel(title = "second"),
            )
        )
        val viewModel = BoardRouteViewModel(store, factory)
        val collected = mutableListOf<String>()

        val job = launch {
            viewModel.selectedUiState.collect { state ->
                collected += state.boardInfo.name
            }
        }
        advanceUntilIdle()

        selectedKey.value = second.boardUrl
        advanceUntilIdle()
        selectedKey.value = first.boardUrl
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf("first", "second", "first"), collected.filter { it.isNotBlank() }.takeLast(3))
        verify(exactly = 1) { factory.create(first.boardUrl) }
        verify(exactly = 1) { factory.create(second.boardUrl) }
    }

    @Test
    fun refreshBoard_usesCachedViewModelForTargetTab() {
        val tab = BoardTabInfo(1L, "board", "https://example.com/test/", "5ch")
        val legacy = mockBoardViewModel()
        val store = mockStore(
            openTabs = MutableStateFlow(listOf(tab)),
            selectedKey = MutableStateFlow(tab.boardUrl),
        )
        val factory = mockFactory(mapOf(tab.boardUrl to legacy))
        val viewModel = BoardRouteViewModel(store, factory)

        viewModel.uiStateFor(tab.boardUrl)
        viewModel.refreshBoard(tab.boardUrl)

        verify(exactly = 1) { factory.create(tab.boardUrl) }
        verify(exactly = 1) { legacy.refreshBoardData() }
    }

    @Test
    fun uiStateFor_disposesCachedViewModelWhenTabCloses() = runTest {
        val tab = BoardTabInfo(1L, "board", "https://example.com/test/", "5ch")
        val openTabs = MutableStateFlow(listOf(tab))
        val legacy = mockBoardViewModel()
        val viewModel = BoardRouteViewModel(
            mockStore(openTabs = openTabs, selectedKey = MutableStateFlow(tab.boardUrl)),
            mockFactory(mapOf(tab.boardUrl to legacy)),
        )

        viewModel.uiStateFor(tab.boardUrl)
        advanceUntilIdle()
        openTabs.value = emptyList()
        advanceUntilIdle()

        verify(exactly = 1) { legacy.disposeResources() }
    }

    /**
     * テスト用の `TabSessionStore` を構成する。
     */
    private fun mockStore(
        openTabs: MutableStateFlow<List<BoardTabInfo>>,
        selectedKey: MutableStateFlow<String?>,
    ): TabSessionStore {
        val store = mockk<TabSessionStore>(relaxed = true)
        every { store.openBoardTabs } returns openTabs
        every { store.selectedBoardTabKey } returns selectedKey
        return store
    }

    /**
     * テスト用の `BoardViewModelFactory` モックを作る。
     */
    private fun mockFactory(viewModels: Map<String, BoardViewModel>): BoardViewModelFactory {
        val factory = mockk<BoardViewModelFactory>()
        viewModels.forEach { (key, viewModel) ->
            every { factory.create(key) } returns viewModel
        }
        return factory
    }

    /**
     * テスト用の `BoardViewModel` モックを作る。
     */
    private fun mockBoardViewModel(title: String = ""): BoardViewModel {
        val viewModel = mockk<BoardViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            BoardUiState(
                boardInfo = com.websarva.wings.android.slevo.data.model.BoardInfo(
                    boardId = 0L,
                    name = title,
                    url = "",
                ),
                threads = listOf(ThreadInfo(title = title)),
            )
        )
        return viewModel
    }
}
