package com.websarva.wings.android.bbsviewer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun replyColor(): Color {
    return if (LocalIsDarkTheme.current) md_theme_dark_blue else md_theme_light_blue
}
