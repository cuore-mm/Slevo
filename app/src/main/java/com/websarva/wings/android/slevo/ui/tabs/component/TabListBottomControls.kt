package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabRefreshProgress
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/**
 * 下部の1段操作群を表示し、板/スレ切替とタブ操作を提供する。
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun TabListBottomControls(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    hazeState: HazeState,
    isRefreshing: Boolean,
    isSearchMode: Boolean,
    isSelectionMode: Boolean = false,
    selectedTabCount: Int = 0,
    refreshProgress: ThreadTabRefreshProgress?,
    onCreateTabClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onCancelRefreshClick: () -> Unit,
) {
    // --- State ---
    val isBoardPage = pagerState.currentPage == TabPage.BOARD.index
    val coroutineScope = rememberCoroutineScope()
    val indicatorProgress = refreshProgress?.progress ?: 0f

    val tapGuardInteractionSource = remember { MutableInteractionSource() }

    // --- Floating controls layout ---
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                    progressive = HazeProgressive.verticalGradient(
                        startIntensity = 0f,
                        endIntensity = 0.5f,
                        preferPerformance = true
                    )
                }
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(top = TabListLayoutDefaults.bottomHazeOverlap)
                .clickable(
                    interactionSource = tapGuardInteractionSource,
                    indication = null,
                    onClick = {},
                )
                .padding(bottom = TabListLayoutDefaults.bottomPadding),
            verticalArrangement = Arrangement.spacedBy(TabListLayoutDefaults.bottomSectionSpacing),
        ) {
            AnimatedVisibility(
                visible = !isSearchMode || isSelectionMode,
                enter = fadeIn(animationSpec = tween(TabListAnimationDefaults.VISIBILITY_MILLIS)),
                exit = fadeOut(animationSpec = tween(TabListAnimationDefaults.VISIBILITY_MILLIS)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TabListLayoutDefaults.bottomContentHeight),
                ) {
                    // 通常操作と選択数表示を同じフットプリントに重ね、切り替え時の上下移動を防ぐ。
                    AnimatedVisibility(
                        modifier = Modifier.matchParentSize(),
                        visible = !isSearchMode && !isSelectionMode,
                        enter = fadeIn(
                            animationSpec = tween(TabListAnimationDefaults.VISIBILITY_MILLIS),
                        ),
                        exit = fadeOut(
                            animationSpec = tween(TabListAnimationDefaults.VISIBILITY_MILLIS),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(TabListLayoutDefaults.bottomSectionSpacing),
                        ) {
                            TabListInlineSection(
                                modifier = Modifier.padding(horizontal = TabListLayoutDefaults.controlsHorizontalPadding),
                                selectedIndex = pagerState.currentPage,
                                isBoardPage = isBoardPage,
                                isRefreshing = isRefreshing,
                                onSelect = { index ->
                                    if (pagerState.currentPage != index) {
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                },
                                onCreateTabClick = onCreateTabClick,
                                onRefreshClick = onRefreshClick,
                                onCancelRefreshClick = onCancelRefreshClick,
                            )
                            TabListRefreshProgressSlot(
                                isVisible = isRefreshing,
                                progress = indicatorProgress,
                            )
                        }
                    }
                    AnimatedVisibility(
                        modifier = Modifier.matchParentSize(),
                        visible = isSelectionMode,
                        enter = fadeIn(animationSpec = tween(TabListAnimationDefaults.VISIBILITY_MILLIS)),
                        exit = fadeOut(animationSpec = tween(TabListAnimationDefaults.VISIBILITY_MILLIS)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(TabListLayoutDefaults.bottomControlHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.tab_selection_count, selectedTabCount),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 下部操作群の 1 段レイアウトを構成し、作成・切替・更新を表示する。
 */
@Composable
private fun TabListInlineSection(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    isBoardPage: Boolean,
    isRefreshing: Boolean,
    onSelect: (Int) -> Unit,
    onCreateTabClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onCancelRefreshClick: () -> Unit,
) {
    // --- Refresh button state ---
    val isRefreshingAnyPage = isRefreshing
    val refreshIcon = if (isRefreshingAnyPage) Icons.Default.Close else Icons.Default.Refresh
    val refreshColor =
        if (isRefreshingAnyPage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val refreshDescription = if (isRefreshingAnyPage) {
        stringResource(R.string.cancel)
    } else {
        stringResource(R.string.refresh)
    }
    val refreshAction = if (isRefreshingAnyPage) onCancelRefreshClick else onRefreshClick

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TabListLayoutDefaults.bottomSectionSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabActionButton(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.open_url),
            onClick = onCreateTabClick,
        )
        TabListSwitchSection(
            modifier = Modifier.weight(1f),
            selectedIndex = selectedIndex,
            onSelect = onSelect,
        )
        if (isBoardPage && !isRefreshingAnyPage) {
            Spacer(modifier = Modifier.size(TabListLayoutDefaults.bottomControlHeight))
        } else {
            TabActionButton(
                imageVector = refreshIcon,
                contentDescription = refreshDescription,
                onClick = refreshAction,
                tint = refreshColor,
            )
        }
    }
}

/**
 * 更新中のみ進捗を描画するための固定スロットを提供する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TabListRefreshProgressSlot(
    isVisible: Boolean,
    progress: Float,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "tabRefreshProgress",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TabListLayoutDefaults.bottomProgressHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (isVisible) {
            LinearWavyProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 下部操作群の上段に板/スレ切替UIを表示する。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TabListSwitchSection(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val roundedCornerShape = MaterialTheme.shapes.extraLargeIncreased
    Surface(
        modifier = modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = roundedCornerShape,
            ),
        shape = roundedCornerShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
    ) {
        val options = listOf(
            stringResource(R.string.board),
            stringResource(R.string.thread),
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(TabListLayoutDefaults.bottomControlHeight)
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            // --- Sliding indicator ---
            val segmentSpacing = TabListLayoutDefaults.bottomSectionSpacing
            val segmentWidth = (maxWidth - segmentSpacing) / TabPage.count
            val indicatorOffsetX by animateDpAsState(
                targetValue = if (selectedIndex == TabPage.BOARD.index) {
                    0.dp
                } else {
                    segmentWidth + segmentSpacing
                },
                animationSpec = tween(TabListAnimationDefaults.VISIBILITY_MILLIS + 20),
                label = "tabSwitchIndicatorOffset",
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffsetX)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        shape = roundedCornerShape,
                    )
            )

            // --- Selectable labels ---
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(segmentSpacing),
            ) {
                options.forEachIndexed { index, label ->
                    val isSelected = selectedIndex == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(roundedCornerShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = true),
                                onClick = { onSelect(index) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TabListBottomControlsBoardPreview() {
    val pagerState = rememberPagerState(
        initialPage = TabPage.BOARD.index,
        pageCount = { TabPage.count },
    )
    val hazeState = rememberHazeState()
    TabListBottomControls(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        pagerState = pagerState,
        hazeState = hazeState,
        isRefreshing = false,
        isSearchMode = false,
        isSelectionMode = false,
        selectedTabCount = 0,
        refreshProgress = null,
        onCreateTabClick = {},
        onRefreshClick = {},
        onCancelRefreshClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun TabListBottomControlsThreadPreview() {
    val pagerState = rememberPagerState(
        initialPage = TabPage.THREAD.index,
        pageCount = { TabPage.count },
    )
    val hazeState = rememberHazeState()
    TabListBottomControls(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        pagerState = pagerState,
        hazeState = hazeState,
        isRefreshing = false,
        isSearchMode = false,
        isSelectionMode = false,
        selectedTabCount = 0,
        refreshProgress = null,
        onCreateTabClick = {},
        onRefreshClick = {},
        onCancelRefreshClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun TabListBottomControlsRefreshingPreview() {
    val pagerState = rememberPagerState(
        initialPage = TabPage.THREAD.index,
        pageCount = { TabPage.count },
    )
    val hazeState = rememberHazeState()
    TabListBottomControls(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        pagerState = pagerState,
        hazeState = hazeState,
        isRefreshing = true,
        isSearchMode = false,
        isSelectionMode = false,
        selectedTabCount = 0,
        refreshProgress = ThreadTabRefreshProgress(
            completedCount = 3,
            totalCount = 8,
        ),
        onCreateTabClick = {},
        onRefreshClick = {},
        onCancelRefreshClick = {},
    )
}
