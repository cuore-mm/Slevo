package com.websarva.wings.android.slevo.ui.thread.screen

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import com.websarva.wings.android.slevo.ui.common.SlevoLazyColumnScrollbar
import com.websarva.wings.android.slevo.ui.common.transition.ImageSharedTransitionKeyFactory
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.navigation.navigateToThread
import com.websarva.wings.android.slevo.ui.tabs.TabsViewModel
import com.websarva.wings.android.slevo.ui.thread.components.MomentumBar
import com.websarva.wings.android.slevo.ui.thread.components.NewArrivalBar
import com.websarva.wings.android.slevo.ui.thread.res.PostDialogTarget
import com.websarva.wings.android.slevo.ui.thread.res.PostItem
import com.websarva.wings.android.slevo.ui.thread.res.PostItemDialogs
import com.websarva.wings.android.slevo.ui.thread.res.rememberPostItemDialogState
import com.websarva.wings.android.slevo.ui.thread.sheet.PostMenuSheet
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState
import com.websarva.wings.android.slevo.ui.thread.viewmodel.buildThreadListItemKey
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import com.websarva.wings.android.slevo.ui.util.GestureHint
import com.websarva.wings.android.slevo.ui.util.detectDirectionalGesture
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    onPopupVisibilityChange: (Boolean) -> Unit = {},
    onRequestTreePopup: (postNumber: Int, baseOffset: IntOffset) -> Unit = { _, _ -> },
    onAddPopupForReplyFrom: (replyNumbers: List<Int>, baseOffset: IntOffset) -> Unit = { _, _ -> },
    onAddPopupForReplyNumber: (postNumber: Int, baseOffset: IntOffset) -> Unit = { _, _ -> },
    onAddPopupForId: (id: String, baseOffset: IntOffset) -> Unit = { _, _ -> },
    onImageLongPress: (String, List<String>) -> Unit = { _, _ -> },
    onImageLoadError: (String, ImageLoadFailureType) -> Unit = { _, _ -> },
    onImageLoadSuccess: (String) -> Unit = {},
    onImageRetry: (String) -> Unit = {},
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
    val popupStack = uiState.popupStack
    // ポップアップ表示中はリスト側の共有トランジションを無効化する。
    val enableListSharedElements = popupStack.isEmpty()
    val density = LocalDensity.current
    val dialogState = rememberPostItemDialogState()
    var menuTarget by remember { mutableStateOf<PostDialogTarget?>(null) }
    var dialogTarget by remember { mutableStateOf<PostDialogTarget?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current

    // --- ナビゲーション ---
    val onUrlClick: (String) -> Unit = { url -> uriHandler.openUri(url) }
    val onThreadUrlClick: (AppRoute.Thread) -> Unit = { route ->
        navController.navigateToThread(
            route = route,
            tabsViewModel = tabsViewModel,
        )
    }
    val onImageClick: (String, List<String>, Int, String) -> Unit =
        { _, imageUrls, tappedIndex, transitionNamespace ->
            if (imageUrls.isEmpty()) {
                // Guard: 画像が存在しない場合は遷移しない。
            } else {
                val encodedUrls = imageUrls.map { imageUrl ->
                    URLEncoder.encode(imageUrl, StandardCharsets.UTF_8.toString())
                }
                val initialIndex = tappedIndex.coerceIn(encodedUrls.indices)
                navController.navigate(
                    AppRoute.ImageViewer(
                        imageUrls = encodedUrls,
                        initialIndex = initialIndex,
                        transitionNamespace = transitionNamespace,
                    )
                )
            }
        }
    val onRequestMenu: (PostDialogTarget) -> Unit = { target ->
        menuTarget = target
    }
    val onShowTextMenu: (String, NgType) -> Unit = { text, type ->
        dialogState.showTextMenu(text = text, type = type)
    }

    LaunchedEffect(popupStack.size) {
        onPopupVisibilityChange(popupStack.isNotEmpty())
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
    var bottomRefreshArmed by remember { mutableStateOf(false) }
    var armOnNextDrag by remember { mutableStateOf(false) }
    var waitingForBottomReach by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
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
                if (
                    bottomRefreshArmed &&
                    source == NestedScrollSource.UserInput &&
                    !listState.canScrollForward &&
                    available.y < 0f
                ) {
                    overscroll -= available.y
                    val reached = overscroll >= refreshThresholdPx
                    // Guard: 未到達 -> 到達 の遷移時のみ触覚を返す。
                    if (reached && !triggerRefresh) {
                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                    triggerRefresh = reached
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (bottomRefreshArmed && triggerRefresh) {
                    onLastRead(posts.size)
                    onBottomRefresh()
                }
                overscroll = 0f
                triggerRefresh = false
                bottomRefreshArmed = false
                return Velocity.Zero
            }
        }
    }

    // Guard: 画面初期化時に既に下端にいる場合、次ドラッグで更新判定可能にする。
    LaunchedEffect(Unit) {
        if (!listState.canScrollForward) {
            armOnNextDrag = true
        }
    }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    isDragging = true
                    // Guard: 下端で指を離した後の「次ドラッグ」だけを更新判定対象にする。
                    if (!listState.canScrollForward && armOnNextDrag) {
                        bottomRefreshArmed = true
                        armOnNextDrag = false
                    }
                }

                is DragInteraction.Stop,
                is DragInteraction.Cancel -> {
                    isDragging = false
                    if (!listState.canScrollForward) {
                        // Guard: ドラッグ終了時に既に下端にいる場合、次ドラッグで更新判定可能にする。
                        armOnNextDrag = true
                    } else {
                        // Guard: ドラッグ終了時に下端にいない場合、慣性で下端に到達した後に次ドラッグで更新判定可能にする。
                        waitingForBottomReach = true
                    }
                }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }
            .collect { canScrollForward ->
                if (canScrollForward) {
                    // Guard: 下端を離れたら更新判定と次ドラッグアームを解除する。
                    bottomRefreshArmed = false
                    armOnNextDrag = false
                    waitingForBottomReach = false
                    overscroll = 0f
                    triggerRefresh = false
                } else if (waitingForBottomReach) {
                    // Guard: ドラッグ終了後に慣性で下端に到達した場合、次ドラッグで更新判定可能にする。
                    armOnNextDrag = true
                    waitingForBottomReach = false
                } else if (!isDragging) {
                    // Guard: ドラッグ以外の方法（scrollToItemなど）で下端に到達した場合、次ドラッグで更新判定可能にする。
                    armOnNextDrag = true
                }
            }
    }

    val showScrollbar by remember(listState) {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
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
                key = { idx, display -> buildThreadListItemKey(idx, display) }
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
                val transitionNamespace = remember(postNum) {
                    ImageSharedTransitionKeyFactory.threadPostNamespace(postNum)
                }
                Column {
                    if (firstAfterIndex != -1 && idx == firstAfterIndex) {
                        NewArrivalBar()
                    }
                    PostItem(
                        modifier = Modifier.onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
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
                        transitionNamespace = transitionNamespace,
                        onImageClick = onImageClick,
                        onImageLongPress = onImageLongPress,
                        imageLoadFailureByUrl = uiState.imageLoadFailureByUrl,
                        onImageLoadError = onImageLoadError,
                        onImageLoadSuccess = onImageLoadSuccess,
                        onImageRetry = onImageRetry,
                        enableSharedElement = enableListSharedElements,
                        onRequestMenu = onRequestMenu,
                        onShowTextMenu = onShowTextMenu,
                        onContentClick = {
                            val baseOffset = if (popupStack.isEmpty()) {
                                itemOffsetHolder.value
                            } else {
                                val last = popupStack.last()
                                IntOffset(
                                    last.offset.x,
                                    (last.offset.y - last.size.height).coerceAtLeast(0)
                                )
                            }
                            onRequestTreePopup(postNum, baseOffset)
                        },
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
                            onAddPopupForReplyFrom(numbs, offset)
                        },
                        onReplyClick = { num ->
                            val offset = if (popupStack.isEmpty()) {
                                itemOffsetHolder.value
                            } else {
                                val last = popupStack.last()
                                IntOffset(
                                    last.offset.x,
                                    (last.offset.y - last.size.height).coerceAtLeast(0)
                                )
                            }
                            onAddPopupForReplyNumber(num, offset)
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
                            onAddPopupForId(id, offset)
                        },
                    )
                }
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
            SlevoLazyColumnScrollbar(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                enabled = showScrollbar,
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

        if (uiState.isLoading || overscroll > 0f) {
            ContainedLoadingIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
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
