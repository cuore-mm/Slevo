package com.websarva.wings.android.slevo.ui.thread.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.ui.theme.imageUrlColor
import com.websarva.wings.android.slevo.ui.theme.replyCountColor
import com.websarva.wings.android.slevo.ui.theme.threadUrlColor
import com.websarva.wings.android.slevo.ui.theme.urlColor
import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import kotlinx.coroutines.launch

/**
 * スレッドのミニマップバーを描画し、スクロール位置と投稿属性に応じたマーカーを重ねる。
 *
 * posts の順序が LazyList の表示順と一致する前提で、各投稿を均等高さに正規化して表示する。
 */
@Composable
fun MomentumBar(
    modifier: Modifier = Modifier,
    posts: List<ThreadPostUiModel>,
    replyCounts: List<Int>,
    lazyListState: LazyListState,
    firstAfterIndex: Int = -1,
    myPostNumbers: Set<Int> = emptySet()
) {
    // --- 色と表示設定 ---
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    val indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val maxBarWidthDp = 24.dp

    // --- マーカー色の準備 ---
    val imageColor = imageUrlColor()
    val threadColor = threadUrlColor()
    val otherColor = urlColor()
    val replyColors = replyCounts.map { count ->
        if (count >= 5) replyCountColor(count) else Color.Unspecified
    }

    val arrivalMarkerColor = MaterialTheme.colorScheme.tertiary
    val myPostMarkerColor = MaterialTheme.colorScheme.primary

    // --- 状態とイベント ---
    var barHeight by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    /**
     * バー上のドラッグ量を投稿リストのスクロール量へ換算する。
     *
     * ミニマップは投稿数に対して等間隔のため、可視投稿の平均高さで概算し、
     * バー上の移動量とスクロール可能領域の比率を合わせる。
     */
    fun calculateDragScrollDelta(
        dragDeltaPx: Float,
        listState: LazyListState,
        currentBarHeight: Int,
        totalPosts: Int,
    ): Float {
        // --- ガード ---
        if (currentBarHeight <= 0 || totalPosts <= 0) {
            return 0f
        }

        // --- 換算係数 ---
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) {
            return 0f
        }
        val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset)
            .coerceAtLeast(0)
        if (viewportHeight == 0) {
            return 0f
        }
        val barHeightPx = currentBarHeight.toFloat()
        val measuredItems = visibleItems.filter { it.size > 0 }
        if (measuredItems.isEmpty()) {
            return 0f
        }
        val averageItemSize = measuredItems.sumOf { it.size }.toFloat() / measuredItems.size
        val totalContentHeight = averageItemSize * totalPosts
        val listScrollableHeight = (totalContentHeight - viewportHeight).coerceAtLeast(0f)
        val indicatorHeight = barHeightPx * viewportHeight / totalContentHeight
        val barScrollableHeight = (barHeightPx - indicatorHeight).coerceAtLeast(1f)
        val scrollScale = listScrollableHeight / barScrollableHeight

        // --- 変換結果 ---
        return dragDeltaPx * scrollScale
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { barHeight = it.height }
            .pointerInput(posts, barHeight) {
                detectTapGestures { offset ->
                    // ガード: 高さが未計測/投稿なしの状態では分母が崩れるため処理しない。
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
                        // ガード: 高さが未計測/投稿なしの状態では分母が崩れるため処理しない。
                        if (barHeight > 0 && posts.isNotEmpty()) {
                            val postHeight = barHeight.toFloat() / posts.size
                            val index = (offset.y / postHeight).toInt().coerceIn(0, posts.lastIndex)
                            scope.launch { lazyListState.scrollToItem(index) }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        // ガード: 高さが未計測/投稿なしの状態では分母が崩れるため処理しない。
                        if (barHeight <= 0 || posts.isEmpty()) {
                            return@detectVerticalDragGestures
                        }
                        // ドラッグ量をスクロール量へ変換し、連続スクロールで反映する。
                        val listDelta = calculateDragScrollDelta(
                            dragDeltaPx = dragAmount,
                            listState = lazyListState,
                            currentBarHeight = barHeight,
                            totalPosts = posts.size,
                        )
                        if (listDelta != 0f) {
                            change.consume()
                            lazyListState.dispatchRawDelta(listDelta)
                        }
                    }
                )
            }
    ) {
        // --- 描画領域の基準値 ---
        val canvasHeight = size.height
        val canvasWidth = size.width
        val maxBarWidthPx = maxBarWidthDp.toPx()

        // ガード: 投稿が無い場合は描画対象が存在しない。
        if (posts.isNotEmpty()) {
            // --- ミニマップの正規化 ---
            val postHeight = canvasHeight / posts.size

            // --- 勢いグラフとスクロール指標 ---
            // 単一投稿の場合は勢いグラフと指標の算出を省略する。
            if (posts.size > 1) {
                val windowSize = 1
                // 勢いは投稿順のまま簡易平滑化し、棒の幅へマッピングする。
                val smoothedMomentum = posts.mapIndexed { index, _ ->
                    val start = (index - windowSize / 2).coerceAtLeast(0)
                    val end = (index + windowSize / 2).coerceAtMost(posts.lastIndex)
                    val subList = posts.subList(start, end + 1)
                    subList.map { it.meta.momentum }.average().toFloat()
                }
                val maxFractionOfBar = 0.5f      // ← ここを 0.5 に。可変にしたければ引数化してもOK
                val minFractionOfBar = 0.0f      // 必要なら最小太さも下駄履かせられる

                val points = smoothedMomentum.mapIndexed { index, m ->
                    val used = minFractionOfBar + (maxFractionOfBar - minFractionOfBar) * m
                    val x = maxBarWidthPx * used            // ← 幅を 0..(0.5*maxBarWidthPx) に制限
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
                // --- 表示中インジケータ ---
                val layoutInfo = lazyListState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                // ガード: 可視アイテムが無い場合は指標を描画しない。
                if (visibleItems.isNotEmpty()) {
                    // 可視ピクセルの比率を投稿数相当に換算する。
                    val viewportStart = layoutInfo.viewportStartOffset
                    val viewportEnd = layoutInfo.viewportEndOffset
                    val visibleFractionCount = visibleItems.fold(0f) { acc, item ->
                        val itemSize = item.size
                        if (itemSize <= 0) {
                            return@fold acc
                        }
                        val itemStart = item.offset
                        val itemEnd = item.offset + itemSize
                        val visibleStart = itemStart.coerceAtLeast(viewportStart)
                        val visibleEnd = itemEnd.coerceAtMost(viewportEnd)
                        val visiblePx = (visibleEnd - visibleStart).coerceAtLeast(0)
                        acc + (visiblePx.toFloat() / itemSize)
                    }
                    val indicatorHeight = (visibleFractionCount * postHeight)
                        .coerceIn(0f, canvasHeight)

                    // 先頭要素の隠れ量から連続的なスクロール位置を求める。
                    val firstItem = visibleItems.first()
                    val firstItemSize = firstItem.size
                    val hiddenTopPx = (viewportStart - firstItem.offset)
                        .coerceIn(0, firstItemSize)
                    val firstItemHiddenFraction = if (firstItemSize > 0) {
                        hiddenTopPx.toFloat() / firstItemSize
                    } else {
                        0f
                    }
                    val rawIndicatorTop = (firstItem.index + firstItemHiddenFraction) * postHeight
                    val maxIndicatorTop = (canvasHeight - indicatorHeight).coerceAtLeast(0f)
                    val indicatorTop = rawIndicatorTop.coerceIn(0f, maxIndicatorTop)

                    if (indicatorHeight > 0f) {
                        drawRect(
                            color = indicatorColor,
                            topLeft = Offset(x = 0f, y = indicatorTop),
                            size = Size(width = canvasWidth, height = indicatorHeight)
                        )
                    }
                }
            }

            // --- URL マーカー ---
            val dotRadius = 3.dp.toPx()
            val dotSpacing = dotRadius * 1.3f // 重なりを減らすため間隔を広げる
            val rightMarginPx = 4.dp.toPx() // 右端の余白
            posts.forEachIndexed { index, post ->
                if (post.meta.urlFlags != 0) {
                    val y = index * postHeight + postHeight / 2f
                    val colors = buildList {
                        if (post.meta.urlFlags and ReplyInfo.HAS_IMAGE_URL != 0) add(
                            imageColor.copy(
                                alpha = 0.6f
                            )
                        )
                        if (post.meta.urlFlags and ReplyInfo.HAS_THREAD_URL != 0) add(
                            threadColor.copy(
                                alpha = 0.6f
                            )
                        )
                        if (post.meta.urlFlags and ReplyInfo.HAS_OTHER_URL != 0) add(
                            otherColor.copy(
                                alpha = 0.6f
                            )
                        )
                    }
                    colors.forEachIndexed { i, color ->
                        val x = canvasWidth - dotRadius - rightMarginPx - i * dotSpacing
                        drawCircle(color = color, radius = dotRadius, center = Offset(x, y))
                    }
                }
            }

            // --- 返信数マーカー ---
            val triangleMaxHeight = 8.dp.toPx()
            replyColors.forEachIndexed { index, color ->
                if (color != Color.Unspecified) {
                    val yCenter = index * postHeight + postHeight / 2f
                    val triangleHeight = triangleMaxHeight
                    val triangleWidth = triangleHeight / 2f
                    val path = Path().apply {
                        moveTo(0f, yCenter - triangleHeight / 2f)
                        lineTo(triangleWidth, yCenter)
                        lineTo(0f, yCenter + triangleHeight / 2f)
                        close()
                    }
                    drawPath(path = path, color = color)
                }
            }

            // --- 書き込みマーク（自分の投稿） ---
            val diamondSize = 8.dp.toPx()
            myPostNumbers.forEach { num ->
                val index = num - 1
                if (index in posts.indices) {
                    val y = index * postHeight + postHeight / 2f
                    val x = canvasWidth / 2f
                    val half = diamondSize / 2f
                    val path = Path().apply {
                        moveTo(x, y - half)
                        lineTo(x + half, y)
                        lineTo(x, y + half)
                        lineTo(x - half, y)
                        close()
                    }
                    drawPath(path = path, color = myPostMarkerColor)
                }
            }

            // --- 新着マーク ---
            if (firstAfterIndex in 0..posts.size) {
                val y = firstAfterIndex * postHeight
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = arrivalMarkerColor,
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 60, heightDp = 400)
@Composable
fun MomentumBarPreview() {
    val dummyPosts = List(30) { i ->
        val urlFlags = when (i % 5) {
            0 -> ReplyInfo.HAS_IMAGE_URL
            1 -> ReplyInfo.HAS_THREAD_URL
            2 -> ReplyInfo.HAS_OTHER_URL
            3 -> ReplyInfo.HAS_IMAGE_URL or ReplyInfo.HAS_THREAD_URL // 画像+スレ
            4 -> ReplyInfo.HAS_IMAGE_URL or ReplyInfo.HAS_THREAD_URL or ReplyInfo.HAS_OTHER_URL // 全部
            else -> 0
        }
        ThreadPostUiModel(
            header = ThreadPostUiModel.Header(
                name = "User$i",
                email = "user$i@example.com",
                date = "2025/08/17",
                id = "$i",
            ),
            body = ThreadPostUiModel.Body(
                content = "Sample post $i",
            ),
            meta = ThreadPostUiModel.Meta(
                momentum = (i % 10) / 10f,
                urlFlags = urlFlags,
            ),
        )
    }
    val listState = rememberLazyListState()
    val counts = List(dummyPosts.size) { index -> if (index % 7 == 0) 5 else 0 }
    val myPosts = setOf(3, 15, 25)
    MomentumBar(
        posts = dummyPosts,
        replyCounts = counts,
        lazyListState = listState,
        firstAfterIndex = 10,
        myPostNumbers = myPosts
    )
}
