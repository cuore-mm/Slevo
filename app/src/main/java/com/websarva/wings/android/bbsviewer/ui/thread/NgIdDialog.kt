package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardEntity
import androidx.compose.ui.window.Dialog

@Composable
fun NgIdDialog(
    idText: String,
    boardText: String = "",
    boards: List<BoardEntity>,
    onConfirm: (String, Boolean, String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(idText) }
    var board by remember { mutableStateOf(boardText) }
    var isRegex by remember { mutableStateOf(false) }
    var showBoardDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.ng_setting),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !isRegex, onClick = { isRegex = false })
                    Text(text = stringResource(R.string.string_literal))
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = isRegex, onClick = { isRegex = true })
                    Text(text = stringResource(R.string.regular_expression))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.id_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable { showBoardDialog = true }
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (board.isNotBlank()) board else stringResource(R.string.target_board),
                        color = if (board.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onConfirm(text, isRegex, board) }) {
                        Text(text = stringResource(R.string.save))
                    }
                }
            }
        }
    }

    if (showBoardDialog) {
        BoardListDialog(
            boards = boards,
            onSelect = {
                board = it.name
                showBoardDialog = false
            },
            onDismiss = { showBoardDialog = false }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun NgIdDialogPreview() {
    NgIdDialog(
        idText = "abcd",
        boardText = "板1",
        boards = listOf(BoardEntity(1, 1, "https://example.com/board1", "板1")),
        onConfirm = { _, _, _ -> },
        onDismiss = {},
    )
}

@Composable
private fun BoardListDialog(
    boards: List<BoardEntity>,
    onSelect: (BoardEntity) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.boardList),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(boards) { b ->
                        Text(
                            text = b.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(b) }
                                .padding(vertical = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}
