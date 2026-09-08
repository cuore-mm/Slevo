package com.websarva.wings.android.slevo.ui.bbslist.category

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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.websarva.wings.android.slevo.ui.common.addPaddingValues

/** カテゴリ一覧のロード結果を表示し、カテゴリ選択を親へ通知する。 */
@Composable
fun BoaredCategoryListScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    uiState: BoardCategoryListUiState,
    onCategoryClick: (CategoryInfo) -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            else -> {
                CategoryGrid(
                    categories = uiState.categories,
                    contentPadding = contentPadding,
                    onCategoryClick = onCategoryClick
                )
            }
        }
    }
}

@Composable
fun CategoryGrid(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    categories: List<CategoryInfo>,
    onCategoryClick: (CategoryInfo) -> Unit
) {
    // 2つずつのリストに変換。最後の要素が単数の場合は null 埋め。
    val rows: List<Pair<CategoryInfo, CategoryInfo?>> =
        categories.chunked(2).map { row ->
            // chunked(2) の返す各 row は必ず size>=1
            val first  = row[0]              // non-null
            val second = row.getOrNull(1)    // nullable
            first to second
        }

    LazyColumn(
        modifier = modifier.consumeWindowInsets(contentPadding),
        contentPadding = addPaddingValues(
            base = PaddingValues(8.dp),
            additional = contentPadding,
        ),
    ) {
        items(
            items = rows,
            key = { (left, _) -> left.categoryId }    // 左側カテゴリの ID をキーに
        ) { (left, right) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                BbsCategoryItem(
                    category = left,
                    modifier = Modifier.weight(1f),
                    onClick = onCategoryClick
                )
                VerticalDivider()
                BbsCategoryItem(
                    category = right,
                    modifier = Modifier.weight(1f),
                    onClick = onCategoryClick
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun BbsCategoryItem(
    modifier: Modifier = Modifier,
    category: CategoryInfo?,
    onClick: (CategoryInfo) -> Unit
) {
    Box(
        modifier = modifier
            .clickable(enabled = category != null) { category?.let { onClick(it) } }
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
            boardCount = 10,
            categoryId = 1L
        ),
        onClick = {}
    )
}
