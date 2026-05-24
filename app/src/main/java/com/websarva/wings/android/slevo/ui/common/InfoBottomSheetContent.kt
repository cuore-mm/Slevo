package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * 情報シート（Thread・Board など）で共通利用する本文コンポーネント。
 *
 * タイトル、区切り線、任意のサブ情報、アクションボタングリッドを垂直に並べる。
 * サブ情報は呼び出し元で自由に構成できるよう Composable スロットとして受け取る。
 */
@Composable
fun InfoBottomSheetContent(
    title: String,
    actionButtons: List<InfoActionButton>,
    modifier: Modifier = Modifier,
    subtitleContent: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp)
        )
        // --- 呼び出し元固有のサブ情報 ---
        subtitleContent?.invoke()
        // --- アクション ---
        Spacer(modifier = Modifier.padding(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                actionButtons.chunked(INFO_GRID_COLUMNS).forEach { rowActions ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowActions.forEach { action ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                LabeledIconButton(
                                    icon = action.icon,
                                    label = action.label,
                                    onClick = action.onClick,
                                )
                            }
                        }
                        repeat(INFO_GRID_COLUMNS - rowActions.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private const val INFO_GRID_COLUMNS = 4

/**
 * 情報シートのアクションボタン表示情報。
 */
data class InfoActionButton(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

@Preview(showBackground = true)
@Composable
private fun InfoBottomSheetContentPreview() {
    InfoBottomSheetContent(
        title = "なんでも実況J",
        subtitleContent = {
            Text(
                text = "5ch.net",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        actionButtons = listOf(
            InfoActionButton(
                icon = Icons.Filled.ContentCopy,
                label = "コピー",
                onClick = {}
            ),
            InfoActionButton(
                icon = Icons.Filled.OpenInBrowser,
                label = "ブラウザ",
                onClick = {}
            ),
            InfoActionButton(
                icon = Icons.Filled.Share,
                label = "共有",
                onClick = {}
            ),
        ),
    )
}
