package com.websarva.wings.android.slevo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class IdColor(val light: Color, val dark: Color) {
    LEVEL1(Color.Blue, Color(0xFFADD8E6)), // Light Blue
    LEVEL2(Color(0xFF9C27B0), Color(0xFFCE93D8)), // Light Purple
    LEVEL3(Color(0xFFE91E63), Color(0xFFF48FB1)), // Light Pink
    LEVEL4(Color.Red, Color(0xFFEF9A9A)) // Light Red
}

@Composable
fun idColor(count: Int): Color {
    val color = when {
        count in 2..3 -> IdColor.LEVEL1
        count in 4..5 -> IdColor.LEVEL2
        count in 6..7 -> IdColor.LEVEL3
        count >= 8 -> IdColor.LEVEL4
        else -> return Color.Unspecified
    }
    return if (LocalIsDarkTheme.current) color.dark else color.light
}
