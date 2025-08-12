package com.websarva.wings.android.bbsviewer.ui.thread.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.ui.thread.viewmodel.NgIdViewModel

@Composable
fun NgIdDialog(
    idText: String,
    boardText: String = "",
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
                Text(text = stringResource(R.string.target_board))
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showBoardDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (board.isNotEmpty()) board else stringResource(R.string.board))
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
        val viewModel: NgIdViewModel = hiltViewModel()
        val boards by viewModel.boards.collectAsState()

        BoardListDialog(
            boards = boards,
            onDismiss = { showBoardDialog = false },
            onSelect = { info ->
                board = info.name
                showBoardDialog = false
            }
        )
    }
}

@Composable
fun BoardListDialog(
    boards: List<BoardInfo>,
    onDismiss: () -> Unit,
    onSelect: (BoardInfo) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.medium) {
            LazyColumn {
                items(boards, key = { it.boardId }) { info ->
                    ListItem(
                        headlineContent = { Text(info.name) },
                        supportingContent = { Text(info.url) },
                        modifier = Modifier.clickable { onSelect(info) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NgIdDialogPreview() {
    NgIdDialog(
        idText = "abcd",
        onConfirm = { _, _, _ -> },
        onDismiss = {}
    )
}

@Preview(showBackground = true)
@Composable
fun BoardListDialogPreview() {
    BoardListDialog(
        boards = listOf(
            BoardInfo(1L, "board1", "https://example.com/board1"),
            BoardInfo(2L, "board2", "https://example.com/board2"),
        ),
        onDismiss = {},
        onSelect = {},
    )
}
