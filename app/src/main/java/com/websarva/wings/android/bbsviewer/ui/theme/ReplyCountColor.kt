package com.websarva.wings.android.bbsviewer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class ReplyCountColor(val light: Color, val dark: Color) {
    LEVEL1(md_theme_light_blue, md_theme_dark_blue),
    LEVEL2(md_theme_light_purple, md_theme_dark_purple),
    LEVEL3(md_theme_light_pink, md_theme_dark_pink),
    LEVEL4(md_theme_light_red, md_theme_dark_red)
}

@Composable
fun replyCountColor(count: Int): Color {
    val color = when (count) {
        in 1..2 -> ReplyCountColor.LEVEL1
        in 3..4 -> ReplyCountColor.LEVEL2
        in 5..6 -> ReplyCountColor.LEVEL3
        in 7..Int.MAX_VALUE -> ReplyCountColor.LEVEL4
        else -> return Color.Unspecified
    }
    return if (LocalIsDarkTheme.current) color.dark else color.light
}
