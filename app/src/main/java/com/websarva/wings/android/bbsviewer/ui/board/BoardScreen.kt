package com.websarva.wings.android.bbsviewer.ui.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.data.model.ThreadDate
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    modifier: Modifier = Modifier,
    threads: List<ThreadInfo>,
    onClick: (ThreadInfo) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    listState: LazyListState = rememberLazyListState()
) {
    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            state = listState,
        ) {
            // リスト全体の先頭に区切り線を追加
            if (threads.isNotEmpty()) { // リストが空でない場合のみ線を表示
                item {
                    HorizontalDivider()
                }
            }

            itemsIndexed(threads) { index, thread ->
                ThreadCard(
                    threadInfo = thread,
                    onClick = onClick
                )
                // 各アイテムの下に区切り線を表示
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ThreadCard(
    threadInfo: ThreadInfo,
    onClick: (ThreadInfo) -> Unit
) {
    val momentumFormatter = remember { DecimalFormat("0.0") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onClick(threadInfo) })
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = threadInfo.title,
            color = if (threadInfo.isVisited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.padding(4.dp))
        Row {
            Text(
                text = threadInfo.date.run { "$year/$month/$day $hour:%02d".format(minute) },
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            if (threadInfo.newResCount > 0) {
                Text(
                    text = threadInfo.newResCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = momentumFormatter.format(threadInfo.momentum),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = threadInfo.resCount.toString().padStart(4),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ThreadCardPreview() {
    ThreadCard(
        threadInfo = ThreadInfo(
            title = "タイトル",
            key = "key",
            resCount = 10,
            date = ThreadDate(2023, 1, 1, 1, 1, "月"),
            momentum = 1235.4,
            isVisited = true,
            newResCount = 3,
        ),
        onClick = {}
    )
}

