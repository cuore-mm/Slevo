package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * タブ一覧画面全体で共有するレイアウト寸法を提供する。
 *
 * 上部検索領域、下部操作群、リスト余白、スクロールバー余白など、
 * 複数のコンポーネントで整合させたい値をここへ集約する。
 */
internal object TabListLayoutDefaults {
    val topSearchHeight: Dp = 72.dp
    val controlsHorizontalPadding: Dp = 16.dp
    val topSearchVerticalPadding: Dp = 8.dp
    val searchBarHeight: Dp = 48.dp
    val searchBarElevation: Dp = 3.dp
    val listTopSpacing: Dp = 8.dp
    val listItemSpacing: Dp = 12.dp
    val bottomHazeOverlap: Dp = 32.dp
    val bottomControlHeight: Dp = 48.dp
    val bottomSectionSpacing: Dp = 8.dp
    val bottomProgressHeight: Dp = 8.dp
    val bottomPadding: Dp = 16.dp
    val bottomActionIconSize: Dp = 28.dp
    val scrollbarBottomInset: Dp = 24.dp
    val emptyStateHorizontalPadding: Dp = 16.dp
    val emptyStateVerticalPadding: Dp = 40.dp

    /**
     * リストが下部操作群に隠れないために必要な bottom padding を返す。
     */
    val listBottomPadding: Dp
        get() = bottomHazeOverlap + bottomControlHeight + bottomSectionSpacing + bottomProgressHeight + bottomPadding

    /**
     * 空状態プレビューで使う標準余白を返す。
     */
    val emptyStatePreviewPadding: PaddingValues
        get() = PaddingValues(
            vertical = emptyStateVerticalPadding,
            horizontal = emptyStateHorizontalPadding,
        )
}

/**
 * タブ一覧画面全体で共有するアニメーション時間を提供する。
 *
 * リスト表示切り替え、検索バー表示、削除アニメーションなど、
 * 画面全体のテンポを合わせたい箇所で利用する。
 */
internal object TabListAnimationDefaults {
    const val LIST_FADE_IN_MILLIS: Int = 180
    const val LIST_FADE_OUT_MILLIS: Int = 120
    const val VISIBILITY_MILLIS: Int = 200
    const val ITEM_FADE_OUT_MILLIS: Int = 100
    const val ITEM_COLLAPSE_DELAY_MILLIS: Int = 40
    const val ITEM_COLLAPSE_MILLIS: Int = 160
    const val ITEM_REMOVAL_MILLIS: Int = ITEM_COLLAPSE_DELAY_MILLIS + ITEM_COLLAPSE_MILLIS
    const val DRAG_HANDOFF_MILLIS: Int = 120
}
