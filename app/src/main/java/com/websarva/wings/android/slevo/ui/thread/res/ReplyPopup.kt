package com.websarva.wings.android.slevo.ui.thread.res

import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import kotlin.math.min

// アニメーションの速度（ミリ秒）
private const val POPUP_ANIMATION_DURATION = 160

/**
 * 返信ポップアップの表示と操作イベントを管理する。
 *
 * 投稿の長押しメニューやダイアログは呼び出し側へ委譲する。
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ReplyPopup(
    popupStack: List<PopupInfo>,
    posts: List<ThreadPostUiModel>,
    replySourceMap: Map<Int, List<Int>>,
    idCountMap: Map<String, Int>,
    idIndexList: List<Int>,
    ngPostNumbers: Set<Int>,
    myPostNumbers: Set<Int>,
    headerTextScale: Float,
    bodyTextScale: Float,
    lineHeight: Float,
    searchQuery: String = "",
    onUrlClick: (String) -> Unit,
    onThreadUrlClick: (AppRoute.Thread) -> Unit,
    onImageClick: (String) -> Unit,
    onImageLongPress: (String, List<String>) -> Unit,
    onRequestMenu: (PostDialogTarget) -> Unit,
    onShowTextMenu: (String, NgType) -> Unit,
    onRequestTreePopup: (postNumber: Int, baseOffset: IntOffset) -> Unit,
    onAddPopupForReplyFrom: (replyNumbers: List<Int>, baseOffset: IntOffset) -> Unit,
    onAddPopupForReplyNumber: (postNumber: Int, baseOffset: IntOffset) -> Unit,
    onAddPopupForId: (id: String, baseOffset: IntOffset) -> Unit,
    onPopupSizeChange: (index: Int, size: IntSize) -> Unit,
    onClose: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // --- 表示状態管理 ---
    val visibilityStates = rememberPopupVisibilityStates(popupStack.size)

    // --- 終了操作 ---
    val closeTopPopup: () -> Unit = {
        if (visibilityStates.isNotEmpty()) {
            visibilityStates.last().targetState = false
        }
    }

    // --- 戻るハンドリング ---
    BackHandler(enabled = popupStack.isNotEmpty()) {
        closeTopPopup()
    }

    // --- ポップアップ描画 ---
    val lastIndex = popupStack.lastIndex
    Box(modifier = Modifier.fillMaxSize()) {
        // --- 背景タップ ---
        PopupBackgroundOverlay(
            popupStack = popupStack,
            onCloseTop = closeTopPopup,
        )
        popupStack.forEachIndexed { index, info ->
            val isTop = index == lastIndex
            val visibleState = visibilityStates.getOrNull(index)
                ?: MutableTransitionState(false).apply { targetState = true }

            LaunchedEffect(
                visibleState.currentState,
                visibleState.targetState,
                popupStack.lastIndex
            ) {
                if (!visibleState.currentState && !visibleState.targetState && index == popupStack.lastIndex) {
                    // 最上位ポップアップの退場完了時のみ閉鎖を通知する。
                    onClose()
                }
            }

            PopupCard(
                info = info,
                index = index,
                isTop = isTop,
                visibleState = visibleState,
                onCloseTop = closeTopPopup,
                onSizeChanged = { size ->
                    if (size != info.size) {
                        onPopupSizeChange(index, size)
                    }
                }
            ) {
                PopupPostList(
                    info = info,
                    posts = posts,
                    replySourceMap = replySourceMap,
                    idCountMap = idCountMap,
                    idIndexList = idIndexList,
                    ngPostNumbers = ngPostNumbers,
                    myPostNumbers = myPostNumbers,
                    headerTextScale = headerTextScale,
                    bodyTextScale = bodyTextScale,
                    lineHeight = lineHeight,
                    searchQuery = searchQuery,
                    onUrlClick = onUrlClick,
                    onThreadUrlClick = onThreadUrlClick,
                    onImageClick = onImageClick,
                    onImageLongPress = onImageLongPress,
                    onRequestMenu = onRequestMenu,
                    onShowTextMenu = onShowTextMenu,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onContentClick = if (info.indentLevels.isNotEmpty()) {
                        null
                    } else {
                        { postNum ->
                            onRequestTreePopup(
                                postNum,
                                calculatePopupOffset(popupStack[index])
                            )
                        }
                    },
                    onReplyFromClick = { numbs ->
                        onAddPopupForReplyFrom(
                            numbs,
                            calculatePopupOffset(popupStack[index]),
                        )
                    },
                    onReplyClick = { num ->
                        onAddPopupForReplyNumber(
                            num,
                            calculatePopupOffset(popupStack[index]),
                        )
                    },
                    onIdClick = { id ->
                        onAddPopupForId(
                            id,
                            calculatePopupOffset(popupStack[index]),
                        )
                    },
                )
            }
        }
    }

}

/**
 * ポップアップ表示数に合わせてアニメーション状態リストを同期する。
 *
 * 表示開始時は非表示状態からアニメーションさせる。
 */
@Composable
private fun rememberPopupVisibilityStates(
    popupStackSize: Int,
): SnapshotStateList<MutableTransitionState<Boolean>> {
    val visibilityStates = remember { mutableStateListOf<MutableTransitionState<Boolean>>() }

    LaunchedEffect(popupStackSize) {
        syncVisibilityStates(visibilityStates, popupStackSize)
    }

    return visibilityStates
}

/**
 * 返信ポップアップの背景タップを監視し、外側タップで最上位を閉じる。
 */
@Composable
private fun PopupBackgroundOverlay(
    popupStack: List<PopupInfo>,
    onCloseTop: () -> Unit,
) {
    if (popupStack.isEmpty()) {
        // 表示対象がない場合は背景タップ判定を行わない。
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(popupStack.size) {
                detectTapGestures { offset ->
                    val topInfo = popupStack.lastOrNull() ?: return@detectTapGestures
                    if (isTapInsidePopup(offset, topInfo)) {
                        return@detectTapGestures
                    }
                    onCloseTop()
                }
            }
    )
}

/**
 * ポップアップのカード表示と最上位以外の入力無効化をまとめて描画する。
 */
@Composable
private fun PopupCard(
    info: PopupInfo,
    index: Int,
    isTop: Boolean,
    visibleState: MutableTransitionState<Boolean>,
    onCloseTop: () -> Unit,
    onSizeChanged: (IntSize) -> Unit,
    content: @Composable () -> Unit,
) {
    // --- アニメーション ---
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(
            animationSpec = tween(durationMillis = POPUP_ANIMATION_DURATION)
        ) + scaleIn(
            animationSpec = tween(durationMillis = POPUP_ANIMATION_DURATION)
        ),
        exit = fadeOut(
            animationSpec = tween(durationMillis = POPUP_ANIMATION_DURATION)
        ) + scaleOut(
            animationSpec = tween(durationMillis = POPUP_ANIMATION_DURATION)
        )
    ) {
        // --- カード描画 ---
        Card(
            modifier = Modifier
                .offset { calculatePopupOffset(info) }
                .zIndex(index.toFloat())
                .onGloballyPositioned { coords ->
                    val size = coords.size
                    if (size != info.size) {
                        onSizeChanged(size)
                    }
                }
                .border(width = 2.dp, color = MaterialTheme.colorScheme.primary)
                .disableInteractionOnUnderlay(isTop, onCloseTop)
        ) {
            content()
        }
    }
}

/**
 * 返信ポップアップ内の投稿一覧を描画し、各アクションを上位へ伝播する。
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PopupPostList(
    info: PopupInfo,
    posts: List<ThreadPostUiModel>,
    replySourceMap: Map<Int, List<Int>>,
    idCountMap: Map<String, Int>,
    idIndexList: List<Int>,
    ngPostNumbers: Set<Int>,
    myPostNumbers: Set<Int>,
    headerTextScale: Float,
    bodyTextScale: Float,
    lineHeight: Float,
    searchQuery: String,
    onUrlClick: (String) -> Unit,
    onThreadUrlClick: (AppRoute.Thread) -> Unit,
    onImageClick: (String) -> Unit,
    onImageLongPress: (String, List<String>) -> Unit,
    onRequestMenu: (PostDialogTarget) -> Unit,
    onShowTextMenu: (String, NgType) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onContentClick: ((Int) -> Unit)?,
    onReplyFromClick: (List<Int>) -> Unit,
    onReplyClick: (Int) -> Unit,
    onIdClick: (String) -> Unit,
) {
    // --- レイアウト ---
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.75f
    Column(
        modifier = Modifier
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
    ) {
        // --- 投稿描画 ---
        info.posts.forEachIndexed { i, p ->
            val postIndex = posts.indexOf(p)
            val postNum = postIndex + 1
            val indentLevel = info.indentLevels.getOrElse(i) { 0 }
            val nextIndent = info.indentLevels.getOrElse(i + 1) { 0 }
            PostItem(
                post = p,
                postNum = postNum,
                idIndex = idIndexList[postIndex],
                idTotal = if (p.header.id.isBlank()) 1 else idCountMap[p.header.id] ?: 1,
                headerTextScale = headerTextScale,
                bodyTextScale = bodyTextScale,
                lineHeight = lineHeight,
                indentLevel = indentLevel,
                searchQuery = searchQuery,
                onUrlClick = onUrlClick,
                onThreadUrlClick = onThreadUrlClick,
                onImageClick = onImageClick,
                onImageLongPress = onImageLongPress,
                onRequestMenu = onRequestMenu,
                onShowTextMenu = onShowTextMenu,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                isMyPost = postNum in myPostNumbers,
                replyFromNumbers = replySourceMap[postNum]?.filterNot { it in ngPostNumbers }
                    ?: emptyList(),
                onReplyFromClick = onReplyFromClick,
                onReplyClick = onReplyClick,
                onIdClick = onIdClick,
                onContentClick = { onContentClick?.invoke(postNum) },
            )
            if (i < info.posts.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp * min(indentLevel, nextIndent))
                )
            }
        }
    }
}

/**
 * ポップアップの描画位置を計算する。
 *
 * 上端が画面外にならないように座標を補正する。
 */
private fun calculatePopupOffset(info: PopupInfo): IntOffset {
    return IntOffset(
        info.offset.x,
        (info.offset.y - info.size.height).coerceAtLeast(0)
    )
}

/**
 * 表示状態リストをポップアップ数に合わせて増減させる。
 */
private fun syncVisibilityStates(
    visibilityStates: SnapshotStateList<MutableTransitionState<Boolean>>,
    targetSize: Int,
) {
    while (visibilityStates.size < targetSize) {
        visibilityStates.add(MutableTransitionState(false).apply { targetState = true })
    }
    while (visibilityStates.size > targetSize) {
        visibilityStates.removeAt(visibilityStates.lastIndex)
    }
}

/**
 * 背景タップが最上位ポップアップ内かどうかを判定する。
 */
private fun isTapInsidePopup(
    tapOffset: Offset,
    topInfo: PopupInfo,
): Boolean {
    val size = topInfo.size
    if (size == IntSize.Zero) {
        // サイズ確定前は外側判定を行わない。
        return true
    }
    val topOffset = calculatePopupOffset(topInfo)
    val insideX = tapOffset.x >= topOffset.x && tapOffset.x < topOffset.x + size.width
    val insideY = tapOffset.y >= topOffset.y && tapOffset.y < topOffset.y + size.height
    return insideX && insideY
}

/**
 * 最上位以外のポップアップ入力を消費し、最上位のクローズ動作に委譲する。
 */
private fun Modifier.disableInteractionOnUnderlay(
    isTop: Boolean,
    onCloseTop: () -> Unit,
): Modifier {
    if (isTop) {
        // 最上位のポップアップは通常通り操作を許可する。
        return this
    }
    // 上位のポップアップ以外は操作を無効化する。
    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val downEvent = awaitPointerEvent(PointerEventPass.Initial)
                downEvent.changes.forEach { it.consume() }
                val down = downEvent.changes.firstOrNull { it.pressed } ?: continue
                var released = false
                while (!released) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change != null && !change.pressed) {
                        released = true
                    }
                }
                // 最上位以外のタップは最上位を閉じる。
                onCloseTop()
            }
        }
    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@SuppressLint("UnrememberedMutableState")
@Preview(showBackground = true)
@Composable
fun ReplyPopupPreview() {
    val dummyPosts = listOf(
        ThreadPostUiModel(
            header = ThreadPostUiModel.Header(
                name = "名無しさん",
                email = "sage",
                date = "2025/08/23",
                id = "ID:12345",
            ),
            body = ThreadPostUiModel.Body(
                content = "これはテスト投稿です。",
            ),
        ),
        ThreadPostUiModel(
            header = ThreadPostUiModel.Header(
                name = "テストユーザー",
                email = "",
                date = "2025/08/23",
                id = "ID:67890",
            ),
            body = ThreadPostUiModel.Body(
                content = "2つ目の投稿。",
            ),
        ),
    )
    val dummyReplySourceMap = mapOf(1 to listOf(2), 2 to listOf(1))
    val dummyIdCountMap = mapOf("ID:12345" to 1, "ID:67890" to 1)
    val dummyIdIndexList = listOf(0, 1)
    val dummyNgPostNumbers = setOf<Int>()
    val popupStack = mutableStateListOf(
        PopupInfo(
            posts = dummyPosts,
            offset = IntOffset(100, 400)
        )
    )
    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            ReplyPopup(
                popupStack = popupStack,
                posts = dummyPosts,
                replySourceMap = dummyReplySourceMap,
                idCountMap = dummyIdCountMap,
                idIndexList = dummyIdIndexList,
                ngPostNumbers = dummyNgPostNumbers,
                myPostNumbers = emptySet(),
                headerTextScale = 0.85f,
                bodyTextScale = 1f,
                lineHeight = DEFAULT_THREAD_LINE_HEIGHT,
                onUrlClick = {},
                onThreadUrlClick = {},
                onImageClick = {},
                onImageLongPress = { _, _ -> },
                onRequestMenu = {},
                onShowTextMenu = { _, _ -> },
                onRequestTreePopup = { _, _ -> },
                onAddPopupForReplyFrom = { _, _ -> },
                onAddPopupForReplyNumber = { _, _ -> },
                onAddPopupForId = { _, _ -> },
                onPopupSizeChange = { _, _ -> },
                onClose = {},
                searchQuery = "",
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this
            )
        }
    }
}
