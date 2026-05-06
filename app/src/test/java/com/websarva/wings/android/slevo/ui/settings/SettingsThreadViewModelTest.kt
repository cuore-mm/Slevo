package com.websarva.wings.android.slevo.ui.settings

import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SettingsThreadViewModel] の設定反映と保存呼び出しを検証するテスト。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsThreadViewModelTest {
    @Test
    fun collectThreadSettings_updatesUiState() = runTest {
        val treeSortFlow = MutableStateFlow(false)
        val scrollbarFlow = MutableStateFlow(true)
        val repository = mockk<SettingsRepository>(relaxed = true)
        every { repository.observeIsTreeSort() } returns treeSortFlow
        every { repository.observeIsThreadMinimapScrollbarEnabled() } returns scrollbarFlow
        val viewModel = SettingsThreadViewModel(repository)

        // ViewModel の初期購読コルーチンを先に開始させる。
        advanceUntilIdle()
        treeSortFlow.value = true
        scrollbarFlow.value = false
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isTreeSort)
        assertTrue(!viewModel.uiState.value.showMinimapScrollbar)
    }

    @Test
    fun updateApis_callRepository() = runTest {
        val repository = mockk<SettingsRepository>(relaxed = true)
        every { repository.observeIsTreeSort() } returns MutableStateFlow(false)
        every { repository.observeIsThreadMinimapScrollbarEnabled() } returns MutableStateFlow(true)
        val viewModel = SettingsThreadViewModel(repository)

        advanceUntilIdle()
        viewModel.updateSort(true)
        viewModel.updateMinimapScrollbar(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setTreeSort(true) }
        coVerify(exactly = 1) { repository.setThreadMinimapScrollbarEnabled(false) }
    }
}
