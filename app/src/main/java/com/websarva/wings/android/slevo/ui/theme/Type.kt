package com.websarva.wings.android.slevo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

fun Typography.withDefaultFontFamily(f: FontFamily) = Typography(
    displayLarge  = displayLarge.copy(fontFamily = f),
    displayMedium = displayMedium.copy(fontFamily = f),
    displaySmall  = displaySmall.copy(fontFamily = f),
    headlineLarge  = headlineLarge.copy(fontFamily = f),
    headlineMedium = headlineMedium.copy(fontFamily = f),
    headlineSmall  = headlineSmall.copy(fontFamily = f),
    titleLarge  = titleLarge.copy(fontFamily = f),
    titleMedium = titleMedium.copy(fontFamily = f),
    titleSmall  = titleSmall.copy(fontFamily = f),
    bodyLarge  = bodyLarge.copy(fontFamily = f),
    bodyMedium = bodyMedium.copy(fontFamily = f),
    bodySmall  = bodySmall.copy(fontFamily = f),
    labelLarge  = labelLarge.copy(fontFamily = f),
    labelMedium = labelMedium.copy(fontFamily = f),
    labelSmall  = labelSmall.copy(fontFamily = f),
)

val Typography = Typography(
    // 必要ならここでサイズ/行間を個別に上書きしてから…
).withDefaultFontFamily(AppFontFamily)
