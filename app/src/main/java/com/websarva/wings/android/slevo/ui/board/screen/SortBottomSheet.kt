package com.websarva.wings.android.slevo.ui.board.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.board.state.ThreadSortKey
import com.websarva.wings.android.slevo.ui.common.SlevoBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    sortKeys: List<ThreadSortKey>,
    currentSortKey: ThreadSortKey,
    isSortAscending: Boolean,
    onSortKeySelected: (ThreadSortKey) -> Unit,
    onToggleSortOrder: () -> Unit,
) {
    SlevoBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
    ) {
        SortBottomSheetContent(
            sortKeys = sortKeys,
            currentSortKey = currentSortKey,
            isSortAscending = isSortAscending,
            onSortKeySelected = onSortKeySelected,
            onToggleSortOrder = onToggleSortOrder,
        )
    }
}

@Composable
private fun SortBottomSheetContent(
    sortKeys: List<ThreadSortKey>,
    currentSortKey: ThreadSortKey,
    isSortAscending: Boolean,
    onSortKeySelected: (ThreadSortKey) -> Unit,
    onToggleSortOrder: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        // タイトルと昇順/降順ボタン
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.sort),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row {
                val sortOrderButtonEnabled = currentSortKey != ThreadSortKey.DEFAULT
                IconButton(
                    onClick = onToggleSortOrder,
                    enabled = sortOrderButtonEnabled // <--- enabled 状態を追加
                ) {
                    Icon(
                        imageVector = if (isSortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = if (isSortAscending) "昇順" else "降順",
                        tint = if (sortOrderButtonEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(
                            alpha = 0.38f
                        ) // <--- 無効時の色
                    )
                }
            }
        }
        HorizontalDivider()

        // ソート基準のリスト
        LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
            items(sortKeys) { key ->
                SortKeyItem(
                    sortKey = key,
                    isSelected = key == currentSortKey,
                    onClick = { onSortKeySelected(key) }
                )
            }
        }
    }
}

@Composable
fun SortKeyItem(
    sortKey: ThreadSortKey,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = sortKey.displayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "選択済み",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun SortBottomSheetPreview() {
    SortBottomSheetContent(
        sortKeys = ThreadSortKey.entries,
        currentSortKey = ThreadSortKey.MOMENTUM,
        isSortAscending = false,
        onSortKeySelected = {},
        onToggleSortOrder = {},
    )
}
