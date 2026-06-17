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
            viewModels = mapOf(tab.boardUrl to mockBoardViewModel()),
        )
        val viewModel = BoardRouteViewModel(store)

        val first = viewModel.uiStateFor(tab.boardUrl)
        val second = viewModel.uiStateFor(tab.boardUrl)

        assertSame(first, second)
        verify(exactly = 1) { store.getOrCreateBoardViewModel(tab.boardUrl) }
    }

    @Test
    fun selectedUiState_switchesTabsWithoutRecreatingExistingFlow() = runTest {
        val first = BoardTabInfo(1L, "first", "https://example.com/test/", "5ch")
        val second = BoardTabInfo(2L, "second", "https://example.com/other/", "5ch")
        val selectedKey = MutableStateFlow<String?>(first.boardUrl)
        val store = mockStore(
            openTabs = MutableStateFlow(listOf(first, second)),
            selectedKey = selectedKey,
            viewModels = mapOf(
                first.boardUrl to mockBoardViewModel(title = "first"),
                second.boardUrl to mockBoardViewModel(title = "second"),
            ),
        )
        val viewModel = BoardRouteViewModel(store)
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

        assertEquals(listOf("", "first", "second", "first"), collected)
        verify(exactly = 1) { store.getOrCreateBoardViewModel(first.boardUrl) }
        verify(exactly = 1) { store.getOrCreateBoardViewModel(second.boardUrl) }
    }

    @Test
    fun refreshBoard_usesCachedViewModelForTargetTab() {
        val tab = BoardTabInfo(1L, "board", "https://example.com/test/", "5ch")
        val legacy = mockBoardViewModel()
        val store = mockStore(
            openTabs = MutableStateFlow(listOf(tab)),
            selectedKey = MutableStateFlow(tab.boardUrl),
            viewModels = mapOf(tab.boardUrl to legacy),
        )
        val viewModel = BoardRouteViewModel(store)

        viewModel.uiStateFor(tab.boardUrl)
        viewModel.refreshBoard(tab.boardUrl)

        verify(exactly = 1) { store.getOrCreateBoardViewModel(tab.boardUrl) }
        verify(exactly = 1) { legacy.refreshBoardData() }
    }

    /**
     * テスト用の `TabSessionStore` を構成する。
     */
    private fun mockStore(
        openTabs: MutableStateFlow<List<BoardTabInfo>>,
        selectedKey: MutableStateFlow<String?>,
        viewModels: Map<String, BoardViewModel>,
    ): TabSessionStore {
        val store = mockk<TabSessionStore>(relaxed = true)
        every { store.openBoardTabs } returns openTabs
        every { store.selectedBoardTabKey } returns selectedKey
        viewModels.forEach { (key, viewModel) ->
            every { store.getOrCreateBoardViewModel(key) } returns viewModel
        }
        return store
    }

    /**
     * テスト用の `BoardViewModel` モックを作る。
     */
    private fun mockBoardViewModel(title: String = ""): BoardViewModel {
        val viewModel = mockk<BoardViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(
            BoardUiState(
                boardInfo = com.websarva.wings.android.slevo.data.model.BoardInfo(name = title),
                threads = listOf(ThreadInfo(title = title)),
            )
        )
        return viewModel
    }
}
