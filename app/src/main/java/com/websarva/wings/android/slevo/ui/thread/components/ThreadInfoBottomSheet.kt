package com.websarva.wings.android.slevo.ui.thread.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.ThreadDate
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.common.CopyDialog
import com.websarva.wings.android.slevo.ui.common.CopyItem
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.util.LabeledIconButton
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadInfoBottomSheet(
    showThreadInfoSheet: Boolean,
    onDismissRequest: () -> Unit,
    threadInfo: ThreadInfo,
    boardInfo: BoardInfo,
    navController: NavHostController,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCopyDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    val threadUrl = parseBoardUrl(threadInfo.url)?.let { (host, boardKey) ->
        "https://$host/test/read.cgi/$boardKey/${threadInfo.key}/"
    } ?: ""

    if (showThreadInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
        ) {
            ThreadInfoBottomSheetContent(
                threadInfo = threadInfo,
                boardName = boardInfo.name,
                onBoardClick = {
                    navController.navigate(
                        AppRoute.Board(
                            boardId = boardInfo.boardId,
                            boardName = boardInfo.name,
                            boardUrl = boardInfo.url
                        )
                    ) {
                        launchSingleTop = true
                    }
                    onDismissRequest()
                },
                onOpenBrowserClick = {
                    uriHandler.openUri(threadUrl)
                    onDismissRequest()
                },
                onCopyClick = {
                    showCopyDialog = true
                    onDismissRequest()
                },
            )
        }
    }
    if (showCopyDialog) {
        CopyDialog(
            items = listOf(
                CopyItem(
                    text = threadInfo.title,
                    label = stringResource(R.string.title)
                ),
                CopyItem(
                    text = threadUrl,
                    label = stringResource(R.string.url)
                ),
                CopyItem(
                    text = "${threadInfo.title}\n$threadUrl",
                    label = stringResource(R.string.title_and_url)
                )
            ),
            onDismissRequest = { showCopyDialog = false }
        )
    }
}

@Composable
private fun ThreadInfoBottomSheetContent(
    threadInfo: ThreadInfo,
    boardName: String,
    onBoardClick: () -> Unit,
    onOpenBrowserClick: () -> Unit,
    onCopyClick: () -> Unit,
) {
    val momentumFormatter = remember { DecimalFormat("0.0") }
    val date = threadInfo.date
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = threadInfo.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.res_count_prefix) + " ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(threadInfo.resCount.toString())
                }
            },
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Row {
            Text(
                text = stringResource(
                    R.string.thread_date,
                    date.year,
                    date.month,
                    date.day,
                    date.dayOfWeek,
                    date.hour,
                    date.minute
                ),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.padding(8.dp))
            Text(
                text = stringResource(R.string.momentum) + ": "
                        + momentumFormatter.format(threadInfo.momentum),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.padding(top = 16.dp)
        ) {
            LabeledIconButton(
                icon = Icons.AutoMirrored.Filled.Article,
                label = boardName,
                onClick = onBoardClick,
            )
            LabeledIconButton(
                icon = Icons.Filled.ContentCopy,
                label = stringResource(R.string.copy),
                onClick = onCopyClick,
            )
            LabeledIconButton(
                icon = Icons.Filled.OpenInBrowser,
                label = stringResource(R.string.open_in_external_browser),
                onClick = onOpenBrowserClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ThreadInfoBottomSheetContentPreview() {
    ThreadInfoBottomSheetContent(
        threadInfo = ThreadInfo(
            title = "スレッドタイトル",
            resCount = 100,
            momentum = 1234.5,
            date = ThreadDate(2024, 5, 1, 12, 34, "水")
        ),
        boardName = "なんでも実況J",
        onBoardClick = {},
        onOpenBrowserClick = {},
        onCopyClick = {},
    )
}
