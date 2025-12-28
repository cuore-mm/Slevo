package com.websarva.wings.android.slevo.ui.thread.sheet

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.data.model.ThreadDate
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.common.CopyDialog
import com.websarva.wings.android.slevo.ui.common.CopyItem
import com.websarva.wings.android.slevo.ui.common.LabeledIconButton
import com.websarva.wings.android.slevo.ui.common.SlevoBottomSheet
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToBoard
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.dialog.NgDialogRoute
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
    tabsViewModel: TabsViewModel? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCopyDialog by remember { mutableStateOf(false) }
    var showNgDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val threadUrl = parseBoardUrl(threadInfo.url)?.let { (host, boardKey) ->
        "https://$host/test/read.cgi/$boardKey/${threadInfo.key}/"
    } ?: ""

    if (showThreadInfoSheet) {
        SlevoBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
        ) {
            ThreadInfoBottomSheetContent(
                threadInfo = threadInfo,
                boardName = boardInfo.name,
                onBoardClick = {
                    val route = AppRoute.Board(
                        boardId = boardInfo.boardId,
                        boardName = boardInfo.name,
                        boardUrl = boardInfo.url
                    )
                    navController.navigateToBoard(
                        route = route,
                        tabsViewModel = tabsViewModel,
                    )
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
                onNgClick = {
                    showNgDialog = true
                    onDismissRequest()
                },
                onShareClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, threadUrl)
                        putExtra(Intent.EXTRA_TITLE, threadInfo.title)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
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
    if (showNgDialog) {
        NgDialogRoute(
            text = threadInfo.title,
            type = NgType.THREAD_TITLE,
            boardName = boardInfo.name,
            boardId = boardInfo.boardId.takeIf { it != 0L },
            onDismiss = { showNgDialog = false }
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
    onNgClick: () -> Unit,
    onShareClick: () -> Unit,
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
        val actionButtons = listOf(
            ThreadInfoActionButton(
                icon = Icons.AutoMirrored.Filled.Article,
                label = boardName,
                onClick = onBoardClick
            ),
            ThreadInfoActionButton(
                icon = Icons.Filled.ContentCopy,
                label = stringResource(R.string.copy),
                onClick = onCopyClick
            ),
            ThreadInfoActionButton(
                icon = Icons.Filled.Block,
                label = stringResource(R.string.ng_registration),
                onClick = onNgClick
            ),
            ThreadInfoActionButton(
                icon = Icons.Filled.OpenInBrowser,
                label = stringResource(R.string.open_in_external_browser),
                onClick = onOpenBrowserClick
            ),
            ThreadInfoActionButton(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.share),
                onClick = onShareClick
            )
        )
        val totalSlots = THREAD_INFO_GRID_COLUMNS * THREAD_INFO_GRID_ROWS
        val placeholders = (totalSlots - actionButtons.size).coerceAtLeast(0)

        LazyVerticalGrid(
            columns = GridCells.Fixed(THREAD_INFO_GRID_COLUMNS),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(actionButtons) { action ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LabeledIconButton(
                        icon = action.icon,
                        label = action.label,
                        onClick = action.onClick,
                    )
                }
            }
            items(placeholders) {
                Box(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private const val THREAD_INFO_GRID_COLUMNS = 4
private const val THREAD_INFO_GRID_ROWS = 2

private data class ThreadInfoActionButton(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ThreadInfoBottomSheetContentPreview() {
    ThreadInfoBottomSheetContent(
        threadInfo = ThreadInfo(
            title = "お前らこのスレ開いてから一分以内にamazonの問い合わせ番号書いてみ？",
            resCount = 100,
            momentum = 1234.5,
            date = ThreadDate(2024, 5, 1, 12, 34, "水")
        ),
        boardName = "なんでも実況J",
        onBoardClick = {},
        onOpenBrowserClick = {},
        onCopyClick = {},
        onNgClick = {},
        onShareClick = {},
    )
}
