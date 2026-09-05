package com.websarva.wings.android.slevo.ui.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollSource.Companion.UserInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * スクロール方向に合わせてボトムバーの表示/非表示を制御する。
 *
 * スクロール無効化が指定された場合はボトムバーの挙動を固定する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberBottomBarShowOnBottomBehavior(
    scrollEnabled: Boolean = true,
): BottomAppBarScrollBehavior {
    // --- State ---
    val barState = rememberBottomAppBarState()
    // --- Animation ---
    val flingSpec = rememberSplineBasedDecay<Float>()
    val snapSpec = remember { spring<Float>(stiffness = Spring.StiffnessMediumLow) }

    // --- Scroll behavior ---
    return BottomAppBarDefaults.exitAlwaysScrollBehavior(
        state = barState,
        canScroll = { scrollEnabled },
        snapAnimationSpec = snapSpec,
        flingAnimationSpec = flingSpec
    )
}

/**
 * ボトムバーのアクション行表示とスクロール連動をまとめて扱う。
 *
 * `progress` は 0f〜1f の範囲で縮退率を表す。
 */
data class BottomBarActionVisibility(
    val progress: MutableState<Float>,
    val nestedScrollConnection: NestedScrollConnection,
)

/**
 * ボトムバーのアクション行表示をスクロール方向に合わせて制御する。
 *
 * 下方向スクロールで縮退し、上方向で展開する。
 */
@Composable
fun rememberBottomBarActionVisibility(
    scrollEnabled: Boolean = true,
    actionRowHeight: Dp = 48.dp,
): BottomBarActionVisibility {
    // --- State ---
    val progress = remember { mutableStateOf(1f) }
    return rememberBottomBarActionVisibility(
        progress = progress,
        scrollEnabled = scrollEnabled,
        actionRowHeight = actionRowHeight,
    )
}

/**
 * 外部で保持している縮退率に対して、本文スクロール用の接続を構成する。
 *
 * タブごとの状態を親Composableで保持する場合に使用し、タブがPagerの構成対象から外れても
 * 直前の縮退率を再利用できるようにする。
 */
@Composable
fun rememberBottomBarActionVisibility(
    progress: MutableState<Float>,
    scrollEnabled: Boolean = true,
    actionRowHeight: Dp = 48.dp,
): BottomBarActionVisibility {
    val density = LocalDensity.current
    val actionRowHeightPx = with(density) { actionRowHeight.toPx().coerceAtLeast(1f) }

    // --- Scroll connection ---
    val nestedScrollConnection = remember(progress, scrollEnabled, actionRowHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!scrollEnabled || source != UserInput) {
                    // Guard: ユーザー入力以外は縮退率を変えない。
                    return Offset.Zero
                }
                val delta = available.y / actionRowHeightPx
                val next = (progress.value + delta).coerceIn(0f, 1f)
                progress.value = next
                return Offset.Zero
            }

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity {
                if (!scrollEnabled) {
                    return Velocity.Zero
                }
                val target = if (progress.value >= 0.5f) 1f else 0f
                animate(
                    initialValue = progress.value,
                    targetValue = target,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                ) { value, _ ->
                    progress.value = value
                }
                return Velocity.Zero
            }
        }
    }

    // --- Result ---
    return BottomBarActionVisibility(
        progress = progress,
        nestedScrollConnection = nestedScrollConnection,
    )
}
