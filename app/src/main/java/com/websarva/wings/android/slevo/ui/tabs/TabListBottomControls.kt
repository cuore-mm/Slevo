package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/**
 * タブ一覧の下部操作群で利用するデフォルト寸法を保持する。
 */
private object ControlsDefaults {
    val hazeTopOverlap: Dp = 32.dp
    val controlHeight: Dp = 48.dp
    val actionIconSize: Dp = 28.dp
    val progressHeight: Dp = 8.dp
}

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
    refreshProgress: ThreadTabRefreshProgress?,
    onCreateTabClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onCancelRefreshClick: () -> Unit,
) {
    // --- State ---
    val isBoardPage = pagerState.currentPage == 0
    val coroutineScope = rememberCoroutineScope()
    val indicatorProgress = refreshProgress?.progress ?: 0f
    val showProgress = isRefreshing && !isBoardPage

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
                .padding(top = ControlsDefaults.hazeTopOverlap)
                .clickable(
                    interactionSource = tapGuardInteractionSource,
                    indication = null,
                    onClick = {},
                )
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabListInlineSection(
                modifier = Modifier.padding(horizontal = 16.dp),
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
                isVisible = showProgress,
                progress = indicatorProgress,
            )
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
    val isThreadRefreshing = !isBoardPage && isRefreshing
    val refreshIcon = if (isThreadRefreshing) Icons.Default.Close else Icons.Default.Refresh
    val refreshDescription = if (isThreadRefreshing) {
        stringResource(R.string.cancel)
    } else {
        stringResource(R.string.refresh)
    }
    val refreshAction = if (isThreadRefreshing) onCancelRefreshClick else onRefreshClick

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        if (isBoardPage) {
            Spacer(modifier = Modifier.size(ControlsDefaults.controlHeight))
        } else {
            TabActionButton(
                imageVector = refreshIcon,
                contentDescription = refreshDescription,
                onClick = refreshAction,
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ControlsDefaults.progressHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (isVisible) {
            LinearWavyProgressIndicator(
                progress = { progress },
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
                .height(ControlsDefaults.controlHeight)
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            // --- Sliding indicator ---
            val segmentSpacing = 8.dp
            val segmentWidth = (maxWidth - segmentSpacing) / 2
            val indicatorOffsetX by animateDpAsState(
                targetValue = if (selectedIndex == 0) 0.dp else (segmentWidth + segmentSpacing),
                animationSpec = tween(220),
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

/**
 * 下部操作群の丸形アイコンボタンを表示する。
 */
@Composable
private fun TabActionButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(ControlsDefaults.controlHeight),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                modifier = Modifier.size(ControlsDefaults.actionIconSize),
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TabListBottomControlsBoardPreview() {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val hazeState = rememberHazeState()
    TabListBottomControls(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        pagerState = pagerState,
        hazeState = hazeState,
        isRefreshing = false,
        refreshProgress = null,
        onCreateTabClick = {},
        onRefreshClick = {},
        onCancelRefreshClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun TabListBottomControlsThreadPreview() {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 2 })
    val hazeState = rememberHazeState()
    TabListBottomControls(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        pagerState = pagerState,
        hazeState = hazeState,
        isRefreshing = false,
        refreshProgress = null,
        onCreateTabClick = {},
        onRefreshClick = {},
        onCancelRefreshClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun TabListBottomControlsRefreshingPreview() {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 2 })
    val hazeState = rememberHazeState()
    TabListBottomControls(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        pagerState = pagerState,
        hazeState = hazeState,
        isRefreshing = true,
        refreshProgress = ThreadTabRefreshProgress(
            completedCount = 3,
            totalCount = 8,
        ),
        onCreateTabClick = {},
        onRefreshClick = {},
        onCancelRefreshClick = {},
    )
}
