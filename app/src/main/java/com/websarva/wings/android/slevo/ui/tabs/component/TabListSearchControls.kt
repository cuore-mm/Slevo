package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.FeedbackTooltipIconButton
import com.websarva.wings.android.slevo.ui.common.SearchInputField
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * タブ一覧の上部に表示する検索バーを提供する。
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TabListSearchTopBar(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 0.45f,
                    endIntensity = 0f,
                    preferPerformance = true
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Card {
            SearchInputField(
                searchQuery = searchQuery,
                onQueryChange = onQueryChange,
                onCloseSearch = onCloseSearch,
                placeholderResId = R.string.search_board_hint,
            )
        }
    }
}

/**
 * タブ一覧通常表示時の右上検索ボタンを提供する。
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TabListSearchButton(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 0.45f,
                    endIntensity = 0f,
                    preferPerformance = true
                )
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        FeedbackTooltipIconButton(
            tooltipText = stringResource(R.string.search),
            onClick = onClick,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search),
            )
        }
    }
}
