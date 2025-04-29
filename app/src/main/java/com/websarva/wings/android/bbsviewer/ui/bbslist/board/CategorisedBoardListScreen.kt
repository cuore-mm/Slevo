package com.websarva.wings.android.bbsviewer.ui.bbslist.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.data.model.BoardInfo

@Composable
fun CategorisedBoardListScreen(
    modifier: Modifier = Modifier,
    boards:  List<BoardInfo>,
    onBoardClick: (BoardInfo) -> Unit
) {
    // 2つずつのリストに変換。最後の要素が単数の場合は null 埋め。
    val rows = boards.chunked(2).map { row ->
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
                CategorisedBoardItem(
                    board = left,
                    modifier = Modifier.weight(1f),
                    onClick = onBoardClick
                )

                // 真ん中の縦線
                VerticalDivider()

                // 右セル
                CategorisedBoardItem(
                    board = right,
                    modifier = Modifier.weight(1f),
                    onClick = onBoardClick
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
fun CategorisedBoardItem(
    modifier: Modifier = Modifier,
    board: BoardInfo?,
    onClick: (BoardInfo) -> Unit
) {
    Box(
        modifier = modifier
            .clickable(enabled = board != null) { board?.let { onClick(it) } }
    ) {
        board?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = it.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CategorisedBoardItemPreview(){
    val board = BoardInfo(
        name = "Test Board",
        url = "https://example.com/test"
    )
    CategorisedBoardItem(
        board = board,
        onClick = {}
    )
}
