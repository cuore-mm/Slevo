package com.websarva.wings.android.slevo.ui.thread

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val TREE_INDENT_MAX_RATIO = 0.25f
internal val DEFAULT_TREE_INDENT_STEP = 16.dp

/**
 * ツリー表示の段数から、各投稿のインデント幅を算出するユーティリティ。
 *
 * インデント上限は通常レス横幅の 1/4 とし、ツリーごとに増分を調整する。
 */
internal fun calculateTreeIndentWidths(
    depths: List<Int>,
    containerWidth: Dp,
    defaultStep: Dp = DEFAULT_TREE_INDENT_STEP,
    maxIndentRatio: Float = TREE_INDENT_MAX_RATIO,
): List<Dp> {
    // --- Guard clauses ---
    if (depths.isEmpty()) {
        // Guard: 空入力は空のインデント一覧を返す。
        return emptyList()
    }
    if (containerWidth <= 0.dp) {
        // Guard: 幅未計測時はデフォルト増分で計算する。
        return depths.map { depth -> defaultStep * depth.toFloat() }
    }

    // --- Max depth mapping ---
    val maxDepths = mapTreeMaxDepths(depths)

    // --- Width calculation ---
    return depths.mapIndexed { index, depth ->
        val maxDepth = maxDepths[index]
        val step = calculateTreeIndentStep(
            containerWidth = containerWidth,
            maxDepth = maxDepth,
            defaultStep = defaultStep,
            maxIndentRatio = maxIndentRatio,
        )
        step * depth.toFloat()
    }
}

/**
 * ツリー単位の最大深さからインデント増分を計算する。
 *
 * 最大深さが上限を超える場合のみ、デフォルト増分を縮小する。
 */
internal fun calculateTreeIndentStep(
    containerWidth: Dp,
    maxDepth: Int,
    defaultStep: Dp = DEFAULT_TREE_INDENT_STEP,
    maxIndentRatio: Float = TREE_INDENT_MAX_RATIO,
): Dp {
    // --- Guard clauses ---
    if (maxDepth <= 0) {
        // Guard: ルートのみのツリーはインデント不要。
        return 0.dp
    }
    if (containerWidth <= 0.dp) {
        // Guard: 幅未計測時は既存の増分を優先する。
        return defaultStep
    }
    // --- Width scaling ---
    val maxIndent = containerWidth * maxIndentRatio
    val scaledStep = maxIndent / maxDepth.toFloat()
    return if (scaledStep < defaultStep) scaledStep else defaultStep
}

/**
 * ツリー表示順の深さリストから、各要素が属するツリーの最大深さを算出する。
 *
 * 深さ 0 をツリー開始とみなし、次の深さ 0 までを同一ツリーとして扱う。
 */
internal fun mapTreeMaxDepths(depths: List<Int>): List<Int> {
    // --- Guard clauses ---
    if (depths.isEmpty()) {
        // Guard: 空入力は空の最大深さ一覧を返す。
        return emptyList()
    }

    val result = IntArray(depths.size)
    var startIndex = 0
    while (startIndex < depths.size) {
        // --- Tree boundary detection ---
        var endIndex = startIndex + 1
        while (endIndex < depths.size && depths[endIndex] != 0) {
            endIndex++
        }

        // --- Max depth calculation ---
        var maxDepth = 0
        for (i in startIndex until endIndex) {
            if (depths[i] > maxDepth) {
                maxDepth = depths[i]
            }
        }
        // --- Mapping ---
        for (i in startIndex until endIndex) {
            result[i] = maxDepth
        }
        startIndex = endIndex
    }
    return result.toList()
}
