package com.websarva.wings.android.bbsviewer.ui.thread

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun MomentumBar(
    modifier: Modifier = Modifier,
    posts: List<ReplyInfo>,
    lazyListState: LazyListState
) {
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
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
                        scope.launch { lazyListState.animateScrollToItem(index) }
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
                            val index = (change.position.y / postHeight).toInt().coerceIn(0, posts.lastIndex)
                            scope.launch { lazyListState.scrollToItem(index) }
                        }
                    }
                )
            }
    ) {
        val canvasHeight = size.height
        val canvasWidth = size.width
        val maxBarWidthPx = maxBarWidthDp.toPx()

        if (posts.size > 1) {
            val postHeight = canvasHeight / posts.size
            val windowSize = 10
            val smoothedMomentum = posts.mapIndexed { index, _ ->
                val start = (index - windowSize / 2).coerceAtLeast(0)
                val end = (index + windowSize / 2).coerceAtMost(posts.lastIndex)
                val subList = posts.subList(start, end + 1)
                subList.map { it.momentum }.average().toFloat()
            }
            val path = Path().apply {
                moveTo(0f, 0f)
                val points = smoothedMomentum.mapIndexed { index, momentum ->
                    val x = maxBarWidthPx * momentum
                    val y = index * postHeight
                    Offset(x, y)
                }
                lineTo(points.first().x, points.first().y)
                for (i in 0 until points.size - 1) {
                    val currentPoint = points[i]
                    val nextPoint = points[i+1]
                    val midPoint = Offset((currentPoint.x + nextPoint.x) / 2, (currentPoint.y + nextPoint.y) / 2)
                    quadraticTo(currentPoint.x, currentPoint.y, midPoint.x, midPoint.y)
                }
                lineTo(points.last().x, points.last().y)
                lineTo(points.last().x, canvasHeight)
                lineTo(0f, canvasHeight)
                close()
            }
            drawPath(path, color = barColor)
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
        }
    }
}
