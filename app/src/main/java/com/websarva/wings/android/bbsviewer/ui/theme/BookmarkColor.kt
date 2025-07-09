package com.websarva.wings.android.bbsviewer.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class BookmarkColor(val value: String, val light: Color, val dark: Color) {
    RED("red", md_theme_light_red, md_theme_dark_red),
    PINK("pink", md_theme_light_pink, md_theme_dark_pink),
    PURPLE("purple", md_theme_light_purple, md_theme_dark_purple),
    INDIGO("indigo", md_theme_light_indigo, md_theme_dark_indigo),
    BLUE("blue", md_theme_light_blue, md_theme_dark_blue),
    TEAL("teal", md_theme_light_teal, md_theme_dark_teal),
    GREEN("green", md_theme_light_green, md_theme_dark_green),
    YELLOW("yellow", md_theme_light_yellow, md_theme_dark_yellow),
    AMBER("amber", md_theme_light_amber, md_theme_dark_amber),
    BROWN("brown", md_theme_light_brown, md_theme_dark_brown);

    companion object {
        fun fromValue(value: String): BookmarkColor? =
            values().firstOrNull { it.value.equals(value, ignoreCase = true) }
    }
}

@Composable
fun bookmarkColor(name: String): Color {
    val color = BookmarkColor.fromValue(name) ?: BookmarkColor.RED
    return if (LocalIsDarkTheme.current) color.dark else color.light
}
