package com.websarva.wings.android.bbsviewer.ui.board

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardGroupEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkBottomSheet(
    modifier: Modifier = Modifier,
    groups: List<BoardGroupEntity>,          // 追加：グループ一覧
    selectedGroupId: Long?,                  // 追加：現在選択中のグループID
    onGroupSelected: (Long) -> Unit,         // 追加：グループ選択時
    onUnbookmarkRequested: () -> Unit, // ★ お気に入り解除のリクエスト用コールバックを追加
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
fun BookmarkSheetContent(
    groups: List<BoardGroupEntity>,
    selectedGroupId: Long?,
    onGroupSelected: (Long) -> Unit,
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
            items(groups) { group ->
                val isSelected = group.groupId == selectedGroupId
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
                                onGroupSelected(group.groupId) // ★ グループ選択/変更
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
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BookmarkSheetPreview() {
    BookmarkSheetContent(
        onAddGroup = {},
        onGroupSelected = {},
        onUnbookmarkRequested = {},

        groups = listOf(
            BoardGroupEntity(1, "グループ1", "#FF0000", sortOrder = 1),
            BoardGroupEntity(2, "グループ2", "#00FF00", sortOrder = 2),
            BoardGroupEntity(3, "グループ3", "#0000FF", sortOrder = 3),
            BoardGroupEntity(4, "グループ4", "#FFFF00", sortOrder = 4),
        ),
        selectedGroupId = 1
    )
}
