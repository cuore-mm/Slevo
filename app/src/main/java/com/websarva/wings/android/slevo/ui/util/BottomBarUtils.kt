package com.websarva.wings.android.slevo.ui.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberBottomBarShowOnBottomBehavior(
    listState: LazyListState,
    showThreshold: Dp = 4.dp,     // 到達とみなすギャップ(px換算)
    leaveThreshold: Dp = 48.dp,   // 可視のままでも離脱とみなすギャップ
    leaveItemsThreshold: Int = 1, // 末尾から何アイテム離れたら解除するか
): BottomAppBarScrollBehavior {
    val density = LocalDensity.current
    val showThPx = with(density) { showThreshold.toPx() }
    val leaveThPx = with(density) { leaveThreshold.toPx() }

    val barState = rememberBottomAppBarState()
    var atBottomSticky by remember { mutableStateOf(false) }
    // 保持ロック (ミリ秒)。sticky にした時点から最低この時刻までは解除を無視する
    var stickyLockUntil by remember { mutableLongStateOf(0L) }

    // 末尾アイテムが“可視か”と、そのときのビューポート下端との隙間(px)
    val lastVisibleAndGap by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf Triple(false, Float.POSITIVE_INFINITY, 0)

            val lastVisible = info.visibleItemsInfo.lastOrNull()
            val lastVisibleIndex = lastVisible?.index ?: -1
            val isLastItemVisible = lastVisibleIndex >= total - 1

            val gap = if (lastVisible != null) {
                val viewportEnd = info.viewportEndOffset
                val lastEnd = lastVisible.offset + lastVisible.size
                (viewportEnd - lastEnd).toFloat()
            } else {
                Float.POSITIVE_INFINITY
            }

            // 末尾からどれだけ離れたか（アイテム数）
            val itemsFromEnd = (total - 1 - lastVisibleIndex).coerceAtLeast(0)
            Triple(isLastItemVisible, gap, itemsFromEnd)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { lastVisibleAndGap }
            .distinctUntilChanged()
            .collect { (isLastVisible, gap, itemsFromEnd) ->
                if (isLastVisible && gap <= showThPx) {
                    // 最下部に到達 → 表示 & スティッキー ON
                    if (!atBottomSticky) {
                        atBottomSticky = true
                        // スティッキーにして高さをゼロに固定
                        barState.heightOffset = 0f
                        // 最低保持時間を今から1秒後に設定
                        stickyLockUntil = System.currentTimeMillis() + 1000L
                    }
                } else {
                    // 離脱判定（ヒステリシス）
                    val farFromEndByItems = itemsFromEnd >= leaveItemsThreshold
                    val farFromEndByGap = gap > leaveThPx // 末尾が可視のまま広く空いた
                    if (farFromEndByItems || farFromEndByGap) {
                        // 最低保持時間が経過していなければ解除しない
                        if (System.currentTimeMillis() >= stickyLockUntil) {
                            atBottomSticky = false
                        }
                    }
                }
            }
    }

    val flingSpec = rememberSplineBasedDecay<Float>()
    val snapSpec = remember { spring<Float>(stiffness = Spring.StiffnessMediumLow) }

    return BottomAppBarDefaults.exitAlwaysScrollBehavior(
        state = barState,
        canScroll = { !atBottomSticky },   // スティッキー中は閉じ動作を無効化
        snapAnimationSpec = snapSpec,
        flingAnimationSpec = flingSpec
    )
}
