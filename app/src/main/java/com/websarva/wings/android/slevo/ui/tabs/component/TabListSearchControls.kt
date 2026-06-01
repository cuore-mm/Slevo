package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
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
 * タブ一覧上部の検索領域を提供する。
 *
 * 通常時は右上に検索ボタン、検索モード時は上部に検索バーを表示する。
 * いずれの場合も同じ固定高さの full-width haze 領域を持つ。
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TabListTopSearchArea(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    isSearchMode: Boolean,
    searchQuery: String,
    onSearchClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TabListTopSearchDefaults.height)
            .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 0.45f,
                    endIntensity = 0f,
                    preferPerformance = true,
                )
            }
            .padding(horizontal = TabListTopSearchDefaults.horizontalPadding, vertical = TabListTopSearchDefaults.verticalPadding),
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (isSearchMode) {
            Card {
                SearchInputField(
                    searchQuery = searchQuery,
                    onQueryChange = onQueryChange,
                    onCloseSearch = onCloseSearch,
                    placeholderResId = R.string.search_board_hint,
                )
            }
        } else {
            FeedbackTooltipIconButton(
                tooltipText = stringResource(R.string.search),
                onClick = onSearchClick,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search),
                )
            }
        }
    }
}

/**
 * 上部検索領域のデフォルト寸法を保持する。
 */
internal object TabListTopSearchDefaults {
    val height: Dp = 72.dp
    val horizontalPadding: Dp = 12.dp
    val verticalPadding: Dp = 8.dp
}
