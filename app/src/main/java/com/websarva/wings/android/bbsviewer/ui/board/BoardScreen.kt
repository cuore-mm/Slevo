package com.websarva.wings.android.bbsviewer.ui.board

import android.R.attr.onClick
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.R
import com.websarva.wings.android.bbsviewer.data.model.ThreadDate
import com.websarva.wings.android.bbsviewer.data.model.ThreadInfo
import java.text.DecimalFormat
import com.websarva.wings.android.bbsviewer.data.model.THREAD_KEY_THRESHOLD
import java.time.LocalDate

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
    val (momentumMean, momentumStd) = remember(threads) {
        val values = threads.filter {
            it.key.toLongOrNull()?.let { key -> key < THREAD_KEY_THRESHOLD } ?: false
        }.map { it.momentum }
        val mean = if (values.isNotEmpty()) values.average() else 0.0
        val std = if (values.size > 1) {
            kotlin.math.sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
        } else {
            0.0
        }
        mean to std
    }

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

            itemsIndexed(
                items = threads,
                key = { _, item -> item.key }
            ) { index, thread ->
                ThreadCard(
                    threadInfo = thread,
                    onClick = onClick,
                    momentumMean = momentumMean,
                    momentumStd = momentumStd
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
    onClick: (ThreadInfo) -> Unit,
    momentumMean: Double,
    momentumStd: Double,
) {
    val momentumFormatter = remember { DecimalFormat("0.0") }
    val showInfo = threadInfo.key.toLongOrNull()?.let { it < THREAD_KEY_THRESHOLD } ?: true
    val intensity = if (momentumStd > 0 && showInfo) {
        ((threadInfo.momentum - momentumMean) / momentumStd).toFloat()
    } else {
        0f
    }
    val colorFraction = intensity.coerceAtLeast(0f).coerceAtMost(3f) / 3f
    val momentumColor = lerp(
        MaterialTheme.colorScheme.onSurface,
        MaterialTheme.colorScheme.error,
        colorFraction
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onClick(threadInfo) })
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = threadInfo.title,
            color = if (threadInfo.isVisited)
                MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.padding(4.dp))
        Row{
            if (showInfo) {
                if (threadInfo.isNew && !threadInfo.isVisited) {
                    Text(
                        text = stringResource(R.string.new_label),
                        modifier = Modifier
                            .alignByBaseline()
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.secondary)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                val dateText = threadInfo.date.run {
                    val currentYear = LocalDate.now().year
                    val datePart = if (year == currentYear) {
                        "$month/$day"
                    } else {
                        "$year/$month/$day"
                    }
                    "$datePart $hour:%02d".format(minute)
                }
                Text(
                    text = dateText,
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (showInfo) {
                Text(
                    text = momentumFormatter.format(threadInfo.momentum),
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.labelMedium,
                    color = momentumColor,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = threadInfo.resCount.toString().padStart(4),
                modifier = Modifier.alignByBaseline(),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace
            )
            if (threadInfo.newResCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${threadInfo.newResCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .alignByBaseline()
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
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
            date = ThreadDate(2023, 1, 1, 1, 1, "月"),
            momentum = 1235.4,
            isVisited = true,
            newResCount = 31,
            isNew = true,
        ),
        onClick = {},
        momentumMean = 1000.0,
        momentumStd = 100.0,
    )
}
