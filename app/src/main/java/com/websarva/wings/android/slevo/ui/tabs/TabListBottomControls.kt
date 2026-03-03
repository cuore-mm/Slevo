package com.websarva.wings.android.slevo.ui.tabs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import kotlinx.coroutines.launch

/**
 * タブ一覧の下部操作群で利用するデフォルト寸法を保持する。
 */
internal object TabListBottomControlsDefaults {
    val listBottomPadding: Dp = 64.dp
}

/**
 * 下部の2段操作群を表示し、板/スレ切替とタブ操作を提供する。
 */
@Composable
internal fun TabListBottomControls(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    onCreateTabClick: () -> Unit,
    onRefreshClick: () -> Unit,
) {
    val isBoardPage = pagerState.currentPage == 0
    val coroutineScope = rememberCoroutineScope()

    // --- Floating controls layout ---
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                TabListSegmentRow(
                    modifier = Modifier.fillMaxWidth(),
                    selectedIndex = pagerState.currentPage,
                    onSelect = { index ->
                        if (pagerState.currentPage != index) {
                            // 切り替え時のみアニメーションで遷移する。
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = isBoardPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tabBottomActionButtons"
            ) { boardPage ->
                if (boardPage) {
                    TabActionButton(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.open_url),
                        onClick = onCreateTabClick,
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TabActionButton(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.open_url),
                            onClick = onCreateTabClick,
                        )
                        TabActionButton(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            onClick = onRefreshClick,
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
    IconButton(
        modifier = Modifier
            .size(40.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = CircleShape,
            ),
        onClick = onClick,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * 板/スレ切替のセグメントボタンを描画する。
 */
@Composable
private fun TabListSegmentRow(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    // --- Segmented options ---
    val options = listOf(
        stringResource(R.string.board),
        stringResource(R.string.thread),
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = selectedIndex == index
            Button(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                ),
                onClick = { onSelect(index) },
            ) {
                Text(text = label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TabListBottomControlsBoardPreview() {
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    TabListBottomControls(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        pagerState = pagerState,
        onCreateTabClick = {},
        onRefreshClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun TabListBottomControlsThreadPreview() {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 2 })
    TabListBottomControls(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        pagerState = pagerState,
        onCreateTabClick = {},
        onRefreshClick = {},
    )
}
