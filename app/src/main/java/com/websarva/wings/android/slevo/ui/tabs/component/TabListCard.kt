package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import sh.calvin.reorderable.DragGestureDetector
import java.net.URI
import kotlin.math.abs

/**
 * タブ一覧カードのヘッダー右側に表示する内容を表す型。
 *
 * 板タブでは [None]、スレッドタブでは [ThreadResCount] を渡す。
 * 描画は [TabListCard] 内で一括して行い、呼び出し側の差異を吸収する。
 */
sealed class TabHeaderTrailingContent {
    data object None : TabHeaderTrailingContent()

    /**
     * @param resCount レス総数
     * @param newResCount 新着レス数。0 以下の場合は新着バッジを表示しない。
     */
    data class ThreadResCount(
        val resCount: Int,
        val newResCount: Int,
    ) : TabHeaderTrailingContent()
}

/**
 * スワイプと縦スクロールの競合を解決するための方向判定モード。
 *
 * [Undecided] は未確定、[HorizontalSwipe] は横スワイプ確定、[VerticalScroll] は縦スクロール確定。
 */
private enum class DragMode {
    Undecided,
    HorizontalSwipe,
    VerticalScroll,
}

private const val DRAGGING_CARD_ALPHA = 0.80f

/** タブカード内部で共有するレイアウト寸法を提供する。 */
private object TabListCardDefaults {
    val trailingActionSize = 24.dp
    val trailingActionIconSize = 16.dp

    val trailingActionTopPadding = 2.dp
    val trailingActionEndPadding = 8.dp
    val trailingActionContentSpacing = 8.dp

    val headerMinHeight: Dp
        get() = trailingActionSize

    val headerEndPadding: Dp
        get() = trailingActionEndPadding +
                trailingActionSize +
                trailingActionContentSpacing
}

/**
 * 指の移動量に対して、端に近づくほど移動量を圧縮するラバーバンド補正を返す。
 *
 * 右方向スワイプや削除不可タブのスワイプで急停止を避け、抵抗感のある追従を作る。
 */
private fun applyRubberBandOffset(rawOffset: Float, limit: Float): Float {
    if (limit <= 0f) return 0f
    val clampedRaw = rawOffset.coerceIn(-limit * 8f, limit * 8f)
    val sign = if (clampedRaw >= 0f) 1f else -1f
    val distance = abs(clampedRaw)
    val resisted = limit * (1f - (1f / (distance / limit + 1f)))
    return sign * resisted
}

/**
 * タブ一覧カードの共通外枠と情報配置を提供する。
 *
 * 上部はタイトル・追加情報スロット・閉じるボタン、下部は主要タイトルを表示する。
 * 本文タイトルの行数上限は呼び出し側が指定する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TabListCard(
    modifier: Modifier = Modifier,
    bookmarkColor: Color?,
    onClick: () -> Unit,
    onLongPress: (IntRect) -> Unit = {},
    onLongPressMoved: (Offset) -> Unit = {},
    onLongPressReleased: () -> Unit = {},
    isHiddenForSelection: Boolean = false,
    isPinned: Boolean = false,
    isRemoving: Boolean = false,
    headerTitle: String,
    headerTrailingContent: TabHeaderTrailingContent = TabHeaderTrailingContent.None,
    bodyTitle: String,
    bodyMaxLines: Int = 2,
    onCloseClick: () -> Unit,
    onSwipeDelete: (() -> Unit)? = null,
    isSwipeDeleteEnabled: Boolean = false,
    reorderHandle: ((DragGestureDetector) -> Modifier)? = null,
    onReorderFinished: () -> Unit = {},
    onReorderCancelled: () -> Unit = {},
    isDragging: Boolean = false,
    onMoveUp: (() -> Boolean)? = null,
    onMoveDown: (() -> Boolean)? = null,
) {
    // --- Selection animation ---
    // 長押し選択中とドラッグ中は拡大状態を維持し、操作終了時に元サイズへ縮小復帰する。
    val isScaled = isHiddenForSelection || isDragging
    val selectionScale by animateFloatAsState(
        targetValue = if (isScaled) 1.04f else 1f,
        animationSpec = tween(durationMillis = if (isScaled) 220 else 180),
        label = "tabSelectionScale",
    )
    val draggingAlpha by animateFloatAsState(
        targetValue = if (isDragging) DRAGGING_CARD_ALPHA else 1f,
        animationSpec = tween(durationMillis = TabListAnimationDefaults.DRAGGING_ALPHA_MILLIS),
        label = "tabDraggingAlpha",
    )
    val cardInteractionSource = remember { MutableInteractionSource() }

    // --- Swipe-to-delete state ---
    val canHandleSwipeGesture =
        isSwipeDeleteEnabled && !isDragging && !isRemoving && onSwipeDelete != null
    val canDeleteBySwipe = canHandleSwipeGesture && !isPinned
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var cardBounds by remember { mutableStateOf(IntRect.Zero) }
    var isFlyingOut by remember { mutableStateOf(false) }
    val velocityTracker = remember { VelocityTracker() }
    val density = LocalDensity.current
    val dragHandoffOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var dragHandoffAnimationJob by remember { mutableStateOf<Job?>(null) }

    /**
     * 抵抗付きPreviewの残差を設定し、カードの描画位置だけを指へ補間する。
     */
    fun animateDragHandoff(handoffOffset: Offset) {
        dragHandoffAnimationJob?.cancel()
        dragHandoffAnimationJob = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            dragHandoffOffset.snapTo(handoffOffset)
            dragHandoffOffset.animateTo(
                targetValue = Offset.Zero,
                animationSpec = tween(
                    durationMillis = TabListAnimationDefaults.DRAG_HANDOFF_MILLIS,
                    easing = LinearOutSlowInEasing,
                ),
            )
        }
    }

    /**
     * drag終了時の描画handoffを停止し、Calvinのsettle animationと重ならないよう0へ戻す。
     */
    fun resetDragHandoff() {
        dragHandoffAnimationJob?.cancel()
        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            dragHandoffOffset.snapTo(Offset.Zero)
        }
    }

    // 距離による削除しきい値はカード幅の55%。
    val swipeThreshold = remember(cardWidthPx) {
        if (cardWidthPx > 0f) cardWidthPx * 0.55f else with(density) { 100.dp.toPx() }
    }
    // 速度判定のしきい値は 800dp/s、最小移動距離は 24dp。
    val velocityThreshold = with(density) { 800.dp.toPx() }
    val minVelocityDistance = with(density) { 24.dp.toPx() }
    val springBackSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val swipeResistanceLimitPx = with(density) { 56.dp.toPx() }

    // Keep this pointer node attached while the card is pressed. State changes are read through
    // rememberUpdatedState so long-press recomposition cannot cancel the nested reorder handler.
    val currentCanHandleSwipeGesture = rememberUpdatedState(canHandleSwipeGesture && !isFlyingOut)
    val currentCanDeleteBySwipe = rememberUpdatedState(canDeleteBySwipe)
    val currentSwipeThreshold = rememberUpdatedState(swipeThreshold)
    val currentCardWidthPx = rememberUpdatedState(cardWidthPx)
    val currentOnSwipeDelete = rememberUpdatedState(onSwipeDelete)
    val swipeGestureModifier = Modifier.pointerInput(Unit) {
        // 横スワイプと縦スクロールを競合させないため、固定された外側Boxで方向判定を行う。
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentCanHandleSwipeGesture.value) return@awaitEachGesture

            var dragMode = DragMode.Undecided
            val touchSlop = viewConfiguration.touchSlop
            var totalDx = 0f
            var totalDy = 0f
            var trackedPosition = Offset.Zero
            var latestOffset = offsetX.value
            var offsetUpdateJob: Job? = null

            velocityTracker.resetTracking()

            fun restoreOffset() {
                latestOffset = 0f
                offsetUpdateJob?.cancel()
                coroutineScope.launch {
                    offsetX.animateTo(0f, animationSpec = springBackSpec)
                }
            }

            // --- Direction disambiguation loop ---
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.find { it.id == down.id }
                    ?: return@awaitEachGesture
                if (!currentCanHandleSwipeGesture.value || change.isConsumed) {
                    // Reorderableが所有したsequence、または無効化されたsequenceから撤退する。
                    restoreOffset()
                    return@awaitEachGesture
                }
                if (!change.pressed) break

                val delta = change.positionChange()
                totalDx += delta.x
                totalDy += delta.y

                if (dragMode == DragMode.Undecided) {
                    if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                        if (abs(totalDx) > abs(totalDy)) {
                            dragMode = DragMode.HorizontalSwipe
                            velocityTracker.addPosition(change.uptimeMillis, trackedPosition)
                        } else {
                            dragMode = DragMode.VerticalScroll
                            if (offsetX.value != 0f) restoreOffset()
                            return@awaitEachGesture
                        }
                    }
                }

                if (dragMode == DragMode.HorizontalSwipe) {
                    change.consume()
                    val newOffset = when {
                        // 削除可能時の左方向は従来どおり削除判定に使える移動量を保持する。
                        currentCanDeleteBySwipe.value && totalDx <= 0f ->
                            totalDx.coerceIn(-currentCardWidthPx.value, 0f)
                        // 右方向または削除不可タブでは抵抗感をつけて追従させる。
                        else -> applyRubberBandOffset(totalDx, swipeResistanceLimitPx)
                    }
                    latestOffset = newOffset
                    trackedPosition += Offset(delta.x, 0f)
                    velocityTracker.addPosition(change.uptimeMillis, trackedPosition)
                    offsetUpdateJob?.cancel()
                    offsetUpdateJob = coroutineScope.launch {
                        offsetX.snapTo(newOffset)
                    }
                }
            }

            // --- On finger release ---
            if (dragMode == DragMode.HorizontalSwipe) {
                val velocity = velocityTracker.calculateVelocity()
                val distanceMet = latestOffset < -currentSwipeThreshold.value
                val velocityMet =
                    velocity.x < -velocityThreshold && -latestOffset > minVelocityDistance

                if (currentCanDeleteBySwipe.value && (distanceMet || velocityMet)) {
                    isFlyingOut = true
                    coroutineScope.launch {
                        offsetUpdateJob?.cancelAndJoin()
                        offsetX.animateTo(
                            targetValue = -currentCardWidthPx.value * 1.2f,
                            animationSpec = tween(durationMillis = 140)
                        )
                        currentOnSwipeDelete.value?.invoke()
                    }
                } else {
                    restoreOffset()
                }
            } else if (latestOffset != 0f) {
                restoreOffset()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // 拡大とhandoffを外側へ置き、alpha layerによる境界clipを避ける。
                translationX = dragHandoffOffset.value.x
                translationY = dragHandoffOffset.value.y
                scaleX = selectionScale
                scaleY = selectionScale
                transformOrigin = TransformOrigin.Center
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .onGloballyPositioned { coordinates ->
                    cardWidthPx = coordinates.size.width.toFloat()
                    val bounds = coordinates.boundsInWindow()
                    cardBounds = IntRect(
                        bounds.left.toInt(),
                        bounds.top.toInt(),
                        bounds.right.toInt(),
                        bounds.bottom.toInt(),
                    )
                }
                .graphicsLayer {
                    // Previewでは元カードを隠し、reorder中は対象カードだけを半透明にする。
                    alpha = if (isHiddenForSelection) 0f else draggingAlpha
                },
            shape = MaterialTheme.shapes.largeIncreased,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 1.dp,
            ),
        ) {
            // ContentAreaの操作sourceを共有し、close/pinを含むカード全体へ押下表示を描画する。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .indication(
                        interactionSource = cardInteractionSource,
                        indication = LocalIndication.current,
                    ),
            ) {
                val hapticFeedback = LocalHapticFeedback.current
                val detector = reorderHandle?.let {
                    SlevoTabDragGestureDetector(
                        onLongPress = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress(cardBounds)
                        },
                        onLongPressMoved = onLongPressMoved,
                        onDragThresholdActivated = { handoffOffset ->
                            hapticFeedback.performHapticFeedback(
                                HapticFeedbackType.GestureThresholdActivate,
                            )
                            animateDragHandoff(handoffOffset)
                        },
                        onLongPressReleased = onLongPressReleased,
                        onDragFinished = {
                            resetDragHandoff()
                            onReorderFinished()
                        },
                        onDragCancelled = {
                            resetDragHandoff()
                            onReorderCancelled()
                        },
                    )
                }
                val moveUpLabel = stringResource(R.string.tab_move_up)
                val moveDownLabel = stringResource(R.string.tab_move_down)
                val accessibilityModifier = if (onMoveUp != null || onMoveDown != null) {
                    Modifier.semantics {
                        customActions = buildList {
                            onMoveUp?.let { add(CustomAccessibilityAction(moveUpLabel, it)) }
                            onMoveDown?.let { add(CustomAccessibilityAction(moveDownLabel, it)) }
                        }
                    }
                } else {
                    Modifier
                }
                val gestureModifier = if (detector != null) {
                    Modifier
                        .clickable(
                            enabled = !isRemoving && !isFlyingOut && offsetX.value == 0f,
                            interactionSource = cardInteractionSource,
                            indication = null,
                            onClick = onClick,
                        )
                        .then(reorderHandle(detector))
                        .then(accessibilityModifier)
                } else {
                    Modifier
                        .combinedClickable(
                            enabled = !isRemoving && !isFlyingOut && offsetX.value == 0f,
                            interactionSource = cardInteractionSource,
                            indication = null,
                            onClick = onClick,
                            onLongClick = { onLongPress(cardBounds) },
                        )
                        .then(accessibilityModifier)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(swipeGestureModifier)
                ) {
                    Row(
                        modifier = gestureModifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                    ) {
                        // --- Card body ---
                        Column(
                            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            // --- Header ---
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = TabListCardDefaults.headerMinHeight)
                                    .padding(
                                        start = 8.dp,
                                        end = TabListCardDefaults.headerEndPadding,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (bookmarkColor != null) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.StarBorder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                            Icon(
                                                imageVector = Icons.Filled.Star,
                                                contentDescription = null,
                                                tint = bookmarkColor,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    } else {
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = headerTitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    when (headerTrailingContent) {
                                        is TabHeaderTrailingContent.ThreadResCount -> {
                                            Text(
                                                text = headerTrailingContent.resCount.toString(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            if (headerTrailingContent.newResCount > 0) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "+${headerTrailingContent.newResCount}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier
                                                        .background(
                                                            color = MaterialTheme.colorScheme.primary,
                                                            shape = RoundedCornerShape(999.dp),
                                                        )
                                                        .padding(
                                                            horizontal = 6.dp,
                                                            vertical = 2.dp
                                                        ),
                                                )
                                            }
                                        }

                                        TabHeaderTrailingContent.None -> Unit
                                    }
                                }
                            }
                            // --- Body ---
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp),
                                shape = MaterialTheme.shapes.largeIncreased,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                            ) {
                                val bodyStyle = MaterialTheme.typography.bodyMedium
                                val density = LocalDensity.current
                                val verticalPadding = 8.dp
                                val textMinHeight =
                                    with(density) { (bodyStyle.lineHeight * bodyMaxLines).toDp() } +
                                            verticalPadding * 2

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = textMinHeight),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        text = bodyTitle,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = 12.dp,
                                                vertical = verticalPadding
                                            ),
                                        overflow = TextOverflow.Ellipsis,
                                        maxLines = bodyMaxLines,
                                        style = bodyStyle,
                                    )
                                }
                            }
                        }
                    }
                }

                // close/pin は ContentArea と兄弟にし、reorder/swipe の開始領域から除外する。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = TabListCardDefaults.trailingActionTopPadding,
                            end = TabListCardDefaults.trailingActionEndPadding,
                        )
                        .size(TabListCardDefaults.trailingActionSize),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.pinned),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(TabListCardDefaults.trailingActionIconSize),
                        )
                    } else {
                        IconButton(
                            enabled = !isRemoving && !isFlyingOut,
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape,
                                )
                                .size(TabListCardDefaults.trailingActionSize),
                            onClick = {
                                // タブクローズ操作は一覧遷移より優先して処理する。
                                onCloseClick()
                            },
                        ) {
                            Icon(
                                modifier = Modifier.size(TabListCardDefaults.trailingActionIconSize),
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TabListCardPreview() {
    TabListCard(
        modifier = Modifier.padding(12.dp),
        bookmarkColor = null,
        onClick = {},
        headerTitle = "example.com",
        headerTrailingContent = TabHeaderTrailingContent.ThreadResCount(120, 3),
        bodyTitle = "カードのタイトル",
        onCloseClick = {},
    )
}

@Preview(showBackground = true)
@Composable
fun ColoredTabListCardPreview() {
    TabListCard(
        modifier = Modifier.padding(12.dp),
        bookmarkColor = MaterialTheme.colorScheme.primary,
        onClick = {},
        headerTitle = "example.com",
        headerTrailingContent = TabHeaderTrailingContent.ThreadResCount(120, 0),
        bodyTitle = "カードのタイトル",
        onCloseClick = {},
    )
}

/**
 * 板URLからサービス名に相当するホスト名を取り出す。
 */
internal fun extractServiceName(boardUrl: String): String {
    return runCatching { URI(boardUrl).host }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: boardUrl // URL解析に失敗した場合はそのまま表示する。
}
