package com.websarva.wings.android.slevo.ui.thread.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.ui.thread.components.MomentumBar
import com.websarva.wings.android.slevo.ui.thread.components.NewArrivalBar
import com.websarva.wings.android.slevo.ui.thread.dialog.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.dialog.ReplyPopup
import com.websarva.wings.android.slevo.ui.thread.item.PostItem
import com.websarva.wings.android.slevo.ui.thread.state.ReplyInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.foundation.gestures.scrollBy
import kotlin.math.min

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThreadScreen(
    modifier: Modifier = Modifier,
    uiState: ThreadUiState,
    listState: LazyListState = rememberLazyListState(),
    navController: NavHostController,
    onAutoScrollBottom: () -> Unit = {},
    onBottomRefresh: () -> Unit = {},
    onLastRead: (Int) -> Unit = {},
    onReplyToPost: (Int) -> Unit = {},
) {
    // 投稿一覧（nullの場合は空リスト）
    val posts = uiState.posts ?: emptyList()
    // 表示対象の投稿（フィルタ済み）
    val visiblePosts = uiState.visiblePosts
    // 表示用の投稿データ（ReplyInfo型）
    val displayPosts = visiblePosts.map { it.post }
    // 各投稿の返信数
    val replyCounts = uiState.replyCounts
    // 新着バーを表示するインデックス
    val firstAfterIndex = uiState.firstAfterIndex
    // ポップアップ表示用のスタック
    val popupStack = remember { androidx.compose.runtime.mutableStateListOf<PopupInfo>() }
    // NG（非表示）対象の投稿番号リスト
    val ngNumbers = uiState.ngPostNumbers
    val density = LocalDensity.current

    LaunchedEffect(listState, visiblePosts, uiState.sortType) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    delay(500)
                    if (!listState.isScrollInProgress) {
                        val layoutInfo = listState.layoutInfo
                        val lastRead = if (!listState.canScrollForward) {
                            posts.size
                        } else {
                            val half = layoutInfo.viewportEndOffset / 2
                            layoutInfo.visibleItemsInfo
                                .filter { it.offset < half }
                                .mapNotNull { info ->
                                    val idx = info.index - 1
                                    if (idx in visiblePosts.indices) {
                                        val display = visiblePosts[idx]
                                        if (uiState.sortType != ThreadSortType.TREE || display.depth == 0) {
                                            display.num
                                        } else {
                                            null
                                        }
                                    } else null
                                }
                                .maxOrNull()
                        }
                        lastRead?.let { onLastRead(it) }
                    }
                }
            }
    }

    val autoScrollDpPerSec = 40f
    val isScrollInProgress = listState.isScrollInProgress
    LaunchedEffect(uiState.isAutoScroll, isScrollInProgress, autoScrollDpPerSec, density) {
        if (!uiState.isAutoScroll || isScrollInProgress) return@LaunchedEffect
        val pxPerSec = with(density) { autoScrollDpPerSec.dp.toPx() }
        var lastTime: Long? = null
        while (isActive) {
            val dt = withFrameNanos { now ->
                val prev = lastTime
                lastTime = now
                if (prev == null) 0f else (now - prev) / 1_000_000_000f
            }
            if (dt == 0f) continue

            val consumed = listState.scrollBy(pxPerSec * dt)
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val atEnd = last != null &&
                last.index == info.totalItemsCount - 1 &&
                last.offset + last.size <= info.viewportEndOffset + 1

            if (atEnd || consumed == 0f) {
                onAutoScrollBottom()
                continue
            }
        }
    }

    val refreshThresholdPx = with(density) { 80.dp.toPx() }
    var overscroll by remember { mutableFloatStateOf(0f) }
    var triggerRefresh by remember { mutableStateOf(false) }
    val nestedScrollConnection = remember(listState, posts.size) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (overscroll > 0f && available.y > 0f) {
                    val consume = min(overscroll, available.y)
                    overscroll -= consume
                    triggerRefresh = overscroll >= refreshThresholdPx
                    Offset(0f, consume)
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!listState.canScrollForward && available.y < 0f) {
                    overscroll -= available.y
                    triggerRefresh = overscroll >= refreshThresholdPx
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (triggerRefresh) {
                    onLastRead(posts.size)
                    onBottomRefresh()
                }
                overscroll = 0f
                triggerRefresh = false
                return Velocity.Zero
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(nestedScrollConnection),
                state = listState,
            ) {
                if (visiblePosts.isNotEmpty()) {
                    val firstIndent = if (uiState.sortType == ThreadSortType.TREE) {
                        visiblePosts.first().depth
                    } else {
                        0
                    }
                    item(key = "thread_header_divider") {
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp * firstIndent))
                    }
                }

                itemsIndexed(
                    items = visiblePosts,
                    key = { _, display -> "${display.num}_${display.dimmed}" }
                ) { idx, display ->
                    val postNum = display.num
                    val post = display.post
                    val index = postNum - 1
                    val indent = if (uiState.sortType == ThreadSortType.TREE) {
                        display.depth
                    } else {
                        0
                    }
                    val nextIndent = if (idx + 1 < visiblePosts.size) {
                        if (uiState.sortType == ThreadSortType.TREE) {
                            visiblePosts[idx + 1].depth
                        } else {
                            0
                        }
                    } else {
                        0
                    }

                    // 再構成を発生させない座標ホルダ（クリック時のみ参照）
                    data class OffsetHolder(var value: IntOffset)

                    val itemOffsetHolder = remember { OffsetHolder(IntOffset.Zero) }
                    Column {
                        if (firstAfterIndex != -1 && idx == firstAfterIndex) {
                            NewArrivalBar()
                        }
                        PostItem(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                val pos = coords.positionInWindow()
                                itemOffsetHolder.value = IntOffset(pos.x.toInt(), pos.y.toInt())
                            },
                            post = post,
                            postNum = postNum,
                            idIndex = uiState.idIndexList.getOrElse(index) { 1 },
                            idTotal = if (post.id.isBlank()) 1 else uiState.idCountMap[post.id]
                                ?: 1,
                            navController = navController,
                            boardName = uiState.boardInfo.name,
                            boardId = uiState.boardInfo.boardId,
                            headerTextScale = if (uiState.isIndividualTextScale) uiState.headerTextScale else uiState.textScale * 0.85f,
                            bodyTextScale = if (uiState.isIndividualTextScale) uiState.bodyTextScale else uiState.textScale,
                            lineHeight = if (uiState.isIndividualTextScale) uiState.lineHeight else DEFAULT_THREAD_LINE_HEIGHT,
                            indentLevel = indent,
                            replyFromNumbers = uiState.replySourceMap[postNum] ?: emptyList(),
                            isMyPost = postNum in uiState.myPostNumbers,
                            dimmed = display.dimmed,
                            searchQuery = uiState.searchQuery,
                            onReplyFromClick = { nums ->
                                val offset = if (popupStack.isEmpty()) {
                                    itemOffsetHolder.value
                                } else {
                                    val last = popupStack.last()
                                    IntOffset(
                                        last.offset.x,
                                        (last.offset.y - last.size.height).coerceAtLeast(0)
                                    )
                                }
                                val targets = nums.filterNot { it in ngNumbers }.mapNotNull { num ->
                                    posts.getOrNull(num - 1)
                                }
                                if (targets.isNotEmpty()) {
                                    popupStack.add(PopupInfo(targets, offset))
                                }
                            },
                            onReplyClick = { num ->
                                if (num in 1..posts.size && num !in ngNumbers) {
                                    val target = posts[num - 1]
                                    val baseOffset = itemOffsetHolder.value
                                    val offset = if (popupStack.isEmpty()) {
                                        baseOffset
                                    } else {
                                        val last = popupStack.last()
                                        IntOffset(
                                            last.offset.x,
                                            (last.offset.y - last.size.height).coerceAtLeast(0)
                                        )
                                    }
                                    popupStack.add(PopupInfo(listOf(target), offset))
                                }
                            },
                            onMenuReplyClick = { onReplyToPost(it) },
                            onIdClick = { id ->
                                val offset = if (popupStack.isEmpty()) {
                                    itemOffsetHolder.value
                                } else {
                                    val last = popupStack.last()
                                    IntOffset(
                                        last.offset.x,
                                        (last.offset.y - last.size.height).coerceAtLeast(0)
                                    )
                                }
                                val targets = posts.mapIndexedNotNull { idx, p ->
                                    val num = idx + 1
                                    if (p.id == id && num !in ngNumbers) p else null
                                }
                                if (targets.isNotEmpty()) {
                                    popupStack.add(PopupInfo(targets, offset))
                                }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                start = 16.dp * min(
                                    indent,
                                    nextIndent
                                )
                            )
                        )
                    }
                }
            }
            // 中央の区切り線
            VerticalDivider()

            // 右側: 固定の勢いバー
            MomentumBar(
                modifier = Modifier
                    .width(24.dp)
                    .fillMaxHeight(),
                posts = displayPosts,
                replyCounts = replyCounts,
                lazyListState = listState,
                firstAfterIndex = firstAfterIndex,
                myPostNumbers = uiState.myPostNumbers
            )
        }

        if (popupStack.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event =
                                    awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            )
        }

        ReplyPopup(
            popupStack = popupStack,
            posts = posts,
            replySourceMap = uiState.replySourceMap,
            idCountMap = uiState.idCountMap,
            idIndexList = uiState.idIndexList,
            ngPostNumbers = ngNumbers,
            myPostNumbers = uiState.myPostNumbers,
            navController = navController,
            boardName = uiState.boardInfo.name,
            boardId = uiState.boardInfo.boardId,
            headerTextScale = if (uiState.isIndividualTextScale) uiState.headerTextScale else uiState.textScale * 0.85f,
            bodyTextScale = if (uiState.isIndividualTextScale) uiState.bodyTextScale else uiState.textScale,
            lineHeight = if (uiState.isIndividualTextScale) uiState.lineHeight else DEFAULT_THREAD_LINE_HEIGHT,
            searchQuery = uiState.searchQuery,
            onClose = { if (popupStack.isNotEmpty()) popupStack.removeAt(popupStack.lastIndex) }
        )

        if (uiState.isLoading) {
            LinearProgressIndicator(
                progress = { uiState.loadProgress },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                color = ProgressIndicatorDefaults.linearColor,
                trackColor = ProgressIndicatorDefaults.linearTrackColor,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }

        val arrowRotation by animateFloatAsState(
            targetValue = if (triggerRefresh) 180f else (overscroll / refreshThresholdPx).coerceIn(
                0f,
                1f
            ) * 180f,
            label = "arrowRotation"
        )

        if (uiState.isLoading) {
            ContainedLoadingIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
            )
        } else if (overscroll > 0f) {
            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .rotate(arrowRotation)
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Preview(showBackground = true)
@Composable
fun ThreadScreenPreview() {
    val previewPosts = listOf(
        ReplyInfo(
            name = "名無しさん",
            email = "sage",
            date = "2025/07/09(水) 19:40:25.769",
            id = "test1",
            beLoginId = "12345",
            beRank = "DIA(20000)",
            beIconUrl = "http://img.2ch.net/ico/hikky2.gif",
            content = "これはテスト投稿です。"
        ),
        ReplyInfo(
            name = "名無しさん",
            email = "sage",
            date = "2025/07/09(水) 19:41:00.123",
            id = "test2",
            content = "別のテスト投稿です。"
        )
    )
    val uiState = ThreadUiState(
        posts = previewPosts,
        boardInfo = com.websarva.wings.android.slevo.data.model.BoardInfo(
            0L,
            "board",
            "https://example.com/"
        ),
        idCountMap = previewPosts.groupingBy { it.id }.eachCount(),
        idIndexList = previewPosts.mapIndexed { i, _ -> i + 1 },
        replySourceMap = emptyMap()
    )
    ThreadScreen(
        uiState = uiState,
        navController = NavHostController(LocalContext.current),
        onAutoScrollBottom = {},
        onBottomRefresh = {}
    )
}
