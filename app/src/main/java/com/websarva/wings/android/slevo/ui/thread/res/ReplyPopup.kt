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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.websarva.wings.android.slevo.data.model.DEFAULT_THREAD_LINE_HEIGHT
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.common.SlevoLazyColumnScrollbar
import com.websarva.wings.android.slevo.ui.common.transition.ImageSharedTransitionKeyFactory
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.thread.state.PopupInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import kotlin.math.max
import kotlin.math.min

private const val POPUP_ANIMATION_DURATION = 160
private const val BASE_MAX_HEIGHT_RATIO = 0.75f
private const val STEP_MAX_HEIGHT_RATIO = 0.05f
private const val MIN_MAX_HEIGHT_RATIO = 0.55f
private val POPUP_RIGHT_MARGIN = 4.dp
private val POPUP_LEFT_MARGIN_STEP = 8.dp
private val POPUP_BASE_LEFT_MARGIN = 4.dp
private val POPUP_MAX_LEFT_MARGIN = 28.dp

/**
 * ポップアップ配置の計算結果を保持する。
 */
private data class PopupPlacement(
    val leftMargin: Dp,
    val maxWidth: Dp,
    val offset: IntOffset,
)

/**
 * 返信ポップアップの表示と操作イベントを管理する。
 *
 * 投稿の長押しメニューやダイアログは呼び出し側へ委譲する。
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ReplyPopup(
    modifier: Modifier = Modifier,
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
    onImageClick: (String, List<String>, Int, String) -> Unit,
    onImageLongPress: (String, List<String>) -> Unit,
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType> = emptyMap(),
    onImageLoadError: (String, ImageLoadFailureType) -> Unit = { _, _ -> },
    onImageLoadSuccess: (String) -> Unit = {},
    onImageRetry: (String) -> Unit = {},
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
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenWidthPx = with(density) {
        screenWidthDp.dp.roundToPx()
    }

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
    Box(modifier = modifier) {
        // --- 背景タップ ---
        PopupBackgroundOverlay(
            popupStack = popupStack,
            screenWidthPx = screenWidthPx,
            screenWidthDp = screenWidthDp,
            density = density,
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
                screenWidthPx = screenWidthPx,
                screenWidthDp = screenWidthDp,
                density = density,
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
                    popupIndex = index,
                    screenWidthPx = screenWidthPx,
                    screenWidthDp = screenWidthDp,
                    density = density,
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
                    imageLoadFailureByUrl = imageLoadFailureByUrl,
                    onImageLoadError = onImageLoadError,
                    onImageLoadSuccess = onImageLoadSuccess,
                    onImageRetry = onImageRetry,
                    onRequestMenu = onRequestMenu,
                    onShowTextMenu = onShowTextMenu,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onContentClick = if (info.indentLevels.isNotEmpty()) {
                        null
                    } else {
                        { postNum, baseOffset ->
                            onRequestTreePopup(
                                postNum,
                                baseOffset,
                            )
                        }
                    },
                    onReplyFromClick = { numbs, baseOffset ->
                        onAddPopupForReplyFrom(
                            numbs,
                            baseOffset,
                        )
                    },
                    onReplyClick = { num, baseOffset ->
                        onAddPopupForReplyNumber(
                            num,
                            baseOffset,
                        )
                    },
                    onIdClick = { id, baseOffset ->
                        onAddPopupForId(
                            id,
                            baseOffset,
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
 * 画面復帰時に既存ポップアップがある場合は表示済み状態で復元し、
 * それ以外の新規追加分は非表示状態からアニメーションさせる。
 */
@Composable
private fun rememberPopupVisibilityStates(
    popupStackSize: Int,
): SnapshotStateList<MutableTransitionState<Boolean>> {
    val visibilityStates = remember { mutableStateListOf<MutableTransitionState<Boolean>>() }
    var hasInitializedVisibilityStates by remember { mutableStateOf(false) }

    LaunchedEffect(popupStackSize) {
        syncVisibilityStates(
            visibilityStates = visibilityStates,
            targetSize = popupStackSize,
            skipEnterAnimation = !hasInitializedVisibilityStates && popupStackSize > 0,
        )
        hasInitializedVisibilityStates = true
    }

    return visibilityStates
}

/**
 * 返信ポップアップの背景タップを監視し、外側タップで最上位を閉じる。
 */
@Composable
private fun PopupBackgroundOverlay(
    popupStack: List<PopupInfo>,
    screenWidthPx: Int,
    screenWidthDp: Int,
    density: Density,
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
                    val topIndex = popupStack.lastIndex
                    val topInfo = popupStack.lastOrNull() ?: return@detectTapGestures
                    if (isTapInsidePopup(
                            tapOffset = offset,
                            topInfo = topInfo,
                            topIndex = topIndex,
                            screenWidthPx = screenWidthPx,
                            screenWidthDp = screenWidthDp,
                            density = density,
                        )
                    ) {
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
    screenWidthPx: Int,
    screenWidthDp: Int,
    density: Density,
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
        val shape = MaterialTheme.shapes.small
        val placement = calculatePopupPlacement(
            info = info,
            popupIndex = index,
            screenWidthPx = screenWidthPx,
            screenWidthDp = screenWidthDp,
            density = density,
        )
        Card(
            modifier = Modifier
                .padding(
                    start = placement.leftMargin,
                    end = POPUP_RIGHT_MARGIN,
                    top = 8.dp,
                    bottom = 8.dp
                )
                .widthIn(max = placement.maxWidth)
                .offset { placement.offset }
                .zIndex(index.toFloat())
                .onGloballyPositioned { coords ->
                    val size = coords.size
                    if (size != info.size) {
                        onSizeChanged(size)
                    }
                }
                .border(width = 2.dp, color = MaterialTheme.colorScheme.primary, shape = shape)
                .disableInteractionOnUnderlay(isTop, onCloseTop),
            shape = shape,
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
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
    popupIndex: Int,
    screenWidthPx: Int,
    screenWidthDp: Int,
    density: Density,
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
    onImageClick: (String, List<String>, Int, String) -> Unit,
    onImageLongPress: (String, List<String>) -> Unit,
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType>,
    onImageLoadError: (String, ImageLoadFailureType) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onImageRetry: (String) -> Unit,
    onRequestMenu: (PostDialogTarget) -> Unit,
    onShowTextMenu: (String, NgType) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onContentClick: ((Int, IntOffset) -> Unit)?,
    onReplyFromClick: (List<Int>, IntOffset) -> Unit,
    onReplyClick: (Int, IntOffset) -> Unit,
    onIdClick: (String, IntOffset) -> Unit,
) {
    // --- レイアウト ---
    val maxHeightRatio = calculatePopupMaxHeightRatio(popupIndex)
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * maxHeightRatio
    val listState = rememberLazyListState()
    val showScrollbar by remember(listState) {
        derivedStateOf { listState.canScrollForward || listState.canScrollBackward }
    }
    Box(
        modifier = Modifier.heightIn(max = maxHeight)
    ) {
        SlevoLazyColumnScrollbar(
            state = listState,
            enabled = showScrollbar,
        ) {
            PopupPostLazyColumn(
                info = info,
                popupIndex = popupIndex,
                screenWidthPx = screenWidthPx,
                screenWidthDp = screenWidthDp,
                density = density,
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
                imageLoadFailureByUrl = imageLoadFailureByUrl,
                onImageLoadError = onImageLoadError,
                onImageLoadSuccess = onImageLoadSuccess,
                onImageRetry = onImageRetry,
                onRequestMenu = onRequestMenu,
                onShowTextMenu = onShowTextMenu,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onContentClick = onContentClick,
                onReplyFromClick = onReplyFromClick,
                onReplyClick = onReplyClick,
                onIdClick = onIdClick,
                maxHeight = maxHeight,
                listState = listState,
            )
        }
    }
}

/**
 * 返信ポップアップ内の投稿一覧を LazyColumn で描画する。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PopupPostLazyColumn(
    info: PopupInfo,
    popupIndex: Int,
    screenWidthPx: Int,
    screenWidthDp: Int,
    density: Density,
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
    onImageClick: (String, List<String>, Int, String) -> Unit,
    onImageLongPress: (String, List<String>) -> Unit,
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType>,
    onImageLoadError: (String, ImageLoadFailureType) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onImageRetry: (String) -> Unit,
    onRequestMenu: (PostDialogTarget) -> Unit,
    onShowTextMenu: (String, NgType) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onContentClick: ((Int, IntOffset) -> Unit)?,
    onReplyFromClick: (List<Int>, IntOffset) -> Unit,
    onReplyClick: (Int, IntOffset) -> Unit,
    onIdClick: (String, IntOffset) -> Unit,
    maxHeight: Dp,
    listState: LazyListState,
) {
    LazyColumn(
        modifier = Modifier.heightIn(max = maxHeight),
        state = listState,
    ) {
        itemsIndexed(info.posts) { i, p ->
            val postIndex = posts.indexOf(p)
            val postNum = postIndex + 1
            val indentLevel = info.indentLevels.getOrElse(i) { 0 }
            val nextIndent = info.indentLevels.getOrElse(i + 1) { 0 }
            var postOffset by remember { mutableStateOf(IntOffset.Zero) }
            var isPostOffsetMeasured by remember { mutableStateOf(false) }
            val baseOffset = if (isPostOffsetMeasured) {
                postOffset
            } else {
                // レイアウト未計測時は現在のポップアップ配置結果をフォールバックに使う。
                calculatePopupPlacement(
                    info = info,
                    popupIndex = popupIndex,
                    screenWidthPx = screenWidthPx,
                    screenWidthDp = screenWidthDp,
                    density = density,
                ).offset
            }
            val transitionNamespace = ImageSharedTransitionKeyFactory.popupPostNamespace(
                popupId = info.popupId,
                postNumber = postNum,
            )
            PostItem(
                modifier = Modifier.onGloballyPositioned { coords ->
                    val position = coords.positionInRoot()
                    postOffset = IntOffset(position.x.toInt(), position.y.toInt())
                    isPostOffsetMeasured = true
                },
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
                transitionNamespace = transitionNamespace,
                onImageClick = onImageClick,
                onImageLongPress = onImageLongPress,
                imageLoadFailureByUrl = imageLoadFailureByUrl,
                onImageLoadError = onImageLoadError,
                onImageLoadSuccess = onImageLoadSuccess,
                onImageRetry = onImageRetry,
                onRequestMenu = onRequestMenu,
                onShowTextMenu = onShowTextMenu,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                isMyPost = postNum in myPostNumbers,
                replyFromNumbers = replySourceMap[postNum]?.filterNot { it in ngPostNumbers }
                    ?: emptyList(),
                onReplyFromClick = { numbers -> onReplyFromClick(numbers, baseOffset) },
                onReplyClick = { number -> onReplyClick(number, baseOffset) },
                onIdClick = { id -> onIdClick(id, baseOffset) },
                onContentClick = { onContentClick?.invoke(postNum, baseOffset) },
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
 * ポップアップ配置を余白主導で計算する。
 *
 * 段数別左余白、幅上限、右端見切れ防止、Y補正を同一経路で扱う。
 */
private fun calculatePopupPlacement(
    info: PopupInfo,
    popupIndex: Int,
    screenWidthPx: Int,
    screenWidthDp: Int,
    density: Density,
): PopupPlacement {
    // --- Left margin ---
    val leftMargin = calculatePopupLeftMargin(popupIndex)
    val leftMarginPx = with(density) { leftMargin.roundToPx() }

    // --- Width constraint ---
    val maxWidth = (screenWidthDp.dp - leftMargin - POPUP_RIGHT_MARGIN).coerceAtLeast(1.dp)

    // --- X clamp ---
    val rightMarginPx = with(density) { POPUP_RIGHT_MARGIN.roundToPx() }
    val clampedX = calculatePopupPlacementOffsetX(
        desiredX = info.offset.x,
        popupWidthPx = info.size.width,
        screenWidthPx = screenWidthPx,
        rightMarginPx = rightMarginPx,
        leftMarginPx = leftMarginPx,
    )

    // --- Y correction ---
    val correctedY = (info.offset.y - info.size.height).coerceAtLeast(0)

    return PopupPlacement(
        leftMargin = leftMargin,
        maxWidth = maxWidth,
        offset = IntOffset(clampedX, correctedY),
    )
}

/**
 * ポップアップ配置用の X 座標を計算する。
 *
 * 未計測時は基準座標をそのまま使い、計測後は右余白を保つようクランプする。
 */
internal fun calculatePopupPlacementOffsetX(
    desiredX: Int,
    popupWidthPx: Int,
    screenWidthPx: Int,
    rightMarginPx: Int,
    leftMarginPx: Int,
): Int {
    val desiredOffsetX = desiredX - leftMarginPx
    if (popupWidthPx <= 0) {
        // 幅未計測時は右端制約を適用せず、基準位置を優先する。
        return desiredOffsetX.coerceAtLeast(0)
    }
    val rightEdgeMaxX = max(screenWidthPx - popupWidthPx - rightMarginPx - leftMarginPx, 0)
    return min(desiredOffsetX, rightEdgeMaxX).coerceAtLeast(0)
}

/**
 * ポップアップ段数ごとの左余白を返す。
 *
 * 1段目を基準に 8.dp ずつ増加し、[POPUP_MAX_LEFT_MARGIN] を上限とする。
 */
internal fun calculatePopupLeftMargin(popupIndex: Int): Dp {
    val normalizedIndex = popupIndex.coerceAtLeast(0)
    val margin = POPUP_BASE_LEFT_MARGIN + (POPUP_LEFT_MARGIN_STEP * normalizedIndex)
    return if (margin > POPUP_MAX_LEFT_MARGIN) POPUP_MAX_LEFT_MARGIN else margin
}

/**
 * ポップアップ段数に応じた最大高さ比率を返す。
 *
 * 1段目を基準とし、段数が増えるごとに一定値で縮小する。
 * 縮小後の値は [MIN_MAX_HEIGHT_RATIO] を下回らない。
 */
internal fun calculatePopupMaxHeightRatio(popupIndex: Int): Float {
    // 不正な負値入力は1段目として扱う。
    val depth = popupIndex.coerceAtLeast(0)
    val calculated = BASE_MAX_HEIGHT_RATIO - (depth * STEP_MAX_HEIGHT_RATIO)
    return calculated.coerceAtLeast(MIN_MAX_HEIGHT_RATIO)
}

/**
 * 表示状態リストをポップアップ数に合わせて増減させる。
 */
private fun syncVisibilityStates(
    visibilityStates: SnapshotStateList<MutableTransitionState<Boolean>>,
    targetSize: Int,
    skipEnterAnimation: Boolean,
) {
    // 初回復帰時は既存ポップアップを表示済み状態で復元し、再入場アニメーションを抑止する。
    if (skipEnterAnimation && visibilityStates.isEmpty() && targetSize > 0) {
        repeat(targetSize) {
            visibilityStates.add(MutableTransitionState(true).apply { targetState = true })
        }
        return
    }

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
    topIndex: Int,
    screenWidthPx: Int,
    screenWidthDp: Int,
    density: Density,
): Boolean {
    val size = topInfo.size
    if (size == IntSize.Zero) {
        // サイズ確定前は外側判定を行わない。
        return true
    }
    val topPlacement = calculatePopupPlacement(
        info = topInfo,
        popupIndex = topIndex,
        screenWidthPx = screenWidthPx,
        screenWidthDp = screenWidthDp,
        density = density,
    )
    val topLeftMarginPx = with(density) { topPlacement.leftMargin.roundToPx().toFloat() }
    val popupLeft = topPlacement.offset.x + topLeftMarginPx
    val popupRight = popupLeft + size.width
    val insideX = tapOffset.x >= popupLeft && tapOffset.x < popupRight
    val insideY =
        tapOffset.y >= topPlacement.offset.y && tapOffset.y < topPlacement.offset.y + size.height
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
            popupId = 1L,
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
                onImageClick = { _, _, _, _ -> },
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
