package com.websarva.wings.android.bbsviewer.ui.bbslist.service

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.R
import kotlin.math.roundToInt

@Composable
fun BBSListScreen(
    uiState: BbsServiceUiState,
    modifier: Modifier = Modifier,
    onClick: (ServiceInfo) -> Unit,
    onRemove: (ServiceInfo) -> Unit,
    onMove: (from: Int, to: Int) -> Unit
) {
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }

    LazyColumn(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        itemsIndexed(
            items = uiState.services,
            key = { _, svc -> svc.name }  // 一意のキー
        ) { index, service ->
            ServiceCard(
                service   = service,
                editMode  = uiState.editMode,
                onClick   = onClick,
                onRemove  = { onRemove(service) },
                onDrag    = { fromOffset, dragAmount ->
                    // ドラッグを終了したら onMove を呼ぶ
                    val toIndex = when {
                        dragAmount.y < -threshold -> (index - 1).coerceAtLeast(0)
                        dragAmount.y >  threshold -> (index + 1).coerceAtMost(uiState.services.lastIndex)
                        else -> index
                    }
                    if (toIndex != index) onMove(index, toIndex)
                }
            )
        }
    }
}

@Composable
fun ServiceCard(
    service: ServiceInfo,
    modifier: Modifier = Modifier,
    onClick: (ServiceInfo) -> Unit,
    editMode: Boolean,
    onRemove: () -> Unit,
    onDrag: (fromOffset: Offset, dragAmount: Offset) -> Unit
) {
    // ドラッグ中のオフセットを覚えておく
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .pointerInput(editMode) {
                if (editMode) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offset += dragAmount
                        },
                        onDragEnd = {
                            onDrag(offset, offset)
                            offset = Offset.Zero
                        }
                    )
                }
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clickable { if (!editMode) onClick(service) },
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ─── 左端：削除 or spacer ───
                if (editMode) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }

                // ─── メニューアイコン or spacer ───
                if (!editMode && service.boardCount != null) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }

                // ─── サービス名 ───
                Text(
                    text = service.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // ─── 板数（editMode=false のときのみ） ───
                if (!editMode && service.boardCount != null) {
                    Text(text = "(${service.boardCount})")
                }

                // ─── 右端：ドラッグハンドル or spacer ───
                if (editMode) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "並び替え",
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
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
                        .padding(top = 8.dp)
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

@Preview(showBackground = true)
@Composable
fun ServiceCardPreview() {
    ServiceCard(
        service = ServiceInfo(
            serviceId = "1",
            name = "5ch.net",
            boardCount = 100
        ),
        onClick = {},
        editMode = false,
        onRemove = {},
        onDrag = { _, _ -> }
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
