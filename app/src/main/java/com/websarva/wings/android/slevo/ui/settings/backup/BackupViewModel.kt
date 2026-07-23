package com.websarva.wings.android.slevo.ui.settings.backup

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.backup.BackupExportResult
import com.websarva.wings.android.slevo.data.backup.BackupRepository
import com.websarva.wings.android.slevo.data.backup.BackupRestoreResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * バックアップ作成と復元画面の ViewModel。
 *
 * バックアップ作成の確認ダイアログ・export 実行と、
 * 復元のファイル選択・preview 読取・確認ダイアログ・restore 実行を管理する。
 * Snackbar 表示は [BackupUiEvent] で通知し、文言解決は Compose 側で行う。
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BackupUiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<BackupUiEvent> = _events.asSharedFlow()

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
            _uiState.update { it.copy(isExporting = false) }
            when (result) {
                is BackupExportResult.Success -> _events.emit(BackupUiEvent.ExportSucceeded)
                is BackupExportResult.Failure -> _events.emit(BackupUiEvent.ExportFailed)
            }
        }
    }

    // --- 復元 ---

    /** 復元ボタン押下 → ファイル選択開始。 */
    fun onRestoreClick() {
        if (_uiState.value.isExporting || _uiState.value.isRestoring) return
        // ファイル選択は Compose 側の launcher で起動する。
        // ViewModel は後続の uri 受信を待つだけ。
    }

    /** SAF launcher が復元 ZIP の URI を返した → preview 読取開始。 */
    fun onRestoreUriReceived(uri: Uri?) {
        if (uri == null) {
            // ユーザーがファイル選択をキャンセルした。
            return
        }
        _uiState.update { it.copy(isPreviewLoading = true) }
        viewModelScope.launch {
            val result = backupRepository.previewBackup(uri)
            when (result) {
                is BackupRestoreResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isPreviewLoading = false,
                            showRestoreConfirmDialog = true,
                            restoreIncludeCookies = false,
                            previewContainsCookies = result.containsCookies,
                        )
                    }
                }
                is BackupRestoreResult.Invalid -> {
                    _uiState.update { it.copy(isPreviewLoading = false) }
                    _events.emit(BackupUiEvent.InvalidBackup)
                }
                is BackupRestoreResult.Failure -> {
                    _uiState.update { it.copy(isPreviewLoading = false) }
                    _events.emit(BackupUiEvent.RestorePrepareFailed)
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
        _uiState.update { it.copy(showRestoreConfirmDialog = false, restorePreview = null) }
    }

    /** 復元確認ダイアログの「復元」ボタン押下 → pending restore 作成開始。 */
    fun onConfirmRestore(uri: Uri) {
        _uiState.update { it.copy(showRestoreConfirmDialog = false, isRestoring = true) }
        // バックアップに Cookie が含まれていない場合は強制的に false にする
        val includeCookies = _uiState.value.previewContainsCookies && _uiState.value.restoreIncludeCookies
        logD("onConfirmRestore: previewContainsCookies=${_uiState.value.previewContainsCookies}" +
            " restoreIncludeCookies=${_uiState.value.restoreIncludeCookies}" +
            " effective=$includeCookies")
        viewModelScope.launch {
            val result = backupRepository.restoreBackup(uri, includeCookies)
            when (result) {
                is BackupRestoreResult.Success -> {
                    // 復元準備成功 → 完了ダイアログを表示する。
                    _uiState.update { it.copy(isRestoring = false, showRestorePreparedDialog = true) }
                }
                is BackupRestoreResult.Invalid -> {
                    _uiState.update { it.copy(isRestoring = false) }
                    _events.emit(BackupUiEvent.InvalidBackup)
                }
                is BackupRestoreResult.Failure -> {
                    _uiState.update { it.copy(isRestoring = false) }
                    _events.emit(BackupUiEvent.RestorePrepareFailed)
                }
            }
        }
    }

    /** 復元準備完了ダイアログを閉じる（「あとで」押下時）。 */
    fun onRestorePreparedDismiss() {
        _uiState.update { it.copy(showRestorePreparedDialog = false) }
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

    /** 定数。 */
    private companion object {
        private const val TAG = "BackupViewModel"
    }
}
