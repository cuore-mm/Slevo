package com.websarva.wings.android.slevo.ui.common

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.ThreadInfo
import com.websarva.wings.android.slevo.ui.common.bookmark.BookmarkStatusState
import com.websarva.wings.android.slevo.ui.theme.bookmarkColor
import com.websarva.wings.android.slevo.ui.thread.components.ThreadToolBar
import com.websarva.wings.android.slevo.ui.thread.state.ThreadUiState

/**
 * タブ型ボトムバーに並べる単体アクションの表示情報を保持する。
 *
 * アイコンとアクセシビリティ文言、実行コールバックをひとまとまりで扱う。
 */
data class TabToolBarAction(
    val icon: ImageVector,
    @param:StringRes val contentDescriptionRes: Int,
    val onClick: () -> Unit,
    val tint: Color? = null,
)

private const val CollapsedTitleScale = 0.85f
private const val IconEnableThreshold = 0.5f
private val ExpandedHeight = 96.dp
private val CollapsedHeight = 56.dp
private val SideSlotMaxWidth = 48.dp
private val ActionRowTranslation = 24.dp
private val CollapsedIconTranslation = 8.dp
private val ExpandedIconTranslation = 6.dp
private val CollapsedTitleFontWeight = FontWeight.Medium
private val CollapsedTitleHorizontalPadding = 12.dp
private val ExpandedTitleHorizontalPadding = 0.dp
private val CollapsedTitleVerticalPadding = 8.dp
private val ExpandedTitleVerticalPadding = 0.dp

/**
 * TabToolBar のレイアウト計算結果をまとめて保持する。
 *
 * 縮退率に応じた高さ・フォントサイズ・表示閾値を共有するために使う。
 * カード内外のアイコン枠幅もここでまとめて管理する。
 */
data class TabToolBarLayoutState(
    val clampedProgress: Float,
    val collapsedAlpha: Float,
    val expandedHeight: Dp,
    val sideSlotWidth: Dp,
    val cardSideSlotWidth: Dp,
    val titleFontSize: TextUnit,
    val titleFontWeight: FontWeight,
    val titleMaxLines: Int,
    val titleHorizontalPadding: Dp,
    val titleVerticalPadding: Dp,
    val actionTranslationPx: Float,
    val collapsedTranslationPx: Float,
    val expandedTranslationPx: Float,
    val collapsedIconEnabled: Boolean,
    val expandedIconEnabled: Boolean,
)

/**
 * TabToolBar の表示補間に使うレイアウト値を計算する。
 *
 * 進捗値とテキストスタイルから高さ・文字サイズ・太さ・行数・余白・表示閾値を導出する。
 */
@Composable
fun rememberTabToolBarLayoutState(
    actionsProgress: Float,
    titleStyle: TextStyle,
    titleFontWeight: FontWeight,
    titleMaxLines: Int,
): TabToolBarLayoutState {
    // --- Progress ---
    val clampedProgress = actionsProgress.coerceIn(0f, 1f)
    val collapsedAlpha = 1f - clampedProgress

    // --- Heights ---
    val targetHeight = CollapsedHeight + (ExpandedHeight - CollapsedHeight) * clampedProgress
    val expandedHeight by animateDpAsState(
        targetValue = targetHeight,
        label = "BottomBarHeight",
    )
    val sideSlotWidth by animateDpAsState(
        targetValue = SideSlotMaxWidth * collapsedAlpha,
        label = "CollapsedSideSlotWidth",
    )
    val cardSideSlotWidth by animateDpAsState(
        targetValue = SideSlotMaxWidth * clampedProgress,
        label = "ExpandedCardSideSlotWidth",
    )

    // --- Typography ---
    val baseFontSize = if (titleStyle.fontSize != TextUnit.Unspecified) {
        titleStyle.fontSize
    } else {
        MaterialTheme.typography.titleSmall.fontSize
    }
    val collapsedFontSize = baseFontSize * CollapsedTitleScale
    val titleFontSize = lerp(baseFontSize, collapsedFontSize, collapsedAlpha)
    val resolvedTitleFontWeight = if (collapsedAlpha > 0f) {
        CollapsedTitleFontWeight
    } else {
        titleFontWeight
    }
    val resolvedTitleMaxLines = if (collapsedAlpha > 0f) 1 else titleMaxLines
    val titleHorizontalPadding = lerp(
        ExpandedTitleHorizontalPadding,
        CollapsedTitleHorizontalPadding,
        collapsedAlpha,
    )
    val titleVerticalPadding = lerp(
        ExpandedTitleVerticalPadding,
        CollapsedTitleVerticalPadding,
        collapsedAlpha,
    )

    // --- Translations ---
    val density = LocalDensity.current
    val actionTranslationPx = with(density) { ActionRowTranslation.toPx() }
    val collapsedTranslationPx = with(density) { CollapsedIconTranslation.toPx() }
    val expandedTranslationPx = with(density) { ExpandedIconTranslation.toPx() }

    // --- Thresholds ---
    val collapsedIconEnabled = collapsedAlpha > IconEnableThreshold
    val expandedIconEnabled = clampedProgress > IconEnableThreshold

    return TabToolBarLayoutState(
        clampedProgress = clampedProgress,
        collapsedAlpha = collapsedAlpha,
        expandedHeight = expandedHeight,
        sideSlotWidth = sideSlotWidth,
        cardSideSlotWidth = cardSideSlotWidth,
        titleFontSize = titleFontSize,
        titleFontWeight = resolvedTitleFontWeight,
        titleMaxLines = resolvedTitleMaxLines,
        titleHorizontalPadding = titleHorizontalPadding,
        titleVerticalPadding = titleVerticalPadding,
        actionTranslationPx = actionTranslationPx,
        collapsedTranslationPx = collapsedTranslationPx,
        expandedTranslationPx = expandedTranslationPx,
        collapsedIconEnabled = collapsedIconEnabled,
        expandedIconEnabled = expandedIconEnabled,
    )
}

/**
 * 板/スレッド画面のボトムバー表示を共通化する。
 *
 * 上段はタイトル・ブックマーク・更新、下段はアクション群を並べる。
 * `actionsProgress` でアクション群の縮退率を制御する。
 * 縮退時はタイトルを小さくし、カード外にタブ/書き込みアイコンを表示する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TabToolBar(
    modifier: Modifier = Modifier,
    title: String,
    bookmarkState: BookmarkStatusState,
    onBookmarkClick: () -> Unit,
    actions: List<TabToolBarAction>,
    onTabListClick: () -> Unit,
    onPostClick: () -> Unit,
    tabIconContentDescriptionRes: Int,
    postIconContentDescriptionRes: Int,
    actionsProgress: Float = 1f,
    onTitleClick: (() -> Unit),
    onRefreshClick: (() -> Unit),
    isLoading: Boolean = false,
    loadProgress: Float = 0f,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
    titleFontWeight: FontWeight = FontWeight.Bold,
    titleMaxLines: Int = 2,
    titleTextAlign: TextAlign = TextAlign.Start,
) {
    // --- Layout state ---
    val layoutState = rememberTabToolBarLayoutState(
        actionsProgress = actionsProgress,
        titleStyle = titleStyle,
        titleFontWeight = titleFontWeight,
        titleMaxLines = titleMaxLines,
    )
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)

    // --- Layout ---
    Box(modifier = modifier.fillMaxWidth()) {
        FlexibleBottomAppBar(
            expandedHeight = layoutState.expandedHeight,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                // --- Header ---
                TabToolBarHeader(
                    title = title,
                    bookmarkState = bookmarkState,
                    onBookmarkClick = onBookmarkClick,
                    onTabListClick = onTabListClick,
                    onPostClick = onPostClick,
                    onTitleClick = onTitleClick,
                    onRefreshClick = onRefreshClick,
                    tabIconContentDescriptionRes = tabIconContentDescriptionRes,
                    postIconContentDescriptionRes = postIconContentDescriptionRes,
                    titleStyle = titleStyle,
                    titleTextAlign = titleTextAlign,
                    layoutState = layoutState,
                    cardModifier = cardModifier,
                )

                // --- Actions row ---
                BottomActionsRow(
                    actions = actions,
                    layoutState = layoutState,
                )
            }
        }
        if (isLoading) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                color = ProgressIndicatorDefaults.linearColor,
                trackColor = ProgressIndicatorDefaults.linearTrackColor,
                strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
            )
        }
    }
}

/**
 * TabToolBar の上段ヘッダーを組み立てる。
 *
 * 左右の縮退アイコンと中央のタイトルカードをまとめて配置する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabToolBarHeader(
    modifier: Modifier = Modifier,
    title: String,
    bookmarkState: BookmarkStatusState,
    onBookmarkClick: () -> Unit,
    onTabListClick: () -> Unit,
    onPostClick: () -> Unit,
    onTitleClick: (() -> Unit),
    onRefreshClick: () -> Unit,
    @StringRes tabIconContentDescriptionRes: Int,
    @StringRes postIconContentDescriptionRes: Int,
    titleStyle: TextStyle,
    titleTextAlign: TextAlign,
    layoutState: TabToolBarLayoutState,
    cardModifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollapsedSideAction(
            slotWidth = layoutState.sideSlotWidth,
            alpha = layoutState.collapsedAlpha,
            translationY = layoutState.collapsedTranslationPx,
            enabled = layoutState.collapsedIconEnabled,
            tooltipText = stringResource(tabIconContentDescriptionRes),
            onClick = onTabListClick,
        ) {
            Icon(
                imageVector = Icons.Filled.CropSquare,
                contentDescription = stringResource(tabIconContentDescriptionRes),
            )
        }

        ExpandedTitleActions(
            modifier = cardModifier.weight(1f),
            title = title,
            bookmarkState = bookmarkState,
            onTitleClick = onTitleClick,
            onBookmarkClick = onBookmarkClick,
            onRefreshClick = onRefreshClick,
            titleStyle = titleStyle,
            titleTextAlign = titleTextAlign,
            layoutState = layoutState,
        )

        CollapsedSideAction(
            slotWidth = layoutState.sideSlotWidth,
            alpha = layoutState.collapsedAlpha,
            translationY = layoutState.collapsedTranslationPx,
            enabled = layoutState.collapsedIconEnabled,
            tooltipText = stringResource(postIconContentDescriptionRes),
            onClick = onPostClick,
        ) {
            Icon(
                imageVector = Icons.Filled.Create,
                contentDescription = stringResource(postIconContentDescriptionRes),
            )
        }
    }
}

/**
 * タイトルカードの展開アイコンとタイトル文字列を描画する。
 *
 * 展開率に応じてカード内アイコンの表示とタイトル文字スタイルを切り替える。
 * アイコン用の幅を維持してタイトル幅の急変を防ぐ。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpandedTitleActions(
    modifier: Modifier = Modifier,
    title: String,
    bookmarkState: BookmarkStatusState,
    onTitleClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onRefreshClick: () -> Unit,
    titleStyle: TextStyle,
    titleTextAlign: TextAlign,
    layoutState: TabToolBarLayoutState,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.largeIncreased,
        onClick = onTitleClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpandedCardAction(
                slotWidth = layoutState.cardSideSlotWidth,
                alpha = layoutState.clampedProgress,
                translationY = layoutState.expandedTranslationPx,
                enabled = layoutState.expandedIconEnabled,
                tooltipText = stringResource(R.string.bookmark),
                onClick = onBookmarkClick,
            ) {
                if (bookmarkState.isBookmarked) {
                    val tintColor =
                        bookmarkState.selectedGroup?.colorName?.let { bookmarkColor(it) }
                            ?: LocalContentColor.current
                    Box {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = tintColor,
                        )
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = stringResource(R.string.bookmark),
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Outlined.StarOutline,
                        contentDescription = stringResource(R.string.bookmark),
                    )
                }
            }

            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        horizontal = layoutState.titleHorizontalPadding,
                        vertical = layoutState.titleVerticalPadding,
                    )
                    .animateContentSize(),
                fontWeight = layoutState.titleFontWeight,
                style = titleStyle.copy(fontSize = layoutState.titleFontSize),
                maxLines = layoutState.titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = titleTextAlign,
            )

            ExpandedCardAction(
                slotWidth = layoutState.cardSideSlotWidth,
                alpha = layoutState.clampedProgress,
                translationY = layoutState.expandedTranslationPx,
                enabled = layoutState.expandedIconEnabled,
                tooltipText = stringResource(R.string.refresh),
                onClick = onRefreshClick,
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.refresh),
                )
            }
        }
    }
}

/**
 * 縮退時にカード外へ表示する左右アクションを描画する。
 *
 * スロット幅を保ちながらアイコンの透過と移動を同期させる。
 */
@Composable
private fun CollapsedSideAction(
    slotWidth: Dp,
    alpha: Float,
    translationY: Float,
    enabled: Boolean,
    tooltipText: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.width(slotWidth),
        contentAlignment = Alignment.Center,
    ) {
        if (alpha > 0f) {
            FeedbackTooltipIconButton(
                modifier = Modifier.graphicsLayer {
                    this.alpha = alpha
                    this.translationY = (1f - alpha) * translationY
                },
                tooltipText = tooltipText,
                showTooltipHost = enabled,
                onClick = {
                    // Guard: アイコンが薄い間は押下を抑止する。
                    if (enabled) {
                        onClick()
                    }
                },
            ) {
                icon()
            }
        }
    }
}

/**
 * タイトルカード内の展開アイコンを描画する。
 *
 * 展開率に応じた透過と移動を適用し、閾値未満では押下を抑止する。
 * スロット幅を維持してタイトル領域の伸縮を滑らかにする。
 */
@Composable
private fun ExpandedCardAction(
    slotWidth: Dp,
    alpha: Float,
    translationY: Float,
    enabled: Boolean,
    tooltipText: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.width(slotWidth),
        contentAlignment = Alignment.Center,
    ) {
        if (alpha > 0f) {
            FeedbackTooltipIconButton(
                modifier = Modifier.graphicsLayer {
                    this.alpha = alpha
                    this.translationY = (1f - alpha) * translationY
                },
                tooltipText = tooltipText,
                showTooltipHost = enabled,
                onClick = {
                    // Guard: 縮退中は誤タップを避ける。
                    if (enabled) {
                        onClick()
                    }
                },
            ) {
                icon()
            }
        }
    }
}

/**
 * 下段のアクションボタン群を描画する。
 *
 * 展開率に応じて透過と移動を適用し、タイトルカードの下に並べる。
 */
@Composable
private fun BottomActionsRow(
    actions: List<TabToolBarAction>,
    layoutState: TabToolBarLayoutState,
) {
    if (layoutState.clampedProgress <= 0f) {
        return
    }

    Spacer(modifier = Modifier.padding(2.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = layoutState.clampedProgress
                translationY =
                    (1f - layoutState.clampedProgress) *
                            layoutState.actionTranslationPx
            },
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        actions.forEach { action ->
            FeedbackTooltipIconButton(
                tooltipText = stringResource(action.contentDescriptionRes),
                onClick = action.onClick,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = stringResource(action.contentDescriptionRes),
                    tint = action.tint ?: LocalContentColor.current,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ThreadToolBarPreview() {
    ThreadToolBar(
        uiState = ThreadUiState(
            threadInfo = ThreadInfo(
                title = "スレッドのタイトル"
            ),
            bookmarkStatusState = BookmarkStatusState(
                isBookmarked = false,
                selectedGroup = null
            )
        ),
        isTreeSort = false,
        onSortClick = {},
        onPostClick = {},
        onTabListClick = {},
        onRefreshClick = {},
        onSearchClick = {},
        onBookmarkClick = {},
        onThreadInfoClick = {},
        onMoreClick = {},
        onAutoScrollClick = {}
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "ThreadToolBar Collapsed")
@Composable
fun ThreadToolBarCollapsedPreview() {
    ThreadToolBar(
        uiState = ThreadUiState(
            threadInfo = ThreadInfo(title = "スレッドのタイトル"),
            bookmarkStatusState = BookmarkStatusState(
                isBookmarked = false,
                selectedGroup = null
            )
        ),
        isTreeSort = false,
        onSortClick = {},
        onPostClick = {},
        onTabListClick = {},
        onRefreshClick = {},
        onSearchClick = {},
        onBookmarkClick = {},
        onThreadInfoClick = {},
        onMoreClick = {},
        onAutoScrollClick = {},
        actionsProgress = 0f,
    )
}
