package com.websarva.wings.android.slevo.ui.settings.backup

import android.net.Uri
import com.websarva.wings.android.slevo.data.backup.export.BackupExportResult
import com.websarva.wings.android.slevo.data.backup.BackupRepository
import com.websarva.wings.android.slevo.data.backup.restore.BackupRestoreResult
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

    // --- 確認ダイアログ（バックアップ作成） ---

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

    // --- URI 受信（export） ---

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

        val eventDeferred = async { viewModel.events.first() }
        runCurrent()

        viewModel.onConfirmCreate()
        assertTrue(viewModel.uiState.value.isExporting)

        viewModel.onUriReceived(uri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isExporting)
        assertEquals(BackupUiEvent.ExportFailed, eventDeferred.await())
    }

    // --- 復元 ---

    @Test
    fun onRestoreUriReceived_null_doesNothing() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onRestoreUriReceived(null)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPreviewLoading)
        assertFalse(viewModel.uiState.value.showRestoreConfirmDialog)
    }

    @Test
    fun onRestoreUriReceived_previewSuccess_showsRestoreConfirmDialog() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns BackupRestoreResult.Success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPreviewLoading)
        assertTrue(viewModel.uiState.value.showRestoreConfirmDialog)
        assertFalse(viewModel.uiState.value.restoreIncludeCookies)
    }

    @Test
    fun onRestoreUriReceived_invalidBackup_emitsInvalidBackup() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns BackupRestoreResult.Invalid("bad")
        }
        val viewModel = BackupViewModel(repository)

        val eventDeferred = async { viewModel.events.first() }
        runCurrent()

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPreviewLoading)
        assertEquals(BackupUiEvent.InvalidBackup, eventDeferred.await())
    }

    @Test
    fun onRestoreCookiesToggle_updatesRestoreIncludeCookies() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns BackupRestoreResult.Success(containsCookies = true)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showRestoreConfirmDialog)

        viewModel.onRestoreCookiesToggle(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.restoreIncludeCookies)

        viewModel.onRestoreCookiesToggle(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.restoreIncludeCookies)
    }

    @Test
    fun onRestoreConfirmCancel_hidesDialog() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns BackupRestoreResult.Success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showRestoreConfirmDialog)

        viewModel.onRestoreConfirmCancel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showRestoreConfirmDialog)
    }

    @Test
    fun onConfirmRestore_success_showsRestorePreparedDialog() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns BackupRestoreResult.Success(containsCookies = false)
            coEvery { restoreBackup(uri, false) } returns BackupRestoreResult.Success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showRestoreConfirmDialog)

        viewModel.onConfirmRestore(uri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRestoring)
        assertTrue(viewModel.uiState.value.showRestorePreparedDialog)
    }

    @Test
    fun onRestorePreparedDismiss_hidesPreparedDialog() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns BackupRestoreResult.Success(containsCookies = false)
            coEvery { restoreBackup(uri, false) } returns BackupRestoreResult.Success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        viewModel.onConfirmRestore(uri)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showRestorePreparedDialog)

        viewModel.onRestorePreparedDismiss()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showRestorePreparedDialog)
    }

    @Test
    fun onConfirmRestore_failure_emitsRestorePrepareFailed() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns BackupRestoreResult.Success(containsCookies = false)
            coEvery { restoreBackup(uri, false) } returns BackupRestoreResult.Failure("err")
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()

        val eventDeferred = async { viewModel.events.first() }
        runCurrent()

        viewModel.onConfirmRestore(uri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRestoring)
        assertEquals(BackupUiEvent.RestorePrepareFailed, eventDeferred.await())
    }

    @Test
    fun onBackupClick_whileRestoring_doesNothing() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns BackupRestoreResult.Success(containsCookies = false)
            coEvery { restoreBackup(uri, false) } returns BackupRestoreResult.Success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        viewModel.onConfirmRestore(uri)
        // onConfirmRestore は state を isRestoring=true にして非同期 task を起動する。
        // advance 前に onBackupClick を呼び、state guard で拒否されることを確認する。
        viewModel.onBackupClick()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showConfirmDialog)
    }

    // --- Cookie 条件表示 ---

    @Test
    fun onRestoreUriReceived_previewWithoutCookies_setsPreviewContainsCookiesFalse() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns BackupRestoreResult.Success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.previewContainsCookies)
        assertTrue(viewModel.uiState.value.showRestoreConfirmDialog)
    }

    @Test
    fun onRestoreUriReceived_previewWithCookies_setsPreviewContainsCookiesTrue() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns BackupRestoreResult.Success(containsCookies = true)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.previewContainsCookies)
        assertTrue(viewModel.uiState.value.showRestoreConfirmDialog)
    }

    @Test
    fun onConfirmRestore_withoutCookies_alwaysFalse() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns BackupRestoreResult.Success(containsCookies = false)
            // previewContainsCookies=false なので、restoreIncludeCookies が true でも false で呼ばれる
            coEvery { restoreBackup(uri, false) } returns BackupRestoreResult.Success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        // toggle ON だが preview に Cookie はない
        viewModel.onRestoreCookiesToggle(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.restoreIncludeCookies)

        viewModel.onConfirmRestore(uri)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showRestorePreparedDialog)
        // mock は restoreBackup(uri, false) だけ設定しているので、true で呼ばれると unmockk 例外になる。
        // ここでテストが pass している = false で呼ばれたことが確認できる。
    }

    // --- ヘルパー ---

    private fun createViewModel(): BackupViewModel {
        val repository: BackupRepository = mockk()
        return BackupViewModel(repository)
    }
}
