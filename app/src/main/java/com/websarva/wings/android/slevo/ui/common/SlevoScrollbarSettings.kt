package com.websarva.wings.android.slevo.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import my.nanihadesuka.compose.ScrollbarSettings

/**
 * Slevo 全体で共通利用するスクロールバー設定を返す。
 */
@Composable
fun rememberSlevoScrollbarSettings(
    enabled: Boolean,
    thumbThickness: Dp = 3.dp,
    scrollbarPadding: Dp = 8.dp
): ScrollbarSettings {
    val colorScheme = MaterialTheme.colorScheme
    return remember(enabled, colorScheme) {
        ScrollbarSettings.Default.copy(
            enabled = enabled,
            thumbThickness = thumbThickness,
            scrollbarPadding = scrollbarPadding,
            thumbUnselectedColor = colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            thumbSelectedColor = colorScheme.primary.copy(alpha = 0.85f),
        )
    }
}
