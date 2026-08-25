package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.websarva.wings.android.slevo.ui.common.SlevoLazyColumnScrollbar

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
        if (fromItem != null && toItem != null) {
            onReorderMoved(fromItem, toItem)
        }
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

                // --- Removal animation ---
                Column {
                    AnimatedVisibility(
                        visible = !isRemoving,
                        enter = EnterTransition.None,
                        exit = fadeOut(
                            animationSpec = tween(
                                durationMillis = TabListAnimationDefaults.ITEM_FADE_OUT_MILLIS,
                                easing = LinearEasing,
                            ),
                        ) + shrinkVertically(
                            animationSpec = tween(
                                durationMillis = TabListAnimationDefaults.ITEM_COLLAPSE_MILLIS,
                                delayMillis = TabListAnimationDefaults.ITEM_COLLAPSE_DELAY_MILLIS,
                                easing = FastOutLinearInEasing,
                            ),
                            shrinkTowards = Alignment.CenterVertically,
                        ),
                    ) {
                        Box(
                            modifier = Modifier.animateItem(
                                fadeInSpec = tween(removalDurationMillis),
                                fadeOutSpec = null,
                                placementSpec = null,
                            )
                        ) {
                            ReorderableItem(
                                state = reorderableState,
                                key = itemKey,
                                enabled = reorderEnabled && !isRemoving,
                            ) { isDragging ->
                                val reorderHandle: (DragGestureDetector) -> Modifier = { detector ->
                                    Modifier.draggableHandle(
                                        enabled = reorderEnabled && !isRemoving,
                                        dragGestureDetector = detector,
                                        onDragStarted = { onReorderStarted(item) },
                                    )
                                }
                                itemContent(item, isRemoving, {
                                    if (!isRemoving) {
                                        onRemoveConfirmed(item)
                                    }
                                }, isDragging, reorderHandle, {
                                    onReorderFinished(item)
                                }, {
                                    onReorderCancelled(item)
                                })
                            }
                        }
                    }

                    val hasVisibleItemAfter = tabItems
                        .drop(index + 1)
                        .any { keyOf(it) !in removingKeys }
                    AnimatedVisibility(
                        visible = !isRemoving && hasVisibleItemAfter,
                        enter = EnterTransition.None,
                        exit = shrinkVertically(
                            animationSpec = tween(
                                durationMillis = TabListAnimationDefaults.ITEM_COLLAPSE_MILLIS,
                                delayMillis = TabListAnimationDefaults.ITEM_COLLAPSE_DELAY_MILLIS,
                                easing = FastOutLinearInEasing,
                            ),
                            shrinkTowards = Alignment.CenterVertically,
                        ),
                    ) {
                        Spacer(modifier = Modifier.height(verticalSpacing))
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
