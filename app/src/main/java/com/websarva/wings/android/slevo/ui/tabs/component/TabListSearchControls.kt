package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import com.websarva.wings.android.slevo.R
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
@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TabListTopSearchArea(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    isSearchMode: Boolean,
    searchInputValue: TextFieldValue,
    searchFocusRequestId: Long?,
    onSearchClick: () -> Unit,
    onSearchInputChange: (TextFieldValue) -> Unit,
    onSearchFocusRequestConsumed: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    val tapGuardInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TabListLayoutDefaults.topSearchHeight)
            .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 0.3f,
                    endIntensity = 0f,
                    preferPerformance = true,
                )
            }
            .clickable(
                interactionSource = tapGuardInteractionSource,
                indication = null,
                onClick = {},
            )
            .padding(
                horizontal = TabListLayoutDefaults.controlsHorizontalPadding,
                vertical = TabListLayoutDefaults.topSearchVerticalPadding,
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        val visibilityAnimationSpec = tween<Float>(durationMillis = TabListAnimationDefaults.VISIBILITY_MILLIS)
        val slideAnimationSpec = tween<IntOffset>(durationMillis = TabListAnimationDefaults.VISIBILITY_MILLIS)

        AnimatedVisibility(
            visible = isSearchMode,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = slideAnimationSpec,
            ) + fadeIn(animationSpec = visibilityAnimationSpec),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = slideAnimationSpec,
            ) + fadeOut(animationSpec = visibilityAnimationSpec),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TabListLayoutDefaults.searchBarHeight),
                shape = MaterialTheme.shapes.extraLargeIncreased,
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = TabListLayoutDefaults.searchBarElevation),
            ) {
                SearchInputField(
                    searchInputValue = searchInputValue,
                    onSearchInputChange = onSearchInputChange,
                    onCloseSearch = onCloseSearch,
                    focusRequestId = searchFocusRequestId,
                    onFocusRequestConsumed = onSearchFocusRequestConsumed,
                    placeholderResId = R.string.search,
                )
            }
        }

        AnimatedVisibility(
            visible = !isSearchMode,
            enter = fadeIn(animationSpec = visibilityAnimationSpec),
            exit = fadeOut(animationSpec = visibilityAnimationSpec),
        ) {
            TabActionButton(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search),
                onClick = onSearchClick,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchModeTrueFalse() {
    TabListTopSearchArea(
        hazeState = HazeState(),
        isSearchMode = false,
        searchInputValue = TextFieldValue(""),
        searchFocusRequestId = null,
        onSearchClick = {},
        onSearchInputChange = {},
        onSearchFocusRequestConsumed = {},
        onCloseSearch = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun SearchModeTruePreview() {
    TabListTopSearchArea(
        hazeState = HazeState(),
        isSearchMode = true,
        searchInputValue = TextFieldValue(""),
        searchFocusRequestId = 1L,
        onSearchClick = {},
        onSearchInputChange = {},
        onSearchFocusRequestConsumed = {},
        onCloseSearch = {},
    )
}
