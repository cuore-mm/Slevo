package com.websarva.wings.android.slevo.ui.thread.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Thread 画面の下端更新インジケーターを描画する。
 *
 * プル中は `overscroll` 量に応じた回転と拡大を行い、更新中は既存どおり右回転表示を維持する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BoxScope.ThreadBottomRefreshIndicator(
    isRefreshing: Boolean,
    overscroll: Float,
    refreshThresholdPx: Float,
) {
    if (!isRefreshing && overscroll <= 0f) {
        // Guard: 非更新・非プル時は表示しない。
        return
    }

    // --- Progress calculation ---
    val rawProgress = if (refreshThresholdPx > 0f) {
        overscroll / refreshThresholdPx
    } else {
        // Guard: 閾値未設定時は進捗ゼロ扱いにする。
        0f
    }
    val sizeProgress = rawProgress.coerceIn(0f, 1f)

    if (isRefreshing) {
        ContainedLoadingIndicator(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
        return
    }

    // --- Pulling motion ---
    // Guard: 進捗に応じた角度目標を設定し、戻し操作では角度が減って右回転に見える。
    val targetRotation = -MAX_PULL_ROTATION_DEGREES * rawProgress
    val animatedRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = spring(),
        label = "threadPullIndicatorRotation",
    )

    val targetScale = lerp(MIN_PULL_SCALE, MAX_PULL_SCALE, sizeProgress)
    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(),
        label = "threadPullIndicatorScale",
    )

    ContainedLoadingIndicator(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 16.dp)
            .graphicsLayer(
                scaleX = animatedScale,
                scaleY = animatedScale,
                rotationZ = animatedRotation,
                transformOrigin = TransformOrigin.Center,
            ),
        progress = { sizeProgress },
    )
}

/**
 * 0.0〜1.0 の範囲で値を線形補間する。
 */
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

// --- Motion constants ---
private const val MIN_PULL_SCALE = 0.0f
private const val MAX_PULL_SCALE = 1.0f
private const val MAX_PULL_ROTATION_DEGREES = 180f
