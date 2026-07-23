package com.websarva.wings.android.slevo.ui.settings.backup

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.backup.export.BackupExportResult
import com.websarva.wings.android.slevo.data.backup.BackupRepository
import com.websarva.wings.android.slevo.data.backup.restore.BackupConfirmationMetadata
import com.websarva.wings.android.slevo.data.backup.restore.BackupRestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * バックアップ作成と復元画面の ViewModel。
 *
 * バックアップ作成の確認ダイアログ・export 実行と、
 * 復元のファイル選択・preview 読取・確認ダイアログ・restore 実行を管理する。
 * 操作結果は [BackupUiState.pendingResults] に保持し、Snackbar 表示完了後の acknowledge
 * まで失われないようにする。
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /** completion の ID 採番と operation state 更新を一つの state transition に直列化する。 */
    private val completionMutex = Mutex()
    private var nextResultId = 0L

    /** 復元ファイル選択で受け取った URI。画面回転で消えないよう ViewModel で保持する。 */
    private var pendingRestoreUri: Uri? = null

    // --- バックアップ作成（既存） ---

    fun onBackupClick() {
        if (_uiState.value.isExporting || _uiState.value.isRestoring) return
        _uiState.update { it.copy(showConfirmDialog = true, includeCookies = false) }
    }

    fun onCookiesToggle(include: Boolean) {
        _uiState.update { it.copy(includeCookies = include) }
    }

    fun onConfirmCancel() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    fun onConfirmCreate() {
        _uiState.update { it.copy(showConfirmDialog = false, isExporting = true) }
    }

    fun onUriReceived(uri: Uri?) {
        if (uri == null) {
            _uiState.update { it.copy(isExporting = false) }
            return
        }
        val includeCookies = _uiState.value.includeCookies
        viewModelScope.launch {
            val result = backupRepository.exportBackup(uri, includeCookies)
            when (result) {
                is BackupExportResult.Success -> completeOperation(
                    createResult = ::exportSucceeded,
                    stateUpdate = { it.copy(isExporting = false) },
                )
                is BackupExportResult.Failure -> completeOperation(
                    createResult = ::exportFailed,
                    stateUpdate = { it.copy(isExporting = false) },
                )
            }
        }
    }

    // --- 復元 ---

    /** 復元ボタン押下 → ファイル選択開始。 */
    fun onRestoreClick() {
        if (_uiState.value.isExporting || _uiState.value.isRestoring) return
        // ファイル選択は Compose 側の launcher で起動する。
        // ViewModel は後続の uri 受信を待つだけ。
        pendingRestoreUri = null
        _uiState.update {
            it.copy(
                isPreviewLoading = false,
                restorePreview = null,
                restoreIncludeCookies = false,
            )
        }
    }

    /** SAF launcher が復元 ZIP の URI を返した → preview 読取開始。 */
    fun onRestoreUriReceived(uri: Uri?) {
        if (uri == null) {
            // ユーザーがファイル選択をキャンセルした。
            return
        }
        pendingRestoreUri = uri
        _uiState.update {
            it.copy(
                isPreviewLoading = true,
                restorePreview = null,
                restoreIncludeCookies = false,
            )
        }
        viewModelScope.launch {
            val result = backupRepository.previewBackup(uri)
            // 新しい選択やキャンセルで URI が変わった場合、古い preview は state を上書きしない。
            if (pendingRestoreUri != uri) return@launch
            when (result) {
                is BackupRestoreResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isPreviewLoading = false,
                            restorePreview = result.metadata.toUiState(),
                            restoreIncludeCookies = false,
                        )
                    }
                }
                is BackupRestoreResult.Invalid -> {
                    completeOperation(
                        createResult = ::invalidBackup,
                        stateUpdate = {
                            it.copy(
                                isPreviewLoading = false,
                                restorePreview = null,
                                restoreIncludeCookies = false,
                            )
                        },
                    )
                }
                is BackupRestoreResult.Failure -> {
                    completeOperation(
                        createResult = ::restorePrepareFailed,
                        stateUpdate = {
                            it.copy(
                                isPreviewLoading = false,
                                restorePreview = null,
                                restoreIncludeCookies = false,
                            )
                        },
                    )
                }
            }
        }
    }

    /** 復元確認ダイアログのクッキー checkbox 切替。 */
    fun onRestoreCookiesToggle(include: Boolean) {
        _uiState.update { it.copy(restoreIncludeCookies = include) }
    }

    /** 復元確認ダイアログのキャンセル。 */
    fun onRestoreConfirmCancel() {
        pendingRestoreUri = null
        _uiState.update { it.copy(restorePreview = null, restoreIncludeCookies = false) }
    }

    /** 復元確認ダイアログの「復元」ボタン押下 → pending restore 作成開始。 */
    fun onConfirmRestore() {
        val uri = pendingRestoreUri ?: return
        val preview = _uiState.value.restorePreview ?: return
        val includeCookies = preview.containsCookies && _uiState.value.restoreIncludeCookies
        pendingRestoreUri = null
        _uiState.update {
            it.copy(
                restorePreview = null,
                restoreIncludeCookies = false,
                isRestoring = true,
            )
        }
        // バックアップに Cookie が含まれていない場合は強制的に false にする。
        logD("onConfirmRestore: containsCookies=${preview.containsCookies}" +
            " restoreIncludeCookies=$includeCookies" +
            " effective=$includeCookies")
        viewModelScope.launch {
            val result = backupRepository.restoreBackup(uri, includeCookies)
            when (result) {
                is BackupRestoreResult.Success -> {
                    // 復元準備成功 → 完了ダイアログを表示する。
                    _uiState.update { it.copy(isRestoring = false, showRestorePreparedDialog = true) }
                }
                is BackupRestoreResult.Invalid -> {
                    completeOperation(
                        createResult = ::invalidBackup,
                        stateUpdate = { it.copy(isRestoring = false) },
                    )
                }
                is BackupRestoreResult.Failure -> {
                    completeOperation(
                        createResult = ::restorePrepareFailed,
                        stateUpdate = { it.copy(isRestoring = false) },
                    )
                }
            }
        }
    }

    /** 復元準備完了ダイアログを閉じる（「あとで」押下時）。 */
    fun onRestorePreparedDismiss() {
        _uiState.update { it.copy(showRestorePreparedDialog = false) }
    }

    /** 表示済み結果の ID が現在の queue 先頭と一致する場合だけ先頭を削除する。 */
    fun acknowledgeResult(resultId: Long) {
        _uiState.update { state ->
            if (state.pendingResults.firstOrNull()?.id != resultId) {
                state
            } else {
                state.copy(pendingResults = state.pendingResults.drop(1))
            }
        }
    }

    /** 処理中の操作抑制判定。 */
    val isBusy: Boolean get() = _uiState.value.isExporting || _uiState.value.isRestoring

    private fun logD(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            // JVM unit test の Log stub では例外になるため握りつぶす。
        }
    }

    /** 完了結果へ ID を付け、対応 state と FIFO queue を同じ atomic transition で更新する。 */
    private suspend fun completeOperation(
        createResult: (Long) -> BackupUiEvent,
        stateUpdate: (BackupUiState) -> BackupUiState,
    ) {
        completionMutex.withLock {
            val resultId = nextResultId + 1
            nextResultId = resultId
            val result = createResult(resultId)
            _uiState.update { state ->
                stateUpdate(state).copy(pendingResults = state.pendingResults + result)
            }
        }
    }

    private fun exportSucceeded(id: Long): BackupUiEvent = BackupUiEvent.ExportSucceeded(id)

    private fun exportFailed(id: Long): BackupUiEvent = BackupUiEvent.ExportFailed(id)

    private fun invalidBackup(id: Long): BackupUiEvent = BackupUiEvent.InvalidBackup(id)

    private fun restorePrepareFailed(id: Long): BackupUiEvent =
        BackupUiEvent.RestorePrepareFailed(id)

    /** data 層の success metadata を、dialog が保持する raw UI state へ変換する。 */
    private fun BackupConfirmationMetadata.toUiState(): RestorePreviewUiState = RestorePreviewUiState(
        createdAt = createdAt,
        appVersionName = appVersionName,
        appVersionCode = appVersionCode,
        containsCookies = containsCookies,
    )

    /** 定数。 */
    private companion object {
        private const val TAG = "BackupViewModel"
    }
}
