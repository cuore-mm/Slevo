package com.websarva.wings.android.slevo.ui.thread.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.websarva.wings.android.slevo.data.model.BoardInfo
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.data.model.GestureAction
import com.websarva.wings.android.slevo.data.model.GestureSettings
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.common.GestureHintOverlay
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.components.MomentumBar
import com.websarva.wings.android.slevo.ui.thread.components.NewArrivalBar
import com.websarva.wings.android.slevo.ui.thread.res.PostDialogTarget
import com.websarva.wings.android.slevo.ui.thread.res.PostItemDialogs
import com.websarva.wings.android.slevo.ui.thread.res.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.res.ReplyPopup
import com.websarva.wings.android.slevo.ui.thread.res.PostItem
import com.websarva.wings.android.slevo.ui.thread.sheet.PostMenuSheet
import com.websarva.wings.android.slevo.ui.thread.res.rememberPostItemDialogState
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import com.websarva.wings.android.slevo.ui.util.GestureHint
import com.websarva.wings.android.slevo.ui.util.detectDirectionalGesture
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * スレッド画面を構成し、投稿一覧と各種オーバーレイを表示する。
 *
 * 投稿メニューやダイアログは画面レベルで集約して制御する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ThreadScreen(
    modifier: Modifier = Modifier,
    uiState: ThreadUiState,
    listState: LazyListState = rememberLazyListState(),
    navController: NavHostController,
    tabsViewModel: TabsViewModel? = null,
    showBottomBar: (() -> Unit)? = null,
    onAutoScrollBottom: () -> Unit = {},
    onBottomRefresh: () -> Unit = {},
    onLastRead: (Int) -> Unit = {},
    onReplyToPost: (Int) -> Unit = {},
    gestureSettings: GestureSettings = GestureSettings.DEFAULT,
    onGestureAction: (GestureAction) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
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
    val popupStack = remember { mutableStateListOf<PopupInfo>() }
    // NG（非表示）対象の投稿番号リスト
    val ngNumbers = uiState.ngPostNumbers
    val density = LocalDensity.current
    val dialogState = rememberPostItemDialogState()
    var menuTarget by remember { mutableStateOf<PostDialogTarget?>(null) }
    var dialogTarget by remember { mutableStateOf<PostDialogTarget?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    // --- ナビゲーション ---
    val onUrlClick: (String) -> Unit = { url -> uriHandler.openUri(url) }
    val onThreadUrlClick: (AppRoute.Thread) -> Unit = { route ->
        navController.navigateToThread(
            route = route,
            tabsViewModel = tabsViewModel,
        )
    }
    val onImageClick: (String) -> Unit = { url ->
        navController.navigate(
            AppRoute.ImageViewer(
                imageUrl = URLEncoder.encode(
                    url,
                    StandardCharsets.UTF_8.toString()
                )
            )
        )
    }
    val onRequestMenu: (PostDialogTarget) -> Unit = { target ->
        menuTarget = target
    }
    val onShowTextMenu: (String, NgType) -> Unit = { text, type ->
        dialogState.showTextMenu(text = text, type = type)
    }

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

    var gestureHint by remember { mutableStateOf<GestureHint>(GestureHint.Hidden) }
    LaunchedEffect(gestureHint) {
        if (gestureHint is GestureHint.Invalid) {
            delay(1200)
            gestureHint = GestureHint.Hidden
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .detectDirectionalGesture(
                enabled = gestureSettings.isEnabled,
                onGestureProgress = { direction ->
                    gestureHint = direction?.let {
                        GestureHint.Direction(it, gestureSettings.assignments[it])
                    } ?: GestureHint.Hidden
                },
                onGestureInvalid = {
                    gestureHint = GestureHint.Invalid
                },
            ) { direction ->
                val action = gestureSettings.assignments[direction]
                if (action == null) {
                    gestureHint = GestureHint.Direction(direction, null)
                    return@detectDirectionalGesture
                }
                gestureHint = GestureHint.Hidden
                when (action) {
                    GestureAction.ToTop -> {
                        showBottomBar?.invoke()
                        coroutineScope.launch {
                            listState.scrollToItem(0)
                        }
                    }

                    GestureAction.ToBottom -> {
                        coroutineScope.launch {
                            showBottomBar?.invoke()
                            val prevViewportEnd = listState.layoutInfo.viewportEndOffset
                            repeat(10) {
                                withFrameNanos { /* 1 フレーム待ち */ }
                                if (listState.layoutInfo.viewportEndOffset != prevViewportEnd) return@repeat
                            }
                            coroutineScope.launch {
                                val totalItems = listState.layoutInfo.totalItemsCount
                                val fallback =
                                    if (visiblePosts.isNotEmpty()) visiblePosts.size else 0
                                val targetIndex = when {
                                    totalItems > 0 -> totalItems - 1
                                    fallback > 0 -> fallback
                                    else -> 0
                                }
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    }

                    else -> onGestureAction(action)
                }
            }
    ) {
        val lazyColumnContent: LazyListScope.() -> Unit = {
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
                        idTotal = if (post.header.id.isBlank()) 1 else uiState.idCountMap[post.header.id]
                            ?: 1,
                        headerTextScale = if (uiState.isIndividualTextScale) uiState.headerTextScale else uiState.textScale * 0.85f,
                        bodyTextScale = if (uiState.isIndividualTextScale) uiState.bodyTextScale else uiState.textScale,
                        lineHeight = if (uiState.isIndividualTextScale) uiState.lineHeight else DEFAULT_THREAD_LINE_HEIGHT,
                        indentLevel = indent,
                        replyFromNumbers = uiState.replySourceMap[postNum] ?: emptyList(),
                        isMyPost = postNum in uiState.myPostNumbers,
                        dimmed = display.dimmed,
                        searchQuery = uiState.searchQuery,
                        onUrlClick = onUrlClick,
                        onThreadUrlClick = onThreadUrlClick,
                        onImageClick = onImageClick,
                        onRequestMenu = onRequestMenu,
                        onShowTextMenu = onShowTextMenu,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onReplyFromClick = { numbs ->
                            val offset = if (popupStack.isEmpty()) {
                                itemOffsetHolder.value
                            } else {
                                val last = popupStack.last()
                                IntOffset(
                                    last.offset.x,
                                    (last.offset.y - last.size.height).coerceAtLeast(0)
                                )
                            }
                            val targets = numbs.filterNot { it in ngNumbers }.mapNotNull { num ->
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
                                if (p.header.id == id && num !in ngNumbers) p else null
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

        if (uiState.showMinimapScrollbar) {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .nestedScroll(nestedScrollConnection),
                    state = listState,
                    content = lazyColumnContent
                )
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
        } else {
            LazyColumnScrollbar(
                modifier = Modifier.fillMaxSize(),
                state = listState,
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    state = listState,
                    content = lazyColumnContent
                )
            }
        }

        if (popupStack.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event =
                                    awaitPointerEvent(PointerEventPass.Initial)
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
            headerTextScale = if (uiState.isIndividualTextScale) uiState.headerTextScale else uiState.textScale * 0.85f,
            bodyTextScale = if (uiState.isIndividualTextScale) uiState.bodyTextScale else uiState.textScale,
            lineHeight = if (uiState.isIndividualTextScale) uiState.lineHeight else DEFAULT_THREAD_LINE_HEIGHT,
            searchQuery = uiState.searchQuery,
            onUrlClick = onUrlClick,
            onThreadUrlClick = onThreadUrlClick,
            onImageClick = onImageClick,
            onRequestMenu = onRequestMenu,
            onShowTextMenu = onShowTextMenu,
            onClose = { if (popupStack.isNotEmpty()) popupStack.removeAt(popupStack.lastIndex) },
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )

        // --- メニュー ---
        menuTarget?.let { target ->
            PostMenuSheet(
                postNum = target.postNum,
                onReplyClick = {
                    menuTarget = null
                    onReplyToPost(target.postNum)
                },
                onCopyClick = {
                    menuTarget = null
                    dialogTarget = target
                    dialogState.showCopyDialog()
                },
                onNgClick = {
                    menuTarget = null
                    dialogTarget = target
                    dialogState.showNgSelectDialog()
                },
                onDismiss = { menuTarget = null }
            )
        }

        // --- ダイアログ ---
        PostItemDialogs(
            target = dialogTarget,
            boardName = uiState.boardInfo.name,
            boardId = uiState.boardInfo.boardId,
            scope = coroutineScope,
            dialogState = dialogState
        )

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
        if (gestureSettings.showActionHints) {
            GestureHintOverlay(state = gestureHint)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Preview(showBackground = true)
@Composable
fun ThreadScreenPreview() {
    val previewPosts = listOf(
        ThreadPostUiModel(
            header = ThreadPostUiModel.Header(
                name = "名無しさん",
                email = "sage",
                date = "2025/07/09(水) 19:40:25.769",
                id = "test1",
                beLoginId = "12345",
                beRank = "DIA(20000)",
                beIconUrl = "http://img.2ch.net/ico/hikky2.gif",
            ),
            body = ThreadPostUiModel.Body(
                content = "これはテスト投稿です。"
            ),
        ),
        ThreadPostUiModel(
            header = ThreadPostUiModel.Header(
                name = "名無しさん",
                email = "sage",
                date = "2025/07/09(水) 19:41:00.123",
                id = "test2",
            ),
            body = ThreadPostUiModel.Body(
                content = "別のテスト投稿です。"
            ),
        ),
    )
    val uiState = ThreadUiState(
        posts = previewPosts,
        boardInfo = BoardInfo(
            0L,
            "board",
            "https://example.com/"
        ),
        idCountMap = previewPosts.groupingBy { it.header.id }.eachCount(),
        idIndexList = previewPosts.mapIndexed { i, _ -> i + 1 },
        replySourceMap = emptyMap()
    )
    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            ThreadScreen(
                uiState = uiState,
                navController = NavHostController(LocalContext.current),
                onAutoScrollBottom = {},
                onBottomRefresh = {},
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this
            )
        }
    }
}
