package com.websarva.wings.android.slevo.ui.util

import androidx.compose.animation.core.Spring
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
 * アクション行の表示状態と、スクロール方向を検知する接続を保持する。
 */
data class BottomBarActionVisibility(
    val actionsVisible: MutableState<Boolean>,
    val nestedScrollConnection: NestedScrollConnection,
)

/**
 * ボトムバーのアクション行表示をスクロール方向に合わせて制御する。
 *
 * 下方向スクロールで非表示、上方向スクロールで表示に戻す。
 */
@Composable
fun rememberBottomBarActionVisibility(
    scrollEnabled: Boolean = true,
): BottomBarActionVisibility {
    // --- State ---
    val actionsVisible = remember { mutableStateOf(true) }

    // --- Scroll connection ---
    val nestedScrollConnection = remember(scrollEnabled) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!scrollEnabled || source != NestedScrollSource.UserInput) {
                    // Guard: ユーザー入力以外は表示状態を変えない。
                    return Offset.Zero
                }
                if (available.y < 0f) {
                    actionsVisible.value = false
                } else if (available.y > 0f) {
                    actionsVisible.value = true
                }
                return Offset.Zero
            }
        }
    }

    // --- Result ---
    return BottomBarActionVisibility(
        actionsVisible = actionsVisible,
        nestedScrollConnection = nestedScrollConnection,
    )
}
