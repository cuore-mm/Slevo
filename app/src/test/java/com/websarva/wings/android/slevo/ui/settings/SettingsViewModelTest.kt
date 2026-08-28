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
        every { repository.observeIsReplyNotificationEnabled() } returns MutableStateFlow(false)
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
        every { repository.observeIsReplyNotificationEnabled() } returns MutableStateFlow(false)
        val viewModel = SettingsViewModel(repository)

        advanceUntilIdle()
        viewModel.updateThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setThemeMode(ThemeMode.LIGHT) }
    }

    /** 返信通知のDataStore購読値がUiStateへ反映されることを確認する。 */
    @Test
    fun collectReplyNotification_updatesUiState() = runTest {
        val replyNotificationFlow = MutableStateFlow(false)
        val repository = mockk<SettingsRepository>(relaxed = true)
        every { repository.observeThemeMode() } returns MutableStateFlow(ThemeMode.SYSTEM)
        every { repository.observeIsReplyNotificationEnabled() } returns replyNotificationFlow
        val viewModel = SettingsViewModel(repository)

        advanceUntilIdle()
        replyNotificationFlow.value = true
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isReplyNotificationEnabled)
    }

    /** 返信通知のON/OFF操作がそのまま永続化APIへ委譲されることを確認する。 */
    @Test
    fun updateReplyNotificationEnabled_callsRepositoryForBothStates() = runTest {
        val repository = mockk<SettingsRepository>(relaxed = true)
        every { repository.observeThemeMode() } returns MutableStateFlow(ThemeMode.SYSTEM)
        every { repository.observeIsReplyNotificationEnabled() } returns MutableStateFlow(false)
        val viewModel = SettingsViewModel(repository)

        advanceUntilIdle()
        viewModel.updateReplyNotificationEnabled(true)
        viewModel.updateReplyNotificationEnabled(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setReplyNotificationEnabled(true) }
        coVerify(exactly = 1) { repository.setReplyNotificationEnabled(false) }
    }
}
