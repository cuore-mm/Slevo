package com.websarva.wings.android.slevo.ui.common

import androidx.annotation.StringRes
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
import androidx.compose.animation.core.animateDpAsState
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

/**
 * TabToolBar のレイアウト計算結果をまとめて保持する。
 *
 * 縮退率に応じた高さ・フォントサイズ・表示閾値を共有するために使う。
 */
data class TabToolBarLayoutState(
    val clampedProgress: Float,
    val collapsedAlpha: Float,
    val expandedHeight: Dp,
    val sideSlotWidth: Dp,
    val titleFontSize: TextUnit,
    val actionTranslationPx: Float,
    val collapsedTranslationPx: Float,
    val expandedTranslationPx: Float,
    val collapsedIconEnabled: Boolean,
    val expandedIconEnabled: Boolean,
)

/**
 * TabToolBar の表示補間に使うレイアウト値を計算する。
 *
 * 進捗値とテキストスタイルから高さ・フォントサイズ・表示閾値を導出する。
 */
@Composable
fun rememberTabToolBarLayoutState(
    actionsProgress: Float,
    titleStyle: TextStyle,
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

    // --- Typography ---
    val baseFontSize = if (titleStyle.fontSize != TextUnit.Unspecified) {
        titleStyle.fontSize
    } else {
        MaterialTheme.typography.titleSmall.fontSize
    }
    val collapsedFontSize = baseFontSize * CollapsedTitleScale
    val titleFontSize = baseFontSize + (collapsedFontSize - baseFontSize) * collapsedAlpha

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
        titleFontSize = titleFontSize,
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
    onTitleClick: (() -> Unit)? = null,
    onRefreshClick: (() -> Unit),
    isLoading: Boolean = false,
    loadProgress: Float = 0f,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
    titleFontWeight: FontWeight = FontWeight.Bold,
    titleMaxLines: Int = 2,
    titleTextAlign: TextAlign = TextAlign.Start,
) {
    val layoutState = rememberTabToolBarLayoutState(
        actionsProgress = actionsProgress,
        titleStyle = titleStyle,
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
                // --- Title row ---
                val cardContent: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (layoutState.clampedProgress > 0f) {
                            FeedbackTooltipIconButton(
                                modifier = Modifier.graphicsLayer {
                                    alpha = layoutState.clampedProgress
                                    translationY =
                                        (1f - layoutState.clampedProgress) *
                                            layoutState.expandedTranslationPx
                                },
                                tooltipText = stringResource(R.string.bookmark),
                                showTooltipHost = layoutState.expandedIconEnabled,
                                onClick = {
                                    // Guard: 縮退中は誤タップを避ける。
                                    if (layoutState.expandedIconEnabled) {
                                        onBookmarkClick()
                                    }
                                },
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
                        }
                        Text(
                            text = title,
                            fontWeight = titleFontWeight,
                            style = titleStyle.copy(fontSize = layoutState.titleFontSize),
                            maxLines = titleMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = titleTextAlign,
                            modifier = Modifier.weight(1f),
                        )
                        if (layoutState.clampedProgress > 0f) {
                            FeedbackTooltipIconButton(
                                modifier = Modifier.graphicsLayer {
                                    alpha = layoutState.clampedProgress
                                    translationY =
                                        (1f - layoutState.clampedProgress) *
                                            layoutState.expandedTranslationPx
                                },
                                tooltipText = stringResource(R.string.refresh),
                                showTooltipHost = layoutState.expandedIconEnabled,
                                onClick = {
                                    // Guard: 縮退中は誤タップを避ける。
                                    if (layoutState.expandedIconEnabled) {
                                        onRefreshClick()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(R.string.refresh),
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.width(layoutState.sideSlotWidth),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (layoutState.collapsedAlpha > 0f) {
                            FeedbackTooltipIconButton(
                                modifier = Modifier.graphicsLayer {
                                    alpha = layoutState.collapsedAlpha
                                    translationY =
                                        (1f - layoutState.collapsedAlpha) *
                                            layoutState.collapsedTranslationPx
                                },
                                tooltipText = stringResource(tabIconContentDescriptionRes),
                                showTooltipHost = layoutState.collapsedIconEnabled,
                                onClick = {
                                    // Guard: アイコンが薄い間は押下を抑止する。
                                    if (layoutState.collapsedIconEnabled) {
                                        onTabListClick()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CropSquare,
                                    contentDescription = stringResource(tabIconContentDescriptionRes),
                                )
                            }
                        }
                    }

                    if (onTitleClick != null) {
                        Card(
                            modifier = cardModifier.weight(1f),
                            onClick = onTitleClick,
                        ) {
                            cardContent()
                        }
                    } else {
                        Card(modifier = cardModifier.weight(1f)) {
                            cardContent()
                        }
                    }

                    Box(
                        modifier = Modifier.width(layoutState.sideSlotWidth),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (layoutState.collapsedAlpha > 0f) {
                            FeedbackTooltipIconButton(
                                modifier = Modifier.graphicsLayer {
                                    alpha = layoutState.collapsedAlpha
                                    translationY =
                                        (1f - layoutState.collapsedAlpha) *
                                            layoutState.collapsedTranslationPx
                                },
                                tooltipText = stringResource(postIconContentDescriptionRes),
                                showTooltipHost = layoutState.collapsedIconEnabled,
                                onClick = {
                                    // Guard: アイコンが薄い間は押下を抑止する。
                                    if (layoutState.collapsedIconEnabled) {
                                        onPostClick()
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Create,
                                    contentDescription = stringResource(postIconContentDescriptionRes),
                                )
                            }
                        }
                    }
                }

                if (layoutState.clampedProgress > 0f) {
                    Spacer(modifier = Modifier.padding(2.dp))

                    // --- Actions row ---
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
