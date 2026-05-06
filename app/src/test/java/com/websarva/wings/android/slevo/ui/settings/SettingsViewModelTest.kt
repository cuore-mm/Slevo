package com.websarva.wings.android.slevo.ui.settings

import com.websarva.wings.android.slevo.data.model.ThemeMode
import com.websarva.wings.android.slevo.data.repository.SettingsRepository
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * [SettingsViewModel] のテーマ設定反映を検証するテスト。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun collectThemeMode_updatesUiState() = runTest {
        val themeFlow = MutableStateFlow(ThemeMode.SYSTEM)
        val repository = mockk<SettingsRepository>(relaxed = true)
        every { repository.observeThemeMode() } returns themeFlow
        val viewModel = SettingsViewModel(repository)

        // ViewModel の初期購読コルーチンを先に開始させる。
        advanceUntilIdle()
        themeFlow.value = ThemeMode.DARK
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
    }

    @Test
    fun updateThemeMode_callsRepository() = runTest {
        val repository = mockk<SettingsRepository>(relaxed = true)
        every { repository.observeThemeMode() } returns MutableStateFlow(ThemeMode.SYSTEM)
        val viewModel = SettingsViewModel(repository)

        advanceUntilIdle()
        viewModel.updateThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setThemeMode(ThemeMode.LIGHT) }
    }
}
