package com.websarva.wings.android.slevo.ui.thread.screen.effects

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.ui.thread.screen.resolveBottomTargetIndex
import com.websarva.wings.android.slevo.ui.thread.state.DisplayPost
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.min

/**
 * スクロール終端で既読レス番号を通知する副作用を管理する。
 */
@Composable
fun ObserveLastReadEffect(
    listState: LazyListState,
    visiblePosts: List<DisplayPost>,
    sortType: ThreadSortType,
    totalPostCount: Int,
    onLastRead: (Int) -> Unit,
) {
    // --- Scroll end observation ---
    LaunchedEffect(listState, visiblePosts, sortType, totalPostCount) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { scrolling ->
                if (!scrolling) {
                    delay(500)
                    if (!listState.isScrollInProgress) {
                        val layoutInfo = listState.layoutInfo
                        val lastRead = if (!listState.canScrollForward) {
                            totalPostCount
                        } else {
                            val half = layoutInfo.viewportEndOffset / 2
                            layoutInfo.visibleItemsInfo
                                .filter { it.offset < half }
                                .mapNotNull { info ->
                                    val idx = info.index - 1
                                    if (idx !in visiblePosts.indices) {
                                        // Guard: ヘッダー行など投稿インデックス外は除外する。
                                        return@mapNotNull null
                                    }
                                    val display = visiblePosts[idx]
                                    if (sortType != ThreadSortType.TREE || display.depth == 0) {
                                        display.num
                                    } else {
                                        null
                                    }
                                }
                                .maxOrNull()
                        }
                        lastRead?.let(onLastRead)
                    }
                }
            }
    }
}

/**
 * 自動スクロールの進行を管理し、終端到達時に通知する副作用を管理する。
 */
@Composable
fun ObserveAutoScrollEffect(
    listState: LazyListState,
    isAutoScroll: Boolean,
    fallbackItemCount: Int,
    onAutoScrollBottom: () -> Unit,
) {
    val density = LocalDensity.current
    val autoScrollDpPerSec = 40f
    val isScrollInProgress = listState.isScrollInProgress

    LaunchedEffect(isAutoScroll, isScrollInProgress, density, fallbackItemCount) {
        if (!isAutoScroll || isScrollInProgress) {
            return@LaunchedEffect
        }
        val pxPerSec = with(density) { autoScrollDpPerSec.dp.toPx() }
        var lastTime: Long? = null
        while (isActive) {
            val dt = withFrameNanos { now ->
                val previous = lastTime
                lastTime = now
                if (previous == null) 0f else (now - previous) / 1_000_000_000f
            }
            if (dt == 0f) {
                continue
            }

            val consumed = listState.scrollBy(pxPerSec * dt)
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            val targetLastIndex = resolveBottomTargetIndex(
                totalItemsCount = info.totalItemsCount,
                fallbackCount = fallbackItemCount,
            )
            val atEnd = last != null &&
                last.index == targetLastIndex &&
                last.offset + last.size <= info.viewportEndOffset + 1

            if (atEnd || consumed == 0f) {
                onAutoScrollBottom()
            }
        }
    }
}

/**
 * 下端更新判定の状態と NestedScrollConnection をまとめたハンドル。
 */
data class BottomRefreshHandle(
    val nestedScrollConnection: NestedScrollConnection,
    val overscroll: Float,
)

/**
 * 下端プルアップ更新の判定ロジックを管理し、UI で利用するハンドルを返す。
 */
@Composable
fun rememberBottomRefreshHandle(
    listState: LazyListState,
    postCount: Int,
    hapticFeedback: HapticFeedback,
    onBottomRefresh: () -> Unit,
    onLastRead: (Int) -> Unit,
): BottomRefreshHandle {
    // --- State ---
    val density = LocalDensity.current
    val refreshThresholdPx = with(density) { 80.dp.toPx() }
    var overscroll by remember { mutableFloatStateOf(0f) }
    var triggerRefresh by remember { mutableStateOf(false) }
    var bottomRefreshArmed by remember { mutableStateOf(false) }
    var armOnNextDrag by remember { mutableStateOf(false) }
    var waitingForBottomReach by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    // --- Nested scroll ---
    val nestedScrollConnection = remember(listState, postCount, refreshThresholdPx) {
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
                source: NestedScrollSource,
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
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                    triggerRefresh = reached
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (bottomRefreshArmed && triggerRefresh) {
                    onLastRead(postCount)
                    onBottomRefresh()
                }
                overscroll = 0f
                triggerRefresh = false
                bottomRefreshArmed = false
                return Velocity.Zero
            }
        }
    }

    // --- Initial arm ---
    // Guard: 画面初期化時に既に下端にいる場合、次ドラッグで更新判定可能にする。
    LaunchedEffect(Unit) {
        if (!listState.canScrollForward) {
            armOnNextDrag = true
        }
    }

    // --- Drag interaction tracking ---
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
                        // Guard: 慣性で下端到達した後に次ドラッグを更新判定対象にする。
                        waitingForBottomReach = true
                    }
                }
            }
        }
    }

    // --- Bottom reach tracking ---
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
                    // Guard: ドラッグ終了後に慣性で下端到達した場合、次ドラッグで更新判定可能にする。
                    armOnNextDrag = true
                    waitingForBottomReach = false
                } else if (!isDragging) {
                    // Guard: scrollToItem などで下端到達した場合も次ドラッグを更新判定対象にする。
                    armOnNextDrag = true
                }
            }
    }

    // --- Output ---
    return BottomRefreshHandle(
        nestedScrollConnection = nestedScrollConnection,
        overscroll = overscroll,
    )
}

/**
 * ポップアップ表示状態の変化を外部へ通知する。
 */
@Composable
fun ObservePopupVisibilityEffect(
    popupCount: Int,
    onPopupVisibilityChange: (Boolean) -> Unit,
) {
    LaunchedEffect(popupCount) {
        onPopupVisibilityChange(popupCount > 0)
    }
}

/**
 * ジェスチャーヒントの無効表示を一定時間後に非表示へ戻す。
 */
@Composable
fun ObserveGestureHintInvalidReset(
    isInvalid: Boolean,
    onReset: () -> Unit,
) {
    LaunchedEffect(isInvalid) {
        if (isInvalid) {
            delay(1200)
            onReset()
        }
    }
}
