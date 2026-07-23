package com.websarva.wings.android.slevo.ui.settings.backup

import android.net.Uri
import com.websarva.wings.android.slevo.data.backup.export.BackupExportResult
import com.websarva.wings.android.slevo.data.backup.BackupRepository
import com.websarva.wings.android.slevo.data.backup.restore.BackupConfirmationMetadata
import com.websarva.wings.android.slevo.data.backup.restore.BackupRestoreResult
import com.websarva.wings.android.slevo.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
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
    fun onUriReceived_success_queuesExportSucceededAndCompletesExport() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { exportBackup(uri, false) } returns BackupExportResult.Success
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onConfirmCreate()
        assertTrue(viewModel.uiState.value.isExporting)

        viewModel.onUriReceived(uri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExporting)
        assertEquals(1, state.pendingResults.size)
        assertEquals(1L, state.pendingResults.single().id)
        assertTrue(state.pendingResults.single() is BackupUiEvent.ExportSucceeded)
    }

    @Test
    fun onUriReceived_failure_queuesExportFailedAndCompletesExport() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { exportBackup(uri, false) } returns BackupExportResult.Failure("failed")
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onConfirmCreate()
        assertTrue(viewModel.uiState.value.isExporting)

        viewModel.onUriReceived(uri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExporting)
        assertEquals(1, state.pendingResults.size)
        assertEquals(1L, state.pendingResults.single().id)
        assertTrue(state.pendingResults.single() is BackupUiEvent.ExportFailed)
    }

    // --- 復元 ---

    @Test
    fun onRestoreUriReceived_null_doesNothing() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onRestoreUriReceived(null)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPreviewLoading)
        assertFalse(viewModel.uiState.value.restorePreview != null)
    }

    @Test
    fun onRestoreUriReceived_previewSuccess_showsRestoreConfirmDialog() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPreviewLoading)
        assertTrue(viewModel.uiState.value.restorePreview != null)
        assertFalse(viewModel.uiState.value.restoreIncludeCookies)
    }

    /** preview success は raw metadata を保持し、同一 ViewModel の再作成中も失わない。 */
    @Test
    fun onRestoreUriReceived_previewSuccess_retainsMetadataInUiState() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns success(containsCookies = true)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()

        assertEquals(
            RestorePreviewUiState(
                createdAt = "2026-07-03T00:00:00Z",
                appVersionName = "1.0.0",
                appVersionCode = 1,
                containsCookies = true,
            ),
            viewModel.uiState.value.restorePreview,
        )
    }

    @Test
    fun onRestoreUriReceived_invalidBackup_queuesInvalidBackupAndCompletesPreview() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns BackupRestoreResult.Invalid("bad")
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isPreviewLoading)
        assertEquals(1, state.pendingResults.size)
        assertEquals(1L, state.pendingResults.single().id)
        assertTrue(state.pendingResults.single() is BackupUiEvent.InvalidBackup)
    }

    @Test
    fun onRestoreCookiesToggle_updatesRestoreIncludeCookies() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns success(containsCookies = true)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.restorePreview != null)

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
            coEvery { previewBackup(uri) } returns success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.restorePreview != null)

        viewModel.onRestoreConfirmCancel()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.restorePreview != null)
        assertFalse(viewModel.uiState.value.restoreIncludeCookies)
    }

    /** 新しい file 選択を開始すると、以前の preview metadata を先に破棄する。 */
    @Test
    fun onRestoreClick_clearsPreviousPreview() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns success(containsCookies = true)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        viewModel.onRestoreCookiesToggle(true)
        viewModel.onRestoreClick()

        assertFalse(viewModel.uiState.value.restorePreview != null)
        assertFalse(viewModel.uiState.value.restoreIncludeCookies)
    }

    /** preview failure は stale metadata と Cookie 選択を結果通知と同時に破棄する。 */
    @Test
    fun onRestoreUriReceived_previewFailure_clearsPreviousPreview() = runTest(testDispatcher) {
        val firstUri = mockk<Uri>()
        val secondUri = mockk<Uri>()
        val secondResult = CompletableDeferred<BackupRestoreResult>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(firstUri) } returns success(containsCookies = true)
            coEvery { previewBackup(secondUri) } coAnswers { secondResult.await() }
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(firstUri)
        advanceUntilIdle()
        viewModel.onRestoreCookiesToggle(true)
        viewModel.onRestoreUriReceived(secondUri)
        assertFalse(viewModel.uiState.value.restorePreview != null)
        assertFalse(viewModel.uiState.value.restoreIncludeCookies)

        secondResult.complete(BackupRestoreResult.Invalid("bad"))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.restorePreview != null)
        assertFalse(viewModel.uiState.value.restoreIncludeCookies)
    }

    @Test
    fun onConfirmRestore_success_showsRestorePreparedDialog() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns success(containsCookies = false)
            coEvery { restoreBackup(uri, false) } returns success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.restorePreview != null)

        viewModel.onConfirmRestore()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRestoring)
        assertTrue(viewModel.uiState.value.showRestorePreparedDialog)
    }

    @Test
    fun onRestorePreparedDismiss_hidesPreparedDialog() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns success(containsCookies = false)
            coEvery { restoreBackup(uri, false) } returns success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        viewModel.onConfirmRestore()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showRestorePreparedDialog)

        viewModel.onRestorePreparedDismiss()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showRestorePreparedDialog)
    }

    @Test
    fun onConfirmRestore_failure_queuesRestorePrepareFailedAndCompletesRestore() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns success(containsCookies = false)
            coEvery { restoreBackup(uri, false) } returns BackupRestoreResult.Failure("err")
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()

        viewModel.onConfirmRestore()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRestoring)
        assertEquals(1, state.pendingResults.size)
        assertEquals(1L, state.pendingResults.single().id)
        assertTrue(state.pendingResults.single() is BackupUiEvent.RestorePrepareFailed)
    }

    @Test
    fun onConfirmRestore_invalidBackup_queuesInvalidBackupAndCompletesRestore() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns success(containsCookies = false)
            coEvery { restoreBackup(uri, false) } returns BackupRestoreResult.Invalid("bad")
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        viewModel.onConfirmRestore()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isRestoring)
        assertEquals(1, state.pendingResults.size)
        assertTrue(state.pendingResults.single() is BackupUiEvent.InvalidBackup)
    }

    @Test
    fun completionResults_useMonotonicIdsAndAcknowledgeOnlyCurrentHead() = runTest(testDispatcher) {
        val exportUri = mockk<Uri>()
        val restoreUri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { exportBackup(exportUri, false) } returns BackupExportResult.Success
            coEvery { previewBackup(restoreUri) } returns BackupRestoreResult.Invalid("bad")
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onConfirmCreate()
        viewModel.onUriReceived(exportUri)
        advanceUntilIdle()
        viewModel.onRestoreUriReceived(restoreUri)
        advanceUntilIdle()

        val results = viewModel.uiState.value.pendingResults
        assertEquals(listOf(1L, 2L), results.map { it.id })
        assertTrue(results[0] is BackupUiEvent.ExportSucceeded)
        assertTrue(results[1] is BackupUiEvent.InvalidBackup)

        viewModel.acknowledgeResult(999L)
        viewModel.acknowledgeResult(results[1].id)
        assertEquals(listOf(1L, 2L), viewModel.uiState.value.pendingResults.map { it.id })

        viewModel.acknowledgeResult(results[0].id)
        assertEquals(listOf(2L), viewModel.uiState.value.pendingResults.map { it.id })
        viewModel.acknowledgeResult(results[0].id)
        assertEquals(listOf(2L), viewModel.uiState.value.pendingResults.map { it.id })

        viewModel.acknowledgeResult(results[1].id)
        assertTrue(viewModel.uiState.value.pendingResults.isEmpty())
        viewModel.acknowledgeResult(results[1].id)
        assertTrue(viewModel.uiState.value.pendingResults.isEmpty())
    }

    @Test
    fun concurrentCompletions_areQueuedInCompletionOrderWithoutLoss() = runTest(testDispatcher) {
        val firstUri = mockk<Uri>()
        val secondUri = mockk<Uri>()
        val firstCompletion = CompletableDeferred<BackupExportResult>()
        val secondCompletion = CompletableDeferred<BackupExportResult>()
        val repository: BackupRepository = mockk {
            coEvery { exportBackup(firstUri, false) } coAnswers { firstCompletion.await() }
            coEvery { exportBackup(secondUri, false) } coAnswers { secondCompletion.await() }
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onConfirmCreate()
        viewModel.onUriReceived(firstUri)
        viewModel.onConfirmCreate()
        viewModel.onUriReceived(secondUri)
        runCurrent()

        secondCompletion.complete(BackupExportResult.Failure("second"))
        advanceUntilIdle()
        firstCompletion.complete(BackupExportResult.Success)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isExporting)
        assertEquals(listOf(1L, 2L), state.pendingResults.map { it.id })
        assertTrue(state.pendingResults[0] is BackupUiEvent.ExportFailed)
        assertTrue(state.pendingResults[1] is BackupUiEvent.ExportSucceeded)
    }

    @Test
    fun cancellationPaths_doNotAddPendingResults() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onConfirmCreate()
        viewModel.onUriReceived(null)
        viewModel.onRestoreUriReceived(null)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pendingResults.isEmpty())
    }

    @Test
    fun onBackupClick_whileRestoring_doesNothing() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns success(containsCookies = false)
            coEvery { restoreBackup(uri, false) } returns success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        viewModel.onConfirmRestore()
        // onConfirmRestore は state を isRestoring=true にして非同期 task を起動する。
        // advance 前に onBackupClick を呼び、state guard で拒否されることを確認する。
        viewModel.onBackupClick()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showConfirmDialog)
    }

    // --- Cookie 条件表示 ---

    @Test
    fun onRestoreUriReceived_previewWithoutCookies_setsCookiePresenceFalse() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.restorePreview?.containsCookies == true)
        assertTrue(viewModel.uiState.value.restorePreview != null)
    }

    @Test
    fun onRestoreUriReceived_previewWithCookies_setsCookiePresenceTrue() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns success(containsCookies = true)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.restorePreview?.containsCookies == true)
        assertTrue(viewModel.uiState.value.restorePreview != null)
    }

    @Test
    fun onConfirmRestore_withoutCookies_alwaysFalse() = runTest(testDispatcher) {
        val uri = mockk<Uri>()
        val repository: BackupRepository = mockk {
            coEvery { previewBackup(uri) } returns success(containsCookies = false)
            // preview に Cookie がないため、restoreIncludeCookies が true でも false で呼ばれる
            coEvery { restoreBackup(uri, false) } returns success(containsCookies = false)
        }
        val viewModel = BackupViewModel(repository)

        viewModel.onRestoreUriReceived(uri)
        advanceUntilIdle()
        // toggle ON だが preview に Cookie はない
        viewModel.onRestoreCookiesToggle(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.restoreIncludeCookies)

        viewModel.onConfirmRestore()
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

    /** ViewModel test 用の、4 field を満たす metadata success result を生成する。 */
    private fun success(containsCookies: Boolean): BackupRestoreResult.Success =
        BackupRestoreResult.Success(
            metadata = BackupConfirmationMetadata(
                createdAt = "2026-07-03T00:00:00Z",
                appVersionName = "1.0.0",
                appVersionCode = 1,
                containsCookies = containsCookies,
            ),
        )
}
