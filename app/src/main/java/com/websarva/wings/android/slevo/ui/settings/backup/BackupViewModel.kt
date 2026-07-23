package com.websarva.wings.android.slevo.ui.settings.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.websarva.wings.android.slevo.data.backup.BackupExportResult
import com.websarva.wings.android.slevo.data.backup.BackupRepository
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
 * バックアップ作成画面の ViewModel。
 *
 * 確認ダイアログ表示、クッキー選択、SAF 保存先選択、エクスポート実行を管理する。
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

    // --- ユーザーアクション ---

    /** バックアップ作成ボタン押下 → 確認ダイアログ表示。 */
    fun onBackupClick() {
        if (_uiState.value.isExporting) return
        _uiState.update { it.copy(showConfirmDialog = true, includeCookies = false) }
    }

    /** 確認ダイアログのクッキー checkbox 切替。 */
    fun onCookiesToggle(include: Boolean) {
        _uiState.update { it.copy(includeCookies = include) }
    }

    /** 確認ダイアログのキャンセル。 */
    fun onConfirmCancel() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    /** 確認ダイアログの作成ボタン押下 → SAF launcher 起動用。 */
    fun onConfirmCreate() {
        _uiState.update { it.copy(showConfirmDialog = false, isExporting = true) }
    }

    /** SAF launcher が保存先 URI を返した → エクスポート開始。 */
    fun onUriReceived(uri: Uri?) {
        if (uri == null) {
            // ユーザーが保存先選択をキャンセルした。
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
}
