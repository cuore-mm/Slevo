package com.websarva.wings.android.bbsviewer.ui.threadlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(
    modifier: Modifier = Modifier,
    threads: List<ThreadInfo>,
    onClick: (ThreadInfo) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
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

@Preview(showBackground = true)
@Composable
fun ThreadCardPreview() {
    ThreadCard(
        threadInfo = ThreadInfo(
            title = "タイトル",
            key = "key",
            resCount = 10,
            date = ThreadDate(2023, 1, 1, 1, 1)
        ),
        onClick = {}
    )
}
