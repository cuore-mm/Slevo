package com.websarva.wings.android.slevo.ui.board.viewmodel

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.websarva.wings.android.slevo.data.repository.BoardRepository
import com.websarva.wings.android.slevo.data.repository.ThreadHistoryRepository
import com.websarva.wings.android.slevo.data.repository.ThreadStateRepository
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import com.websarva.wings.android.slevo.ui.board.state.BoardUiState
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * [ThreadListCoordinator] の検索入力状態更新を検証するテスト。
 */
class ThreadListCoordinatorTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /**
     * 検索入力更新時に IME composition を含む `TextFieldValue` がそのまま保持されることを確認する。
     */
    @Test
    fun updateSearchInput_preservesComposition() = runTest {
        val uiState = MutableStateFlow(BoardUiState())
        val coordinator = ThreadListCoordinator(
            repository = mockk<BoardRepository>(relaxed = true),
            historyRepository = mockk<ThreadHistoryRepository>(relaxed = true),
            threadStateRepository = mockk<ThreadStateRepository>(relaxed = true),
            boardThreadListTransformUseCase = BoardThreadListTransformUseCase(),
            uiState = uiState,
            scope = backgroundScope,
        )
        val inputValue = TextFieldValue(
            text = "かな",
            selection = TextRange(2),
            composition = TextRange(0, 2),
        )

        coordinator.updateSearchInput(inputValue)

        assertEquals(inputValue, uiState.value.searchInputValue)
        assertEquals("かな", uiState.value.searchQuery)
    }
}
