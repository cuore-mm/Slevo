package com.websarva.wings.android.slevo.ui.thread

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val TREE_INDENT_MAX_RATIO = 0.25f
internal val DEFAULT_TREE_INDENT_STEP = 16.dp

/**
 * ツリー表示の段数とルート番号から、各投稿のインデント幅を算出するユーティリティ。
 *
 * インデント上限は通常レス横幅の 1/4 とし、ツリーごとに増分を調整する。
 */
internal fun calculateTreeIndentWidths(
    depths: List<Int>,
    rootNumbers: List<Int>,
    containerWidth: Dp,
    defaultStep: Dp = DEFAULT_TREE_INDENT_STEP,
    maxIndentRatio: Float = TREE_INDENT_MAX_RATIO,
): List<Dp> {
    // --- Guard clauses ---
    if (depths.isEmpty()) {
        // Guard: 空入力は空のインデント一覧を返す。
        return emptyList()
    }
    if (depths.size != rootNumbers.size) {
        // Guard: 対応関係が壊れている場合は例外で通知する。
        throw IllegalArgumentException("depths and rootNumbers must have same size")
    }
    if (containerWidth <= 0.dp) {
        // Guard: 幅未計測時はデフォルト増分で計算する。
        return depths.map { depth -> defaultStep * depth.toFloat() }
    }

    // --- Max depth mapping ---
    val maxDepths = mapTreeMaxDepthsByRoot(depths, rootNumbers)

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
 * ツリー表示順の深さとルート番号から、各要素が属するツリーの最大深さを算出する。
 *
 * ルート番号でツリー境界を判断し、フィルタ後でも誤結合を防ぐ。
 */
internal fun mapTreeMaxDepthsByRoot(
    depths: List<Int>,
    rootNumbers: List<Int>,
): List<Int> {
    // --- Guard clauses ---
    if (depths.isEmpty()) {
        // Guard: 空入力は空の最大深さ一覧を返す。
        return emptyList()
    }
    if (depths.size != rootNumbers.size) {
        // Guard: 対応関係が壊れている場合は例外で通知する。
        throw IllegalArgumentException("depths and rootNumbers must have same size")
    }

    // --- Max depth lookup ---
    val maxDepthByRoot = mutableMapOf<Int, Int>()
    depths.forEachIndexed { index, depth ->
        val root = rootNumbers[index]
        val currentMax = maxDepthByRoot[root] ?: 0
        if (depth > currentMax) {
            maxDepthByRoot[root] = depth
        }
    }

    // --- Mapping ---
    return depths.mapIndexed { index, _ ->
        maxDepthByRoot[rootNumbers[index]] ?: 0
    }
}
