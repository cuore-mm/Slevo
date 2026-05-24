package com.websarva.wings.android.slevo.ui.board.screen

import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.CopyDialog
import com.websarva.wings.android.slevo.ui.common.CopyItem
import com.websarva.wings.android.slevo.ui.common.InfoActionButton
import com.websarva.wings.android.slevo.ui.common.InfoBottomSheetContent
import com.websarva.wings.android.slevo.ui.common.SlevoBottomSheet
import com.websarva.wings.android.slevo.ui.util.ExternalBrowserUtil

/**
 * 板情報を表示するボトムシートを制御する。
 *
 * 板名をタイトル、サービス名をサブ情報として表示し、
 * コピー・外部ブラウザ・共有のアクションを提供する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardInfoBottomSheet(
    showBoardInfoSheet: Boolean,
    onDismissRequest: () -> Unit,
    boardName: String,
    serviceName: String,
    boardUrl: String,
) {
    // --- Sheet state ---
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showCopyDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val noBrowserMessage = stringResource(R.string.no_browser_app_found)

    // --- Sheet content ---
    if (showBoardInfoSheet) {
        SlevoBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
        ) {
            InfoBottomSheetContent(
                title = boardName,
                subtitleContent = {
                    if (serviceName.isNotBlank()) {
                        Text(
                            text = serviceName,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                },
                actionButtons = listOf(
                    InfoActionButton(
                        icon = Icons.Filled.ContentCopy,
                        label = stringResource(R.string.copy),
                        onClick = {
                            showCopyDialog = true
                            onDismissRequest()
                        }
                    ),
                    InfoActionButton(
                        icon = Icons.Filled.OpenInBrowser,
                        label = stringResource(R.string.open_in_external_browser),
                        onClick = {
                            if (boardUrl.isBlank()) {
                                Toast.makeText(
                                    context,
                                    R.string.invalid_url,
                                    Toast.LENGTH_SHORT
                                ).show()
                                onDismissRequest()
                                return@InfoActionButton
                            }
                            val opened = ExternalBrowserUtil.openBrowserChooser(
                                context = context,
                                url = boardUrl,
                                chooserTitle = null
                            )
                            if (!opened) {
                                Toast.makeText(
                                    context,
                                    noBrowserMessage,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            onDismissRequest()
                        }
                    ),
                    InfoActionButton(
                        icon = Icons.Filled.Share,
                        label = stringResource(R.string.share),
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, boardUrl)
                                putExtra(Intent.EXTRA_TITLE, boardName)
                            }
                            context.startActivity(
                                Intent.createChooser(intent, null)
                            )
                            onDismissRequest()
                        }
                    ),
                ),
            )
        }
    }

    // --- Copy Dialog ---
    if (showCopyDialog) {
        CopyDialog(
            items = listOf(
                CopyItem(
                    text = boardName,
                    label = stringResource(R.string.title)
                ),
                CopyItem(
                    text = boardUrl,
                    label = stringResource(R.string.url)
                ),
                CopyItem(
                    text = "$boardName\n$boardUrl",
                    label = stringResource(R.string.title_and_url)
                )
            ),
            onDismissRequest = { showCopyDialog = false }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BoardInfoBottomSheetPreview() {
    BoardInfoBottomSheet(
        showBoardInfoSheet = true,
        onDismissRequest = {},
        boardName = "なんでも実況J",
        serviceName = "5ch.net",
        boardUrl = "https://example.com/board",
    )
}
