package com.websarva.wings.android.bbsviewer.ui.thread.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo
import com.websarva.wings.android.bbsviewer.data.model.NgType
import com.websarva.wings.android.bbsviewer.ui.thread.state.NgUiState
import com.websarva.wings.android.bbsviewer.ui.thread.viewmodel.NgViewModel

@Composable
fun NgDialogRoute(
    id: Long? = null,
    text: String,
    type: NgType,
    boardName: String = "",
    boardId: Long? = null,
    isRegex: Boolean = false,
    onDismiss: () -> Unit,
    viewModel: NgViewModel = hiltViewModel(),
    onConfirm: () -> Unit = { viewModel.saveNg() },
) {
    val uiState = viewModel.uiState.collectAsState().value
    val boards = viewModel.filteredBoards.collectAsState().value

    LaunchedEffect(id, text, boardName, boardId, type, isRegex) {
        viewModel.initialize(id, text, boardName, boardId, type, isRegex)
    }

    NgDialog(
        uiState = uiState,
        onDismiss = onDismiss,
        onConfirmClick = {
            onConfirm()
            onDismiss()
        },
        onTextChange = { viewModel.setText(it) },
        onRegexChange = { viewModel.setRegex(it) },
        onOpenBoardDialog = { viewModel.setShowBoardDialog(true) },
        onCloseBoardDialog = { viewModel.setShowBoardDialog(false) },
        onSelectBoard = { viewModel.setBoard(it) },
        onQueryChange = { viewModel.setBoardQuery(it) },
        boards = boards,
    )
}

@Composable
fun NgDialog(
    uiState: NgUiState,
    onDismiss: () -> Unit,
    onConfirmClick: () -> Unit,
    onTextChange: (String) -> Unit,
    onRegexChange: (Boolean) -> Unit,
    onOpenBoardDialog: () -> Unit,
    onCloseBoardDialog: () -> Unit,
    onSelectBoard: (BoardInfo) -> Unit,
    onQueryChange: (String) -> Unit,
    boards: List<BoardInfo>,
) {
    val labelResId = when (uiState.type) {
        NgType.USER_ID -> R.string.id_label
        NgType.USER_NAME -> R.string.name_label
        NgType.WORD -> R.string.word_label
        NgType.THREAD_TITLE -> R.string.thread_title_label
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.ng_setting),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !uiState.isRegex, onClick = { onRegexChange(false) })
                    Text(text = stringResource(R.string.string_literal))
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = uiState.isRegex, onClick = { onRegexChange(true) })
                    Text(text = stringResource(R.string.regular_expression))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.text,
                    onValueChange = onTextChange,
                    label = { Text(stringResource(labelResId)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(text = stringResource(R.string.target_board))
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onOpenBoardDialog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (uiState.isAllBoards) {
                            stringResource(R.string.all_boards)
                        } else {
                            uiState.boardName.ifEmpty { stringResource(R.string.board) }
                        },
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onConfirmClick) {
                        Text(text = stringResource(R.string.save))
                    }
                }
            }
        }
    }
    if (uiState.showBoardDialog) {
        BoardListDialog(
            boards = boards,
            query = uiState.boardQuery,
            onQueryChange = onQueryChange,
            onDismiss = onCloseBoardDialog,
            onSelect = {
                onSelectBoard(it)
                onCloseBoardDialog()
            },
        )
    }
}

@Composable
fun BoardListDialog(
    boards: List<BoardInfo>,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (BoardInfo) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.medium) {
            LazyColumn {
                item {
                    TextField(
                        value = query,
                        onValueChange = onQueryChange,
                        placeholder = { Text(stringResource(R.string.search_board_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.all_boards)) },
                        modifier = Modifier.clickable { onSelect(BoardInfo(0L, "", "")) },
                    )
                    Divider()
                }
                items(boards, key = { it.boardId }) { info ->
                    ListItem(
                        headlineContent = { Text(info.name) },
                        supportingContent = { Text(info.url) },
                        modifier = Modifier.clickable { onSelect(info) },
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NgDialogPreview() {
    NgDialog(
        uiState = NgUiState(text = "abcd"),
        onDismiss = {},
        onConfirmClick = {},
        onTextChange = {},
        onRegexChange = {},
        onOpenBoardDialog = {},
        onCloseBoardDialog = {},
        onSelectBoard = { _ -> },
        onQueryChange = {},
        boards = emptyList(),
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
        query = "",
        onQueryChange = {},
        onDismiss = {},
        onSelect = {},
    )
}
