package com.websarva.wings.android.slevo.ui.thread.sheet

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.websarva.wings.android.slevo.ui.common.InfoActionButton
import com.websarva.wings.android.slevo.ui.common.InfoBottomSheetContent
import com.websarva.wings.android.slevo.ui.common.SlevoBottomSheet
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToBoard
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.dialog.NgDialogRoute
import com.websarva.wings.android.slevo.ui.util.ExternalBrowserUtil
import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * スレッド情報を表示するボトムシートを制御する。
 *
 * showBoardAction が false の場合は板遷移ボタンを表示しない。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadInfoBottomSheet(
    showThreadInfoSheet: Boolean,
    onDismissRequest: () -> Unit,
    threadInfo: ThreadInfo,
    boardInfo: BoardInfo,
    navController: NavHostController,
    tabsViewModel: TabsViewModel? = null,
    showBoardAction: Boolean = true,
) {
    // --- Sheet state ---
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCopyDialog by remember { mutableStateOf(false) }
    var showNgDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // --- Thread URL ---
    val threadUrl = parseBoardUrl(threadInfo.url)?.let { (host, boardKey) ->
        "https://$host/test/read.cgi/$boardKey/${threadInfo.key}/"
    } ?: ""
    val noBrowserMessage = stringResource(R.string.no_browser_app_found)

    // --- Sheet content ---
    if (showThreadInfoSheet) {
        SlevoBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
        ) {
            ThreadInfoBottomSheetContent(
                threadInfo = threadInfo,
                boardName = boardInfo.name,
                showBoardAction = showBoardAction,
                onBoardClick = {
                    coroutineScope.launch {
                        val route = tabsViewModel?.normalizeBoardRouteForNavigation(
                            AppRoute.Board(
                                boardId = boardInfo.boardId,
                                boardName = boardInfo.name,
                                boardUrl = boardInfo.url
                            )
                        ) ?: AppRoute.Board(
                            boardId = boardInfo.boardId,
                            boardName = boardInfo.name,
                            boardUrl = boardInfo.url
                        )
                        navController.navigateToBoard(
                            route = route,
                            tabsViewModel = tabsViewModel,
                        )
                        onDismissRequest()
                    }
                },
                onOpenBrowserClick = {
                    if (threadUrl.isBlank()) {
                        Toast.makeText(context, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                        onDismissRequest()
                        return@ThreadInfoBottomSheetContent
                    }
                    val opened = ExternalBrowserUtil.openBrowserChooser(
                        context = context,
                        url = threadUrl,
                        chooserTitle = null
                    )
                    if (!opened) {
                        Toast.makeText(context, noBrowserMessage, Toast.LENGTH_SHORT).show()
                    }
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
    // --- Dialogs ---
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

/**
 * スレッド情報シートの本文を描画し、操作ボタンを並べる。
 */
@Composable
private fun ThreadInfoBottomSheetContent(
    threadInfo: ThreadInfo,
    boardName: String,
    showBoardAction: Boolean = true,
    onBoardClick: () -> Unit,
    onOpenBrowserClick: () -> Unit,
    onCopyClick: () -> Unit,
    onNgClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    // --- Summary data ---
    val momentumFormatter = remember { DecimalFormat("0.0") }
    val date = threadInfo.date

    // --- サブ情報：レス数・日付・勢い ---
    val subtitle: @Composable () -> Unit = {
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.res_count_prefix) + " ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(threadInfo.resCount.toString())
                }
            },
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
    }

    // --- アクションボタン ---
    val actionButtons = buildList {
        // 板画面から開いた場合は板遷移ボタンを省略する。
        if (showBoardAction) {
            add(
                InfoActionButton(
                    icon = Icons.AutoMirrored.Filled.Article,
                    label = boardName,
                    onClick = onBoardClick
                )
            )
        }
        add(
            InfoActionButton(
                icon = Icons.Filled.ContentCopy,
                label = stringResource(R.string.copy),
                onClick = onCopyClick
            )
        )
        add(
            InfoActionButton(
                icon = Icons.Filled.Block,
                label = stringResource(R.string.ng_registration),
                onClick = onNgClick
            )
        )
        add(
            InfoActionButton(
                icon = Icons.Filled.OpenInBrowser,
                label = stringResource(R.string.open_in_external_browser),
                onClick = onOpenBrowserClick
            )
        )
        add(
            InfoActionButton(
                icon = Icons.Filled.Share,
                label = stringResource(R.string.share),
                onClick = onShareClick
            )
        )
    }

    InfoBottomSheetContent(
        title = threadInfo.title,
        subtitleContent = subtitle,
        actionButtons = actionButtons,
    )
}

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
