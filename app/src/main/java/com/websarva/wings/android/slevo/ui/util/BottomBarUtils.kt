package com.websarva.wings.android.slevo.ui.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

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
