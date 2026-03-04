package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
internal object TabListBottomControlsDefaults {
    val listBottomPadding: Dp = 0.dp
    val hazeTopOverlap: Dp = 48.dp
}

/**
 * 下部の2段操作群を表示し、板/スレ切替とタブ操作を提供する。
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun TabListBottomControls(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    hazeState: HazeState,
    onCreateTabClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    val isBoardPage = pagerState.currentPage == 0
    val coroutineScope = rememberCoroutineScope()

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
                .padding(top = TabListBottomControlsDefaults.hazeTopOverlap)
                .clickable(
                    interactionSource = tapGuardInteractionSource,
                    indication = null,
                    onClick = {},
                )
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TabListSwitchSection(
                selectedIndex = pagerState.currentPage,
                onSelect = { index ->
                    if (pagerState.currentPage != index) {
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    }
                },
            )
            TabListActionSection(
                isBoardPage = isBoardPage,
                onCreateTabClick = onCreateTabClick,
                onRefreshClick = onRefreshClick,
            )
        }
    }
}

/**
 * 下部操作群の上段に板/スレ切替UIを表示する。
 */
@Composable
private fun TabListSwitchSection(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val roundedCornerShape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp),
            ),
        shape = roundedCornerShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        shadowElevation = 3.dp,
    ) {
        val options = listOf(
            stringResource(R.string.board),
            stringResource(R.string.thread),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = selectedIndex == index
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp),
                    shape = roundedCornerShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    onClick = { onSelect(index) },
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

/**
 * 下部操作群の下段に固定3スロットのアイコンボタン列を表示する。
 */
@Composable
private fun TabListActionSection(
    isBoardPage: Boolean,
    onCreateTabClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // left slot: future "other" button
        Spacer(modifier = Modifier.size(44.dp))
        // center slot: create
        TabActionButton(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.open_url),
            onClick = onCreateTabClick,
        )
        // right slot: refresh (thread only)
        if (isBoardPage) {
            Spacer(modifier = Modifier.size(44.dp))
        } else {
            TabActionButton(
                imageVector = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.refresh),
                onClick = onRefreshClick,
            )
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
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),

    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                modifier = Modifier.size(28.dp),
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
        onCreateTabClick = {},
        onRefreshClick = {},
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
        onCreateTabClick = {},
        onRefreshClick = {},
    )
}
