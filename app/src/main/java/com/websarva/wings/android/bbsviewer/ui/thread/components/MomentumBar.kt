package com.websarva.wings.android.bbsviewer.ui.thread.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.bbsviewer.ui.thread.state.ReplyInfo
import com.websarva.wings.android.bbsviewer.ui.theme.imageUrlColor
import com.websarva.wings.android.bbsviewer.ui.theme.threadUrlColor
import com.websarva.wings.android.bbsviewer.ui.theme.urlColor
import kotlinx.coroutines.launch

@Composable
fun MomentumBar(
    modifier: Modifier = Modifier,
    posts: List<ReplyInfo>,
    lazyListState: LazyListState
) {
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val linkImageColor = imageUrlColor()
    val linkThreadColor = threadUrlColor()
    val linkOtherColor = urlColor()
    val maxBarWidthDp = 24.dp

    var barHeight by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    Canvas(
        modifier = modifier
            .onSizeChanged { barHeight = it.height }
            .pointerInput(posts, barHeight) {
                detectTapGestures { offset ->
                    if (barHeight > 0 && posts.isNotEmpty()) {
                        val postHeight = barHeight.toFloat() / posts.size
                        val index = (offset.y / postHeight).toInt().coerceIn(0, posts.lastIndex)
                        scope.launch { lazyListState.scrollToItem(index) }
                    }
                }
            }
            .pointerInput(posts, barHeight) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        if (barHeight > 0 && posts.isNotEmpty()) {
                            val postHeight = barHeight.toFloat() / posts.size
                            val index = (offset.y / postHeight).toInt().coerceIn(0, posts.lastIndex)
                            scope.launch { lazyListState.scrollToItem(index) }
                        }
                    },
                    onVerticalDrag = { change, _ ->
                        if (barHeight > 0 && posts.isNotEmpty()) {
                            val postHeight = barHeight.toFloat() / posts.size
                            val index = (change.position.y / postHeight).toInt()
                                .coerceIn(0, posts.lastIndex)
                            scope.launch { lazyListState.scrollToItem(index) }
                        }
                    }
                )
            }
    ) {
        val canvasHeight = size.height
        val canvasWidth = size.width
        val maxBarWidthPx = maxBarWidthDp.toPx()

        if (posts.isNotEmpty()) {
            val postHeight = canvasHeight / posts.size

            if (posts.size > 1) {
                val windowSize = 1
                val smoothedMomentum = posts.mapIndexed { index, _ ->
                    val start = (index - windowSize / 2).coerceAtLeast(0)
                    val end = (index + windowSize / 2).coerceAtMost(posts.lastIndex)
                    val subList = posts.subList(start, end + 1)
                    subList.map { it.momentum }.average().toFloat()
                }
                val maxFractionOfBar = 0.5f
                val minFractionOfBar = 0.0f

                val points = smoothedMomentum.mapIndexed { index, m ->
                    val used = minFractionOfBar + (maxFractionOfBar - minFractionOfBar) * m
                    val x = maxBarWidthPx * used
                    val y = index * postHeight
                    Offset(x, y)
                }
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(points.first().x, points.first().y)
                    for (i in 0 until points.size - 1) {
                        val p = points[i]
                        val q = points[i + 1]
                        val mid = Offset((p.x + q.x) / 2f, (p.y + q.y) / 2f)
                        quadraticTo(p.x, p.y, mid.x, mid.y)
                    }
                    lineTo(points.last().x, points.last().y)
                    lineTo(points.last().x, canvasHeight)
                    lineTo(0f, canvasHeight)
                    close()
                }
                drawPath(path, color = barColor)
            }

            val firstVisible = lazyListState.firstVisibleItemIndex
            val visibleCount = lazyListState.layoutInfo.visibleItemsInfo.size
            if (visibleCount > 0) {
                val indicatorTop = firstVisible * postHeight
                val indicatorHeight = visibleCount * postHeight
                drawRect(
                    color = indicatorColor,
                    topLeft = Offset(x = 0f, y = indicatorTop),
                    size = Size(width = canvasWidth, height = indicatorHeight)
                )
            }

            val dotRadius = 2.dp.toPx()
            val shift = dotRadius * 1.5f
            posts.forEachIndexed { index, post ->
                val centerY = index * postHeight + postHeight / 2f
                var offsetIndex = 0
                if (post.urlFlags and ReplyInfo.HAS_IMAGE_URL != 0) {
                    val cx = canvasWidth - dotRadius - offsetIndex * shift
                    drawCircle(color = linkImageColor, radius = dotRadius, center = Offset(cx, centerY))
                    offsetIndex++
                }
                if (post.urlFlags and ReplyInfo.HAS_THREAD_URL != 0) {
                    val cx = canvasWidth - dotRadius - offsetIndex * shift
                    drawCircle(color = linkThreadColor, radius = dotRadius, center = Offset(cx, centerY))
                    offsetIndex++
                }
                if (post.urlFlags and ReplyInfo.HAS_OTHER_URL != 0) {
                    val cx = canvasWidth - dotRadius - offsetIndex * shift
                    drawCircle(color = linkOtherColor, radius = dotRadius, center = Offset(cx, centerY))
                }
            }
        }
    }
}
