package com.websarva.wings.android.slevo.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings

/**
 * Slevo 全体で共通利用するスクロールバー設定を返す。
 *
 * つまみ選択中は太さを増やし、視覚的にドラッグ状態を判別しやすくする。
 */
@Composable
fun rememberSlevoScrollbarSettings(
    enabled: Boolean = true,
    isThumbSelected: Boolean = false,
): ScrollbarSettings {
    val colorScheme = MaterialTheme.colorScheme
    val thumbThickness by animateDpAsState(
        targetValue = if (isThumbSelected) 6.dp else 3.dp,
        label = "slevoScrollbarThickness",
    )
    return remember(enabled, isThumbSelected, thumbThickness, colorScheme) {
        ScrollbarSettings.Default.copy(
            enabled = enabled,
            thumbThickness = thumbThickness,
            thumbUnselectedColor = colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            thumbSelectedColor = colorScheme.primary.copy(alpha = 0.85f),
        )
    }
}

/**
 * 共通スクロールバー設定と触覚フィードバックを適用した LazyColumn ラッパー。
 */
@Composable
fun SlevoLazyColumnScrollbar(
    state: LazyListState,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // --- 状態管理 ---
    val hapticFeedback = LocalHapticFeedback.current
    var isThumbSelected by remember { mutableStateOf(false) }

    // --- スクロールバー描画 ---
    LazyColumnScrollbar(
        modifier = modifier,
        state = state,
        settings = rememberSlevoScrollbarSettings(
            enabled = enabled,
            isThumbSelected = isThumbSelected,
        ),
        indicatorContent = { _, selected ->
            LaunchedEffect(selected) {
                // つまみを掴んだ瞬間のみ触覚を発火する。
                if (selected && !isThumbSelected) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                isThumbSelected = selected
            }
            Spacer(modifier = Modifier.size(0.dp))
        },
    ) {
        content()
    }
}
