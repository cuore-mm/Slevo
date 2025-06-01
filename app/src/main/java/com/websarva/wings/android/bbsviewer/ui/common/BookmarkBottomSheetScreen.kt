package com.websarva.wings.android.bbsviewer.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.model.Groupable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Groupable> BookmarkBottomSheet(
    modifier: Modifier = Modifier,
    groups: List<T>,
    selectedGroupId: Long?,
    onGroupSelected: (groupId: Long) -> Unit,
    onUnbookmarkRequested: () -> Unit,
    onAddGroup: () -> Unit,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        BookmarkSheetContent(
            groups = groups,
            selectedGroupId = selectedGroupId,
            onGroupSelected = onGroupSelected,
            onUnbookmarkRequested = onUnbookmarkRequested,
            onAddGroup = onAddGroup,
        )
    }
}

@Composable
private fun <T : Groupable> BookmarkSheetContent(
    groups: List<T>,
    selectedGroupId: Long?,
    onGroupSelected: (groupId: Long) -> Unit,
    onUnbookmarkRequested: () -> Unit,
    onAddGroup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // タイトル行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.group),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAddGroup) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // グループ一覧
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),                          // 2列固定
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp),                          // 必要に応じて高さ制限
            horizontalArrangement = Arrangement.spacedBy(8.dp),    // アイテム間の横スペース
            verticalArrangement = Arrangement.spacedBy(8.dp),    // 行間の縦スペース
            contentPadding = PaddingValues(8.dp)           // グリッド全体の余白
        ) {
            items(groups, key = { group -> group.id }) { group ->
                val isSelected = group.id == selectedGroupId
                val bgColor = if (isSelected) {
                    Color(group.colorHex.toColorInt())
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                Card(
                    modifier = Modifier
                        .clickable {
                            if (isSelected) { // ★ すでに選択されているグループをタップした場合
                                onUnbookmarkRequested() // ★ お気に入り解除をリクエスト
                            } else { // ★ 選択されていないグループをタップした場合
                                onGroupSelected(group.id) // ★ グループ選択/変更
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(48.dp)
                    ) {
                        // 左側にCardの上下に沿うように色帯を表示
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(10.dp)
                                .background(color = Color(group.colorHex.toColorInt()))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = group.name,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// Preview用のダミーデータと呼び出し
private data class PreviewGroup(
    override val id: Long,
    override val name: String,
    override val colorHex: String,
    override val sortOrder: Int
) : Groupable

@Preview(showBackground = true)
@Composable
fun BookmarkSheetPreview() {
    val previewGroups = listOf(
        PreviewGroup(1, "グループA", "#FF0000", 1),
        PreviewGroup(2, "グループB", "#00FF00", 2),
        PreviewGroup(3, "グループC", "#0000FF", 3),
        PreviewGroup(4, "グループD（とても長い名前）", "#FFFF00", 4),
    )
    MaterialTheme { // PreviewでもThemeを適用
        BookmarkSheetContent(
            groups = previewGroups,
            selectedGroupId = 1L,
            onGroupSelected = {},
            onUnbookmarkRequested = {},
            onAddGroup = {}
        )
    }
}
