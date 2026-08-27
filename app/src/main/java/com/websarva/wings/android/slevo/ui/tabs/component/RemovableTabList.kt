package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.websarva.wings.android.slevo.ui.common.SlevoLazyColumnScrollbar
import kotlin.math.roundToInt

/**
 * タブ一覧の削除アニメーション付きリスト表示を共通化する。
 *
 * 削除中keyを表示状態として受け取り、データ更新前に対象行を縮小・透明化する。
 *
 * @param userScrollEnabled ユーザーによるスクロールを許可するか。
 *                          長押し選択モード中など、スクロールを一時的に抑制したい場合に `false` を渡す。
 */
@Composable
internal fun <T> RemovableTabList(
    modifier: Modifier = Modifier,
    tabItems: List<T>,
    keyOf: (T) -> String,
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
    verticalSpacing: Dp = TabListLayoutDefaults.listItemSpacing,
    removalDurationMillis: Int = TabListAnimationDefaults.ITEM_REMOVAL_MILLIS,
    removingKeys: Set<String> = emptySet(),
    onRemoveConfirmed: (T) -> Unit,
    userScrollEnabled: Boolean = true,
    reorderEnabled: Boolean = false,
    onReorderStarted: (T) -> Unit = {},
    onReorderMoved: (from: T, to: T) -> Unit = { _, _ -> },
    onReorderFinished: (T) -> Unit = {},
    onReorderCancelled: (T) -> Unit = {},
    itemContent: @Composable (
        item: T,
        isRemoving: Boolean,
        requestRemove: () -> Unit,
        isDragging: Boolean,
        reorderHandle: (DragGestureDetector) -> Modifier,
        onReorderFinished: () -> Unit,
        onReorderCancelled: () -> Unit,
    ) -> Unit,
) {
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        val fromItem = tabItems.getOrNull(from.index)
        val toItem = tabItems.getOrNull(to.index)
        logTabReorder {
            "REORDERABLE_MOVE fromIndex=${from.index} toIndex=${to.index} " +
                "fromFound=${fromItem != null} toFound=${toItem != null}"
        }
        if (fromItem != null && toItem != null) {
            onReorderMoved(fromItem, toItem)
        }
    }
    val reorderPlacementSpec: FiniteAnimationSpec<IntOffset>? = if (
        reorderEnabled && removingKeys.isEmpty()
    ) {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    } else {
        null
    }

    // --- List ---
    Box {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            userScrollEnabled = userScrollEnabled,
        ) {
            itemsIndexed(tabItems, key = { _, item -> keyOf(item) }) { index, item ->
                val itemKey = keyOf(item)
                val isRemoving = itemKey in removingKeys

                val hasVisibleItemAfter = tabItems
                    .drop(index + 1)
                    .any { keyOf(it) !in removingKeys }
                // --- Removal animation ---
                val removalHeightFraction by animateFloatAsState(
                    targetValue = if (isRemoving) 0f else 1f,
                    animationSpec = tween(
                        durationMillis = TabListAnimationDefaults.ITEM_COLLAPSE_MILLIS,
                        delayMillis = TabListAnimationDefaults.ITEM_COLLAPSE_DELAY_MILLIS,
                        easing = FastOutLinearInEasing,
                    ),
                    label = "tabRemovalHeight",
                )
                val removalAlpha by animateFloatAsState(
                    targetValue = if (isRemoving) 0f else 1f,
                    animationSpec = tween(
                        durationMillis = TabListAnimationDefaults.ITEM_FADE_OUT_MILLIS,
                        easing = LinearEasing,
                    ),
                    label = "tabRemovalAlpha",
                )
                val removalModifier = Modifier
                    .graphicsLayer {
                        alpha = removalAlpha
                    }
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        // LazyColumnの主軸constraintは無限になり得るため、実測高を縮小する。
                        val animatedHeight = (placeable.height * removalHeightFraction)
                            .roundToInt()
                            .coerceIn(constraints.minHeight, constraints.maxHeight)
                        layout(placeable.width, animatedHeight) {
                            placeable.placeRelative(
                                x = 0,
                                y = (animatedHeight - placeable.height) / 2,
                            )
                        }
                    }
                ReorderableItem(
                    state = reorderableState,
                    key = itemKey,
                    enabled = reorderEnabled && !isRemoving,
                    animateItemModifier = Modifier.animateItem(
                        fadeInSpec = tween(removalDurationMillis),
                        fadeOutSpec = null,
                        placementSpec = reorderPlacementSpec,
                    ),
                ) { isDragging ->
                    val reorderHandle: (DragGestureDetector) -> Modifier = { detector ->
                        Modifier.draggableHandle(
                            enabled = reorderEnabled && !isRemoving,
                            dragGestureDetector = detector,
                            onDragStarted = {
                                logTabReorder { "REORDERABLE_STARTED key=$itemKey" }
                                onReorderStarted(item)
                            },
                        )
                    }

                    // --- Item content ---
                    Column(modifier = removalModifier) {
                        itemContent(item, isRemoving, {
                            if (!isRemoving) {
                                onRemoveConfirmed(item)
                            }
                        }, isDragging, reorderHandle, {
                            logTabReorder { "REORDERABLE_FINISHED key=$itemKey" }
                            onReorderFinished(item)
                        }, {
                            logTabReorder { "REORDERABLE_CANCELLED key=$itemKey" }
                            onReorderCancelled(item)
                        })
                        if (hasVisibleItemAfter) {
                            Spacer(modifier = Modifier.height(verticalSpacing))
                        }
                    }
                }
            }
        }
        SlevoLazyColumnScrollbar(
            modifier = modifier
                .fillMaxSize()
                .padding(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() - TabListLayoutDefaults.scrollbarBottomInset,
                ),
            state = listState,
            enabled = tabItems.size > 1,
        ) {}
    }
}
