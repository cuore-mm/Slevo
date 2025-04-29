package com.websarva.wings.android.bbsviewer.ui.bbslist.service

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.bbsviewer.R
import kotlinx.coroutines.delay

@Composable
fun BBSListScreen(
    uiState: BbsServiceUiState,
    modifier: Modifier = Modifier,
    onClick: (ServiceInfo) -> Unit,
    onLongClick: (String) -> Unit,
) {
    // スピナーをいつ表示／非表示にするかを管理する状態
    val spinnerState by produceState<SpinnerState>(
        initialValue = SpinnerState.Idle,
        uiState.isLoading
    ) {
        if (uiState.isLoading) {
            // 読み込み開始 → 300ms 後に表示
            delay(300)
            value = SpinnerState.Showing(startedAt = System.currentTimeMillis())
        } else {
            // 読み込み完了 → 最低表示時間を確保してから非表示
            when (val prev = value) {
                is SpinnerState.Showing -> {
                    val elapsed = System.currentTimeMillis() - prev.startedAt
                    if (elapsed < 300) delay(300 - elapsed)
                }

                else -> { /* Idle ならそのまま */ }
            }
            value = SpinnerState.Idle
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        if (spinnerState is SpinnerState.Showing) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else if (!uiState.isLoading && uiState.services.isEmpty()) {
            // 空ならセンターにメッセージを表示
            Text(
                text = stringResource(R.string.message_no_registered_bbs),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(
                    items = uiState.services,
                    key = { _, service -> service.domain }
                ) { index, service ->
                    val isSelected = service.domain in uiState.selected

                    ServiceCard(
                        service = service,
                        selected = isSelected,
                        selectMode = uiState.selectMode,
                        onClick = { onClick(service) },
                        onLongClick = { onLongClick(service.domain) },
                    )

                    // 最後のアイテムじゃなければ Divider を入れる
                    if (index < uiState.services.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private sealed class SpinnerState {
    object Idle : SpinnerState()
    data class Showing(val startedAt: Long) : SpinnerState()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ServiceCard(
    service: ServiceInfo,
    selected: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier
            .combinedClickable(
                onClick = {
                    if (!selectMode) {
                        onClick()
                    }else{
                        onLongClick()
                    }
                },
                onLongClick = { if (!selectMode) onLongClick() }
            ),
        leadingContent = {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
            )
        },
        headlineContent = {
            Text(
                text = service.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        trailingContent = {
            Text(
                text = "(${service.boardCount})",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                // 選択中の背景
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                // 通常時の背景
                MaterialTheme.colorScheme.surface
            },
        )
    )
}


@Composable
fun AddBbsDialog(
    enteredUrl: String,
    onUrlChange: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onCancel: () -> Unit,
    onAdd: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.prompt_enter_bbs_menu_or_board_url),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column {
                TextField(
                    value = enteredUrl,
                    onValueChange = onUrlChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.url)) },
                    placeholder = { Text("https://…") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAdd,
                enabled = enteredUrl.isNotBlank()
            ) {
                Text(text = stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun DeleteBbsDialog(
    onDismissRequest: () -> Unit,
    onDelete: () -> Unit,
    selectedCount: Int
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        text = {
            Text(text = "$selectedCount" + stringResource(R.string.dialog_confirm_delete_bbs))
        },
        confirmButton = {
            TextButton(
                onClick = onDelete
            ) {
                Text(text = stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ServiceCardPreview() {
    ServiceCard(
        service = ServiceInfo(
            domain = "1",
            name = "5ch.net",
            boardCount = 100
        ),
        onClick = {},
        onLongClick = {},
        selected = false,
        selectMode = false,
    )
}

@Preview(showBackground = true)
@Composable
fun AddBBSDialogPreview() {
    AddBbsDialog(
        onDismissRequest = {},
        enteredUrl = "",
        onUrlChange = {},
        onCancel = {},
        onAdd = {}
    )
}

@Preview(showBackground = true)
@Composable
fun DeleteBBSDialogPreview() {
    DeleteBbsDialog(
        onDismissRequest = {},
        onDelete = {},
        selectedCount = 1
    )
}
