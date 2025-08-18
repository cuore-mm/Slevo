package com.websarva.wings.android.bbsviewer.ui.thread.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.websarva.wings.android.bbsviewer.ui.thread.state.ReplyInfo
import com.websarva.wings.android.bbsviewer.ui.thread.item.PostItem
import com.websarva.wings.android.bbsviewer.ui.thread.components.MomentumBar
import com.websarva.wings.android.bbsviewer.ui.thread.dialog.PopupInfo
import com.websarva.wings.android.bbsviewer.ui.thread.state.ThreadSortType
import com.websarva.wings.android.bbsviewer.ui.thread.state.ThreadUiState
import com.websarva.wings.android.bbsviewer.ui.thread.dialog.ReplyPopup

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun ThreadScreen(
    modifier: Modifier = Modifier,
    uiState: ThreadUiState,
    listState: LazyListState = rememberLazyListState(),
    navController: NavHostController,
    onBottomRefresh: () -> Unit = {},
) {
    val posts = uiState.posts ?: emptyList()
    val order = if (uiState.sortType == ThreadSortType.TREE) {
        uiState.treeOrder
    } else {
        (1..posts.size).toList()
    }
    val orderedPosts = order.mapNotNull { num ->
        posts.getOrNull(num - 1)?.let { num to it }
    }
    val filteredPosts = if (uiState.searchQuery.isNotBlank()) {
        orderedPosts.filter { it.second.content.contains(uiState.searchQuery, ignoreCase = true) }
    } else {
        orderedPosts
    }
    val displayPosts = filteredPosts.map { it.second }
    val popupStack = remember { androidx.compose.runtime.mutableStateListOf<PopupInfo>() }
    val ngNumbers = uiState.ngPostNumbers

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
                if (filteredPosts.isNotEmpty()) {
                    val firstIndent = if (uiState.sortType == ThreadSortType.TREE) {
                        uiState.treeDepthMap[filteredPosts.first().first] ?: 0
                    } else {
                        0
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp * firstIndent))
                    }
                }

                items(filteredPosts) { (postNum, post) ->
                    if (postNum !in ngNumbers) {
                        val index = postNum - 1
                        val indent = if (uiState.sortType == ThreadSortType.TREE) {
                            uiState.treeDepthMap[postNum] ?: 0
                        } else {
                            0
                        }
                        var itemOffset by remember { mutableStateOf(IntOffset.Zero) }
                        PostItem(
                            modifier = Modifier.onGloballyPositioned { coords ->
                                val pos = coords.positionInWindow()
                                itemOffset = IntOffset(pos.x.toInt(), pos.y.toInt())
                            },
                            post = post,
                            postNum = postNum,
                            idIndex = uiState.idIndexList.getOrElse(index) { 1 },
                            idTotal = if (post.id.isBlank()) 1 else uiState.idCountMap[post.id] ?: 1,
                            navController = navController,
                            boardName = uiState.boardInfo.name,
                            boardId = uiState.boardInfo.boardId,
                            indentLevel = indent,
                            replyFromNumbers = uiState.replySourceMap[postNum] ?: emptyList(),
                            onReplyFromClick = { nums ->
                                val offset = if (popupStack.isEmpty()) {
                                    itemOffset
                                } else {
                                    val last = popupStack.last()
                                    IntOffset(last.offset.x, (last.offset.y - last.size.height).coerceAtLeast(0))
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
                        HorizontalDivider(modifier = Modifier.padding(start = 16.dp * indent))
                    }
                }
            }
            // 中央の区切り線
            VerticalDivider()

            // 右側: 固定の勢いバー
            MomentumBar(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight(),
                posts = displayPosts,
                lazyListState = listState
            )
        }

            ReplyPopup(
                popupStack = popupStack,
                posts = posts,
                replySourceMap = uiState.replySourceMap,
                idCountMap = uiState.idCountMap,
                idIndexList = uiState.idIndexList,
                ngPostNumbers = ngNumbers,
                navController = navController,
                boardName = uiState.boardInfo.name,
                boardId = uiState.boardInfo.boardId,
                onClose = { if (popupStack.isNotEmpty()) popupStack.removeLast() }
            )

        val arrowRotation by animateFloatAsState(
            targetValue = if (triggerRefresh) 180f else (overscroll / refreshThresholdPx).coerceIn(0f, 1f) * 180f,
            label = "arrowRotation"
        )

        if (uiState.isLoading) {
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
        boardInfo = com.websarva.wings.android.bbsviewer.data.model.BoardInfo(0L, "board", "https://example.com/"),
        idCountMap = previewPosts.groupingBy { it.id }.eachCount(),
        idIndexList = previewPosts.mapIndexed { i, _ -> i + 1 },
        replySourceMap = emptyMap()
    )
    ThreadScreen(
        uiState = uiState,
        navController = NavHostController(LocalContext.current),
        onBottomRefresh = {}
    )
}
