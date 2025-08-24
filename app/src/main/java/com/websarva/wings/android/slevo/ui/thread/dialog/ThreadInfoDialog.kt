package com.websarva.wings.android.slevo.ui.thread.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState

@Composable
fun ThreadInfoDialog(
    onDismissRequest: () -> Unit,
    onEvaluateClick: () -> Unit,
    onNGThreadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onArchiveClick: () -> Unit,
    uiState: ThreadUiState
) {
    Dialog(onDismissRequest = onDismissRequest) {
        // ダイアログの内容をCardで包むことで見た目を整える
        Card(
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = uiState.threadInfo?.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                with(uiState.threadInfo.date) {
                    Text(
                        text = "${year}年${month}月${day}日${dayOfWeek}曜日 $hour:%02d".format(
                            minute
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
                // ★評価のボタン（例）
                Button(
                    onClick = {
                        onEvaluateClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        repeat(5) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("評価する")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onNGThreadClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("NGThread")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onDeleteClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("削除")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onArchiveClick()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("アーカイブ")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ThreadInfoDialogPreview() {
    ThreadInfoDialog(
        onDismissRequest = { /* ダイアログを閉じる処理 */ },
        onEvaluateClick = { /* 評価処理 */ },
        onNGThreadClick = { /* NGスレッド処理 */ },
        onDeleteClick = { /* 削除処理 */ },
        onArchiveClick = { /* アーカイブ処理 */ },
        uiState = ThreadUiState(
            threadInfo = ThreadInfo(
                title = "スレッドタイトル",
            )
        )
    )
}
