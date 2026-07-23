package com.websarva.wings.android.slevo.ui.settings.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.SlevoTopAppBar
import com.websarva.wings.android.slevo.ui.settings.SettingsCardWithListItems
import com.websarva.wings.android.slevo.ui.settings.listItemSpecOfBasic
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * バックアップ作成と復元画面の Route ラッパー。
 *
 * ViewModel、SAF launcher、Snackbar イベント収集を保持し、
 * 純粋な UI 描画は [BackupScreenContent] へ委譲する。
 *
 * @param onFinishActivity 復元準備完了後、ユーザーがアプリ終了を選択したときに
 *   現在の Activity stack を閉じる callback。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateUp: () -> Unit,
    onFinishActivity: () -> Unit = {},
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- Snackbar イベント収集 ---
    val successMessage = stringResource(id = R.string.backup_snackbar_success)
    val failureMessage = stringResource(id = R.string.backup_snackbar_failure)
    val restoreFailedMessage = stringResource(id = R.string.restore_snackbar_failed)
    val invalidBackupMessage = stringResource(id = R.string.restore_snackbar_invalid)
    LaunchedEffect(
        viewModel.events, snackbarHostState,
        successMessage, failureMessage, restoreFailedMessage, invalidBackupMessage,
    ) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    BackupUiEvent.ExportSucceeded -> successMessage
                    BackupUiEvent.ExportFailed -> failureMessage
                    BackupUiEvent.RestorePrepareFailed -> restoreFailedMessage
                    BackupUiEvent.InvalidBackup -> invalidBackupMessage
                },
            )
        }
    }

    // --- SAF launcher (バックアップ作成用) ---
    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri: Uri? ->
        viewModel.onUriReceived(uri)
    }

    // --- SAF launcher (復元用) ---
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            restoreUri = uri
            viewModel.onRestoreUriReceived(uri)
        }
    }

    // --- 委譲 ---
    BackupScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onFinishActivity = onFinishActivity,
        onBackupClick = viewModel::onBackupClick,
        onConfirmCancel = viewModel::onConfirmCancel,
        onConfirmCreate = {
            viewModel.onConfirmCreate()
            val filename = buildBackupFilename()
            createLauncher.launch(filename)
        },
        onCookiesToggle = viewModel::onCookiesToggle,
        onRestoreClick = {
            viewModel.onRestoreClick()
            openLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        },
        onRestoreConfirmCancel = viewModel::onRestoreConfirmCancel,
        onRestoreCookiesToggle = viewModel::onRestoreCookiesToggle,
        onConfirmRestore = {
            restoreUri?.let { viewModel.onConfirmRestore(it) }
        },
        onRestorePreparedDismiss = viewModel::onRestorePreparedDismiss,
    )
}

/**
 * バックアップ作成・復元画面の UI 本体。
 *
 * ViewModel にも SAF launcher にも依存せず、
 * [BackupUiState] とコールバックだけで動作する。
 * Preview から直接参照可能。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreenContent(
    uiState: BackupUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateUp: () -> Unit,
    onFinishActivity: () -> Unit,
    onBackupClick: () -> Unit,
    onConfirmCancel: () -> Unit,
    onConfirmCreate: () -> Unit,
    onCookiesToggle: (Boolean) -> Unit,
    onRestoreClick: () -> Unit,
    onRestoreConfirmCancel: () -> Unit,
    onRestoreCookiesToggle: (Boolean) -> Unit,
    onConfirmRestore: () -> Unit,
    onRestorePreparedDismiss: () -> Unit,
) {
    val isBusy = uiState.isExporting || uiState.isPreviewLoading || uiState.isRestoring

    // 確認ダイアログ（バックアップ作成）
    if (uiState.showConfirmDialog) {
        BackupConfirmDialog(
            includeCookies = uiState.includeCookies,
            onCookiesToggle = onCookiesToggle,
            onCancel = onConfirmCancel,
            onCreate = onConfirmCreate,
        )
    }

    // 確認ダイアログ（復元）
    if (uiState.showRestoreConfirmDialog) {
        RestoreConfirmDialog(
            includeCookies = uiState.restoreIncludeCookies,
            containsCookies = uiState.previewContainsCookies,
            onCookiesToggle = onRestoreCookiesToggle,
            onCancel = onRestoreConfirmCancel,
            onRestore = onConfirmRestore,
        )
    }

    // 処理中ダイアログ（バックアップ作成）
    if (uiState.isExporting) {
        ExportingDialog()
    }

    // 処理中ダイアログ（復元準備）
    if (uiState.isRestoring) {
        RestoringDialog()
    }

    // 復元準備完了ダイアログ
    if (uiState.showRestorePreparedDialog) {
        RestorePreparedDialog(
            onDismiss = onRestorePreparedDismiss,
            onFinishActivity = onFinishActivity,
        )
    }

    Scaffold(
        topBar = {
            SlevoTopAppBar(
                title = stringResource(id = R.string.backup_title),
                modifier = Modifier,
                onNavigateUp = onNavigateUp,
                scrollBehavior = null,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            SettingsCardWithListItems(
                items = listOf(
                    listItemSpecOfBasic(
                        headlineText = stringResource(id = R.string.backup_create_button),
                        supportingText = stringResource(id = R.string.backup_description),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.UploadFile,
                                contentDescription = stringResource(id = R.string.backup_create_button),
                            )
                        },
                        onClick = { if (!isBusy) onBackupClick() },
                    ),
                    listItemSpecOfBasic(
                        headlineText = stringResource(id = R.string.backup_restore_button),
                        supportingText = stringResource(id = R.string.backup_restore_description),
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Outlined.Restore,
                                contentDescription = stringResource(id = R.string.backup_restore_button),
                            )
                        },
                        onClick = { if (!isBusy) onRestoreClick() },
                    ),
                ),
                cardEnabled = !isBusy,
            )
        }
    }
}

// --- helpers ---

private fun buildBackupFilename(): String {
    val df = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    return "slevo-backup-${df.format(Date())}.zip"
}

// --- Previews ---

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
private fun BackupScreenContentPreview() {
    SlevoTheme {
        BackupScreenContent(
            uiState = BackupUiState(),
            snackbarHostState = remember { SnackbarHostState() },
            onNavigateUp = {},
            onFinishActivity = {},
            onBackupClick = {},
            onConfirmCancel = {},
            onConfirmCreate = {},
            onCookiesToggle = {},
            onRestoreClick = {},
            onRestoreConfirmCancel = {},
            onRestoreCookiesToggle = {},
            onConfirmRestore = {},
            onRestorePreparedDismiss = {},
        )
    }
}
