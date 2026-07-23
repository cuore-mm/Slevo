package com.websarva.wings.android.slevo.ui.settings.backup

import android.net.Uri
import com.websarva.wings.android.slevo.data.backup.BackupExportResult
import com.websarva.wings.android.slevo.data.backup.BackupRepository
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * [BackupViewModel] の状態遷移と UI イベント発行を検証する。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    // --- 確認ダイアログ ---

    @Test
    fun onBackupClick_showsConfirmDialog() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onBackupClick()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.showConfirmDialog)
        assertFalse(state.includeCookies)
    }

    @Test
    fun onBackupClick_whileExporting_doesNothing() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onConfirmCreate()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isExporting)

        viewModel.onBackupClick()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showConfirmDialog)
    }

    @Test
    fun onCookiesToggle_updatesIncludeCookies() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onBackupClick()
        advanceUntilIdle()

        viewModel.onCookiesToggle(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.includeCookies)

        viewModel.onCookiesToggle(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.includeCookies)
    }

    @Test
    fun onConfirmCancel_hidesDialog() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onBackupClick()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showConfirmDialog)

        viewModel.onConfirmCancel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showConfirmDialog)
    }

    @Test
    fun onConfirmCreate_startsExporting() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onConfirmCreate()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.showConfirmDialog)
        assertTrue(state.isExporting)
    }

    // --- URI 受信（export なしの分岐のみ） ---

    @Test
    fun onUriReceived_null_cancelsExporting() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onConfirmCreate()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isExporting)

        viewModel.onUriReceived(null)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isExporting)
    }

    @Test
    fun onUriReceived_success_emitsExportSucceeded() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { exportBackup(uri, false) } returns BackupExportResult.Success
        }
        val viewModel = BackupViewModel(repository)

        // first() で 1 件だけ待ち受け、受信後に自動完了させる。
        val eventDeferred = async { viewModel.events.first() }
        runCurrent()

        viewModel.onConfirmCreate()
        assertTrue(viewModel.uiState.value.isExporting)

        viewModel.onUriReceived(uri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isExporting)
        assertEquals(BackupUiEvent.ExportSucceeded, eventDeferred.await())
    }

    @Test
    fun onUriReceived_failure_emitsExportFailed() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { exportBackup(uri, false) } returns BackupExportResult.Failure("failed")
        }
        val viewModel = BackupViewModel(repository)

        // first() で 1 件だけ待ち受け、受信後に自動完了させる。
        val eventDeferred = async { viewModel.events.first() }
        runCurrent()

        viewModel.onConfirmCreate()
        assertTrue(viewModel.uiState.value.isExporting)

        viewModel.onUriReceived(uri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isExporting)
        assertEquals(BackupUiEvent.ExportFailed, eventDeferred.await())
    }

    // --- ヘルパー ---

    /** exportBackup が呼ばれない mock で ViewModel を作成する。 */
    private fun createViewModel(): BackupViewModel {
        val repository: BackupRepository = mockk()
        return BackupViewModel(repository)
    }
}
