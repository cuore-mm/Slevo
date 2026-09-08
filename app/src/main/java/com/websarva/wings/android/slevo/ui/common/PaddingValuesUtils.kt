package com.websarva.wings.android.slevo.ui.common

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * 画面自身の Insets と、親が所有するアプリ chrome の Insets を合成する。
 *
 * 上・左右は画面側を採用し、下だけは双方の大きい値を採用して、同じ領域を加算しない。
 */
internal fun mergeScaffoldPaddingValues(
    screenPadding: PaddingValues,
    appChromePadding: PaddingValues,
): PaddingValues = object : PaddingValues {
    override fun calculateTopPadding(): Dp = screenPadding.calculateTopPadding()

    override fun calculateBottomPadding(): Dp = max(
        screenPadding.calculateBottomPadding().value,
        appChromePadding.calculateBottomPadding().value,
    ).dp

    override fun calculateStartPadding(layoutDirection: LayoutDirection): Dp =
        screenPadding.calculateStartPadding(layoutDirection)

    override fun calculateEndPadding(layoutDirection: LayoutDirection): Dp =
        screenPadding.calculateEndPadding(layoutDirection)
}

/**
 * 既存のコンテンツ余白へ別の余白を加え、Insets をスクロール内容へ含める。
 *
 * 方向依存の start/end は呼び出し時の LayoutDirection で計算するため、RTLでも順序を保つ。
 */
internal fun addPaddingValues(
    base: PaddingValues,
    additional: PaddingValues,
): PaddingValues = object : PaddingValues {
    override fun calculateTopPadding(): Dp =
        base.calculateTopPadding() + additional.calculateTopPadding()

    override fun calculateBottomPadding(): Dp =
        base.calculateBottomPadding() + additional.calculateBottomPadding()

    override fun calculateStartPadding(layoutDirection: LayoutDirection): Dp =
        base.calculateStartPadding(layoutDirection) + additional.calculateStartPadding(layoutDirection)

    override fun calculateEndPadding(layoutDirection: LayoutDirection): Dp =
        base.calculateEndPadding(layoutDirection) + additional.calculateEndPadding(layoutDirection)
}
