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
import androidx.compose.runtime.setValue
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
import com.websarva.wings.android.slevo.ui.common.scroll.resolveBottomTargetIndex
import com.websarva.wings.android.slevo.ui.thread.state.ThreadListItem
import com.websarva.wings.android.slevo.ui.thread.state.ThreadSortType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.min

/**
 * スクロール終端で既読レス番号を通知する副作用を管理する。
 */
@Composable
fun ObserveLastReadEffect(
    listState: LazyListState,
    visiblePostRows: List<ThreadListItem.PostRow>,
    sortType: ThreadSortType,
    totalPostCount: Int,
    onLastRead: (Int) -> Unit,
) {
    // --- Scroll end observation ---
    LaunchedEffect(listState, visiblePostRows, sortType, totalPostCount) {
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
                                    if (idx !in visiblePostRows.indices) {
                                        // Guard: ヘッダー行など投稿インデックス外は除外する。
                                        return@mapNotNull null
                                    }
                                    val display = visiblePostRows[idx].displayPost
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
 * 自動スクロールの進行とユーザー操作との衝突を管理する副作用。
 *
 * - 自動スクロールの駆動は `isAutoScroll` と `LaunchedEffect` ライフサイクルを基準にし、
 *   プログラムスクロール自身の `isScrollInProgress` 変化で Effect が再起動しないようにする。
 * - ユーザー手動 drag は `LazyListState.interactionSource.interactions` で検知し、
 *   drag 中は自動スクロールを停止する。
 * - drag 終了後は fling 完了を待ってから短い猶予を置いて自動スクロールを再開する。
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
    val resumeGraceMillis = 150L

    // ユーザー操作中かどうか。drag 開始/終了/fling 完了/猶予で更新する。
    var isUserInteracting by remember { mutableStateOf(false) }

    // drag 開始/終了を検知し、isUserInteracting を更新する。
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    isUserInteracting = true
                }

                is DragInteraction.Stop,
                is DragInteraction.Cancel -> {
                    isUserInteracting = false
                }
            }
        }
    }

    LaunchedEffect(isAutoScroll, density, fallbackItemCount) {
        if (!isAutoScroll) {
            return@LaunchedEffect
        }
        val pxPerSec = with(density) { autoScrollDpPerSec.dp.toPx() }
        var lastTime: Long? = null
        while (isActive) {
            // ユーザー手動操作中は自動スクロールを一時停止する。
            if (isUserInteracting) {
                lastTime = null
                delay(16L)
                continue
            }
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

            // ユーザー操作由来の fling が落ち着くまで待ってから自動スクロールを継続する。
            if (consumed > 0f && isScrollInProgressWaiting(listState)) {
                delay(resumeGraceMillis)
                lastTime = null
            }

            if (atEnd || consumed == 0f) {
                onAutoScrollBottom()
            }
        }
    }
}

/**
 * 直近でユーザー由来のスクロール/ fling が継続しているかを返す。
 *
 * 自動スクロールの `scrollBy` で `isScrollInProgress` が一瞬 true になる短時間だけだと判定が難しいため、
 * 100ms 程度の短時間サンプルで「継続的なユーザー由来の動き」とみなすか判断する。
 */
private suspend fun isScrollInProgressWaiting(listState: LazyListState): Boolean {
    return withTimeoutOrNull(120L) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { it }
            .first()
    } ?: false
}

/**
 * 下端更新判定の状態と NestedScrollConnection をまとめたハンドル。
 */
data class BottomRefreshHandle(
    val nestedScrollConnection: NestedScrollConnection,
    val overscroll: Float,
    val refreshThresholdPx: Float,
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
    // Guard: 0 を下回る値は「戻し超過」の借金として保持する。
    var sessionPullOffset by remember { mutableFloatStateOf(0f) }
    var overscrollConsumed by remember { mutableStateOf(false) }
    var triggerRefresh by remember { mutableStateOf(false) }
    var bottomRefreshArmed by remember { mutableStateOf(false) }
    var armOnNextDrag by remember { mutableStateOf(false) }
    var waitingForBottomReach by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var pendingRelease by remember { mutableStateOf(false) }

    // --- Nested scroll ---
    val nestedScrollConnection = remember(listState, postCount, refreshThresholdPx) {
        object : NestedScrollConnection {
            // --- Overscroll consumption ---
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (overscrollConsumed && available.y > 0f && source == NestedScrollSource.UserInput) {
                    // Guard: overscroll セッション中は指を離すまで縦スクロールを消費する。
                    sessionPullOffset -= available.y
                    // Guard: 表示/閾値判定は 0 以上のみを対象とする。
                    val displayOverscroll = sessionPullOffset.coerceAtLeast(0f)
                    triggerRefresh = displayOverscroll >= refreshThresholdPx
                    Offset(0f, available.y)
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
                    source == NestedScrollSource.UserInput &&
                    !listState.canScrollForward &&
                    available.y < 0f
                ) {
                    if (bottomRefreshArmed || overscrollConsumed) {
                        sessionPullOffset -= available.y
                        // Guard: 表示/閾値判定は 0 以上のみを対象とする。
                        val displayOverscroll = sessionPullOffset.coerceAtLeast(0f)
                        val reached = displayOverscroll >= refreshThresholdPx
                        // Guard: 未到達 -> 到達 の遷移時のみ触覚を返す。
                        if (bottomRefreshArmed && reached && !triggerRefresh) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                        triggerRefresh = reached
                        // Guard: overscroll 開始後はドラッグ終了まで消費を継続する。
                        overscrollConsumed = true
                        return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (bottomRefreshArmed && triggerRefresh) {
                    onLastRead(postCount)
                    onBottomRefresh()
                }
                sessionPullOffset = 0f
                overscrollConsumed = false
                triggerRefresh = false
                bottomRefreshArmed = false
                pendingRelease = false
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
                    if (interaction is DragInteraction.Cancel) {
                        // Guard: キャンセル時は即時にセッションを破棄する。
                        sessionPullOffset = 0f
                        overscrollConsumed = false
                        triggerRefresh = false
                        bottomRefreshArmed = false
                        pendingRelease = false
                    } else {
                        // Guard: onPostFling で発火判定するため、状態は維持する。
                        pendingRelease = true
                    }
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
                    if (overscrollConsumed || pendingRelease) {
                        // Guard: セッション中や指離し直後はアーム解除を行わない。
                        return@collect
                    }
                    // Guard: 下端を離れたら更新判定と次ドラッグアームを解除する。
                    bottomRefreshArmed = false
                    armOnNextDrag = false
                    waitingForBottomReach = false
                    sessionPullOffset = 0f
                    overscrollConsumed = false
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
        overscroll = sessionPullOffset.coerceAtLeast(0f),
        refreshThresholdPx = refreshThresholdPx,
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
