package com.websarva.wings.android.slevo.ui.bookmarklist

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.data.model.Groupable
import com.websarva.wings.android.slevo.data.model.GroupedData
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor

@Composable
fun <G : Groupable, I> GenericGroupedListScreen(
    modifier: Modifier = Modifier,
    groupedDataList: List<GroupedData<G, I>>,
    @StringRes emptyListMessageResId: Int, // リスト全体が空の場合のメッセージID
    @StringRes emptyGroupMessageResId: Int, // グループ内が空の場合のメッセージID
    itemKey: (I) -> Any, // 各アイテムのLazyColumn用キー
    itemContent: @Composable (item: I) -> Unit // 各アイテムを表示するコンポーザブルラムダ
) {
    if (groupedDataList.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = stringResource(emptyListMessageResId))
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)
        ) {
            groupedDataList.forEach { groupedData ->
                // --- グループヘッダー ---
                item(key = "group_header_${groupedData.group.id}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = groupedData.group.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "(${groupedData.items.size})",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }

                // --- グループごとのアイテムカード ---
                item(key = "group_card_${groupedData.group.id}") {
                    val groupColor = bookmarkColor(groupedData.group.colorName)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = MaterialTheme.shapes.medium, // or RoundedCornerShape(8.dp)
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(modifier = Modifier.height(IntrinsicSize.Min)) { // カラーバーの高さを行全体に合わせる
                            // カラーバー
                            Box(
                                modifier = Modifier
                                    .width(8.dp)
                                    .fillMaxHeight()
                                    .background(groupColor)
                            )
                            // アイテムリスト
                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (groupedData.items.isEmpty()) {
                                    Text(
                                        text = stringResource(emptyGroupMessageResId),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    groupedData.items.forEachIndexed { index, item ->
                                        // 引数で渡されたitemContentを使用してアイテムを描画
                                        // itemContent内でクリック処理も定義できるようにする
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            itemContent(item)
                                        }

                                        if (index < groupedData.items.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GenericGroupedListScreenPreview() {
    // サンプル用のGroupable実装
    data class SampleGroup(
        override val id: Long,
        override val name: String,
        val color: String,
        override val sortOrder: Int = 0 // デフォルト値を設定
    ) : Groupable {
        override val colorName: String get() = color
    }

    // サンプルデータ
    val groups = listOf(
        GroupedData(
            group = SampleGroup(1, "グループA", "#FF5722"),
            items = listOf("アイテム1", "アイテム2")
        ),
        GroupedData(
            group = SampleGroup(2, "グループB", "#4CAF50"),
            items = listOf("アイテム3")
        ),
        GroupedData(
            group = SampleGroup(3, "グループC", "#2196F3"),
            items = emptyList()
        )
    )

    GenericGroupedListScreen(
        groupedDataList = groups,
        emptyListMessageResId = android.R.string.emptyPhoneNumber,
        emptyGroupMessageResId = android.R.string.untitled,
        itemKey = { it },
        itemContent = { item ->
            Text(
                text = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )
        }
    )
}
