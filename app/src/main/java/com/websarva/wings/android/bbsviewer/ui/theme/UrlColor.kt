package com.websarva.wings.android.bbsviewer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun imageUrlColor(): Color {
    return if (LocalIsDarkTheme.current) md_theme_dark_orange else md_theme_light_orange
}

@Composable
fun threadUrlColor(): Color {
    return if (LocalIsDarkTheme.current) md_theme_dark_green else md_theme_light_green
}

@Composable
fun urlColor(): Color {
    return if (LocalIsDarkTheme.current) md_theme_dark_blue else md_theme_light_blue
}
