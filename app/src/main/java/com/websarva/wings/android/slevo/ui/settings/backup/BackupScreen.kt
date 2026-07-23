package com.websarva.wings.android.slevo.ui.settings.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
 * バックアップ作成画面の Route ラッパー。
 *
 * ViewModel、SAF launcher、Snackbar イベント収集を保持し、
 * 純粋な UI 描画は [BackupScreenContent] へ委譲する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onNavigateUp: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- Snackbar イベント収集 ---
    val successMessage = stringResource(id = R.string.backup_snackbar_success)
    val failureMessage = stringResource(id = R.string.backup_snackbar_failure)
    LaunchedEffect(viewModel.events, snackbarHostState, successMessage, failureMessage) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    BackupUiEvent.ExportSucceeded -> successMessage
                    BackupUiEvent.ExportFailed -> failureMessage
                },
            )
        }
    }

    // --- SAF launcher ---
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        viewModel.onUriReceived(uri)
    }

    // --- 委譲 ---
    BackupScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateUp = onNavigateUp,
        onBackupClick = viewModel::onBackupClick,
        onConfirmCancel = viewModel::onConfirmCancel,
        // ViewModel の状態更新後、SAF 保存先選択を起動する。
        onConfirmCreate = {
            viewModel.onConfirmCreate()
            val filename = buildBackupFilename()
            launcher.launch(filename)
        },
        onCookiesToggle = viewModel::onCookiesToggle,
    )
}

/**
 * バックアップ作成画面の UI 本体。
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
    onBackupClick: () -> Unit,
    onConfirmCancel: () -> Unit,
    onConfirmCreate: () -> Unit,
    onCookiesToggle: (Boolean) -> Unit,
) {
    // 確認ダイアログ
    if (uiState.showConfirmDialog) {
        BackupConfirmDialog(
            includeCookies = uiState.includeCookies,
            onCookiesToggle = onCookiesToggle,
            onCancel = onConfirmCancel,
            onCreate = onConfirmCreate,
        )
    }

    // 処理中ダイアログ
    if (uiState.isExporting) {
        ExportingDialog()
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
                                imageVector = Icons.Outlined.CloudUpload,
                                contentDescription = stringResource(id = R.string.backup_create_button),
                            )
                        },
                        onClick = {
                            if (!uiState.isExporting) onBackupClick()
                        },
                    )
                ),
                cardEnabled = !uiState.isExporting,
            )
        }
    }
}

/**
 * 確認ダイアログ。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackupConfirmDialog(
    includeCookies: Boolean,
    onCookiesToggle: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(id = R.string.backup_confirm_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(id = R.string.backup_confirm_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(24.dp))

                val shape = MaterialTheme.shapes.largeIncreased
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .toggleable(
                            value = includeCookies,
                            enabled = true,
                            role = Role.Checkbox,
                            onValueChange = onCookiesToggle,
                        ),
                    shape = shape,
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.size(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Checkbox(
                                    checked = includeCookies,
                                    onCheckedChange = null,
                                    enabled = true,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(id = R.string.backup_include_cookies),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(id = R.string.backup_confirm_cookie_warning),
                            modifier = Modifier.padding(start = 32.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreate) {
                Text(stringResource(id = R.string.backup_confirm_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(id = android.R.string.cancel))
            }
        },
    )
}

/**
 * 処理中ダイアログ。
 */
@Composable
private fun ExportingDialog() {
    AlertDialog(
        onDismissRequest = { /* 処理中は閉じられない */ },
        title = { Text(stringResource(id = R.string.backup_exporting_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(id = R.string.backup_exporting_message))
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        },
        confirmButton = { /* 処理中はボタンなし */ },
    )
}

/**
 * 推奨ファイル名を生成する。
 */
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
            onBackupClick = {},
            onConfirmCancel = {},
            onConfirmCreate = {},
            onCookiesToggle = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BackupConfirmDialogPreview() {
    BackupConfirmDialog(
        includeCookies = false,
        onCookiesToggle = {},
        onCancel = {},
        onCreate = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun ExportingDialogPreview() {
    ExportingDialog()
}
