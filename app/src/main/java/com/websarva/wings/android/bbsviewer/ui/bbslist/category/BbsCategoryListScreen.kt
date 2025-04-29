package com.websarva.wings.android.bbsviewer.ui.bbslist.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun BbsCategoryListScreen(
    modifier: Modifier = Modifier,
    uiState: BbsCategoryListUiState,
    onCategoryClick: (String) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.errorMessage != null -> {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            else -> {
                CategoryGrid(
                    categories = uiState.categories,
                    onCategoryClick = onCategoryClick
                )
            }
        }
    }
}

@Composable
fun CategoryGrid(
    modifier: Modifier = Modifier,
    categories: List<CategoryInfo>,
    onCategoryClick: (String) -> Unit
) {
    // 2つずつのリストに変換。最後の要素が単数の場合は null 埋め。
    val rows = categories.chunked(2).map { row ->
        Pair(row.getOrNull(0), row.getOrNull(1))
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
    ) {
        itemsIndexed(rows) { index, (left, right) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min) // 縦線をセルいっぱいに伸ばすため
            ) {
                // 左セル
                BbsCategoryItem(
                    category = left,
                    modifier = Modifier.weight(1f),
                    onClick = onCategoryClick
                )

                // 真ん中の縦線
                VerticalDivider()

                // 右セル
                BbsCategoryItem(
                    category = right,
                    modifier = Modifier.weight(1f),
                    onClick = onCategoryClick
                )
            }
            // 各行の下の横線
            if (index < rows.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun BbsCategoryItem(
    modifier: Modifier = Modifier,
    category: CategoryInfo?,
    onClick: (String) -> Unit
) {
    Box(
        modifier = modifier
            .clickable(enabled = category != null) { category?.let { onClick(it.name) } }
    ) {
        category?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = it.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "(${it.boardCount})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BbsCategoryItemPreview() {
    BbsCategoryItem(
        category = CategoryInfo(
            name = "Test Category",
            boardCount = 10
        ),
        onClick = {}
    )
}
