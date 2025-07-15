package com.websarva.wings.android.bbsviewer.ui.thread

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.navigation.NavHostController

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun ThreadScreen(
    modifier: Modifier = Modifier,
    posts: List<ReplyInfo>,
    listState: LazyListState = rememberLazyListState(),
    navController: NavHostController,
    isRefreshing: Boolean = false,
    onBottomRefresh: () -> Unit = {},
) {
    val popupStack = remember { mutableStateListOf<PopupInfo>() }
    val idCountMap = remember(posts) { posts.groupingBy { it.id }.eachCount() }
    val idIndexList = remember(posts) {
        val indexMap = mutableMapOf<String, Int>()
        posts.map { reply ->
            val idx = (indexMap[reply.id] ?: 0) + 1
            indexMap[reply.id] = idx
            idx
        }
    }

    val replySourceMap = remember(posts) {
        val map = mutableMapOf<Int, MutableList<Int>>()
        val regex = Regex(">>(\\d+)")
        posts.forEachIndexed { idx, reply ->
            regex.findAll(reply.content).forEach { match ->
                val num = match.groupValues[1].toIntOrNull() ?: return@forEach
                if (num in 1..posts.size) {
                    map.getOrPut(num) { mutableListOf() }.add(idx + 1)
                }
            }
        }
        map.mapValues { it.value.toList() }
    }

    val density = LocalDensity.current
    val refreshThresholdPx = with(density) { 80.dp.toPx() }
    var overscroll by remember { mutableStateOf(0f) }
    var triggerRefresh by remember { mutableStateOf(false) }
    val nestedScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (!listState.canScrollForward && available.y < 0f) {
                    overscroll -= available.y
                    triggerRefresh = overscroll >= refreshThresholdPx
                } else if (available.y > 0f) {
                    overscroll = 0f
                    triggerRefresh = false
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (triggerRefresh) {
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
                if (posts.isNotEmpty()) {
                    item {
                        HorizontalDivider()
                    }
                }

                itemsIndexed(posts) { index, post ->
                    var itemOffset by remember { mutableStateOf(IntOffset.Zero) }
                    PostItem(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val pos = coords.positionInWindow()
                            itemOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
                        },
                        post = post,
                        postNum = index + 1,
                        idIndex = idIndexList[index],
                        idTotal = if (post.id.isBlank()) 1 else idCountMap[post.id] ?: 1,
                        navController = navController,
                        replyFromNumbers = replySourceMap[index + 1] ?: emptyList(),
                        onReplyFromClick = { nums ->
                            val offset = if (popupStack.isEmpty()) {
                                itemOffset
                            } else {
                                val last = popupStack.last()
                                IntOffset(last.offset.x, (last.offset.y - last.size.height).coerceAtLeast(0))
                            }
                            val targets = nums.mapNotNull { num ->
                                posts.getOrNull(num - 1)
                            }
                            if (targets.isNotEmpty()) {
                                popupStack.add(PopupInfo(targets, offset))
                            }
                        },
                        onReplyClick = { num ->
                            if (num in 1..posts.size) {
                                val target = posts[num - 1]
                                val baseOffset = itemOffset
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
                        }
                    )
                    HorizontalDivider()
                }
            }
            // 中央の区切り線
            VerticalDivider()

            // 右側: 固定の勢いバー
            MomentumBar(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight(),
                posts = posts,
                lazyListState = listState
            )
        }

        ReplyPopup(
            popupStack = popupStack,
            posts = posts,
            replySourceMap = replySourceMap,
            idCountMap = idCountMap,
            idIndexList = idIndexList,
            navController = navController,
            onClose = { if (popupStack.isNotEmpty()) popupStack.removeLast() }
        )

        val arrowRotation by animateFloatAsState(
            targetValue = if (triggerRefresh) 180f else (overscroll / refreshThresholdPx).coerceIn(0f, 1f) * 180f,
            label = "arrowRotation"
        )

        if (isRefreshing) {
            CircularProgressIndicator(
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
    ThreadScreen(
        posts = listOf(
            ReplyInfo(
                name = "名無しさん",
                email = "sage",
                date = "2025/07/09(水) 19:40:25.769",
                id = "test1",
                content = "これはテスト投稿です。"
            ),
            ReplyInfo(
                name = "名無しさん",
                email = "sage",
                date = "2025/07/09(水) 19:41:00.123",
                id = "test2",
                content = "別のテスト投稿です。"
            )
        ),
        navController = NavHostController(LocalContext.current),
        isRefreshing = false,
        onBottomRefresh = {}
    )
}
