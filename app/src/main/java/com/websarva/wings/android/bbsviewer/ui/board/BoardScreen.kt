package com.websarva.wings.android.bbsviewer.ui.board

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.datasource.local.entity.BoardGroupEntity
import com.websarva.wings.android.bbsviewer.data.model.ThreadDate
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import androidx.core.graphics.toColorInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    modifier: Modifier = Modifier,
    threads: List<ThreadInfo>,
    onClick: (ThreadInfo) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(threads) { thread ->
                ThreadCard(
                    threadInfo = thread,
                    onClick = onClick
                )
            }
        }
    }
}

@Composable
fun ThreadCard(
    threadInfo: ThreadInfo,
    onClick: (ThreadInfo) -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onClick(threadInfo) }),
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Text(
                text = threadInfo.title,
            )
            Row {
                Text(
                    text = threadInfo.date.run { "$year/$month/$day $hour:%02d".format(minute) },
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = threadInfo.resCount.toString(),
                    style = MaterialTheme.typography.labelMedium
                )
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkBottomSheet(
    modifier: Modifier = Modifier,
    groups: List<BoardGroupEntity>,          // 追加：グループ一覧
    selectedGroupId: Long?,                  // 追加：現在選択中のグループID
    onGroupSelected: (Long) -> Unit,         // 追加：グループ選択時
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
            onAddGroup = onAddGroup,
        )
    }
}

@Composable
fun BookmarkSheetContent(
    groups: List<BoardGroupEntity>,
    selectedGroupId: Long?,
    onGroupSelected: (Long) -> Unit,
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
                Surface(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clickable { onGroupSelected(group.groupId) },
                    shape = RoundedCornerShape(16.dp),
                    color = bgColor
                ) {
                    Text(
                        text = group.name,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddGroupDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    onAdd: () -> Unit,
    onValueChange: (String) -> Unit,
    enteredValue: String,
    onColorSelected: (String) -> Unit,
    selectedColor: String,
    colors: List<String> = listOf(
        "#FF0000", "#00FF00", "#0000FF", "#FFFF00",
        "#FF00FF", "#00FFFF", "#FFA500", "#800080",
        "#008080", "#FFC0CB",
    )
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        text = {
            Column {
                /* ---------- ① 色選択エリア ---------- */
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    maxItemsInEachRow = 5,                             // 例：1 行あたり最大 4 個
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colors.forEach { hex ->
                        val isSelected = hex == selectedColor
                        val bgColor = Color(hex.toColorInt())
                        val border  = if (isSelected)
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else null

                        Surface(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { onColorSelected(hex) },
                            shape  = CircleShape,
                            color  = bgColor,
                            border = border
                        ) { /* 円形チップ */ }
                    }
                }

                Spacer(Modifier.height(8.dp))

                /* ---------- ② グループ名入力 ---------- */
                TextField(
                    value = enteredValue,
                    onValueChange = onValueChange,
                    singleLine = true,
                    label = { Text(stringResource(R.string.group_name)) },
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onAdd
            ) {
                Text(text = stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ThreadCardPreview() {
    ThreadCard(
        threadInfo = ThreadInfo(
            title = "タイトル",
            key = "key",
            resCount = 10,
            date = ThreadDate(2023, 1, 1, 1, 1, "月")
        ),
        onClick = {}
    )
}

@Preview(showBackground = true)
@Composable
fun BookmarkDialogPreview() {
    BookmarkSheetContent(
        onAddGroup = {},
        onGroupSelected = {},
        groups = listOf(
            BoardGroupEntity(1, "グループ1", "#FF0000", sortOrder = 1),
            BoardGroupEntity(2, "グループ2", "#00FF00", sortOrder = 2),
            BoardGroupEntity(3, "グループ3", "#0000FF", sortOrder = 3),
            BoardGroupEntity(4, "グループ4", "#FFFF00", sortOrder = 4),
        ),
        selectedGroupId = 1
    )
}

@Preview(showBackground = true)
@Composable
fun AddGroupDialogPreview() {
    var name by remember { mutableStateOf("") }
    var selColor by remember { mutableStateOf("#FF4081") }
    val palette = listOf("#FF4081", "#3F51B5", "#4CAF50", "#FF9800")

    AddGroupDialog(
        onDismissRequest = {},
        onAdd = {},
        onValueChange = { name = it },
        onColorSelected = { selColor = it },
        enteredValue = name,
        selectedColor = selColor
    )
}
