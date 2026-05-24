package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
        val totalSlots = INFO_GRID_COLUMNS * INFO_GRID_ROWS
        val placeholders = (totalSlots - actionButtons.size).coerceAtLeast(0)

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
        Card {
            LazyVerticalGrid(
                columns = GridCells.Fixed(INFO_GRID_COLUMNS),
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
}

private const val INFO_GRID_COLUMNS = 4
private const val INFO_GRID_ROWS = 2

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
                icon = androidx.compose.material.icons.Icons.Filled.ContentCopy,
                label = "コピー",
                onClick = {}
            ),
            InfoActionButton(
                icon = androidx.compose.material.icons.Icons.Filled.OpenInBrowser,
                label = "ブラウザ",
                onClick = {}
            ),
            InfoActionButton(
                icon = androidx.compose.material.icons.Icons.Filled.Share,
                label = "共有",
                onClick = {}
            ),
        ),
    )
}
