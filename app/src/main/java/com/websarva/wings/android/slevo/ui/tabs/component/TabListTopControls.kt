package com.websarva.wings.android.slevo.ui.tabs.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.SearchInputField
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.roundToInt

/**
 * タブ一覧上部の検索領域を提供する。
 *
 * 通常時は右上に検索ボタンとその他ボタン、検索モード時は上部に検索バーを表示する。
 * いずれの場合も同じ固定高さの full-width haze 領域を持つ。
 */
@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TabListTopControls(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    isSearchMode: Boolean,
    isSelectionMode: Boolean = false,
    selectedTabCount: Int = 0,
    searchInputValue: TextFieldValue,
    searchFocusRequestId: Long?,
    onSearchClick: () -> Unit,
    onMoreClick: (IntRect) -> Unit,
    onSearchInputChange: (TextFieldValue) -> Unit,
    onSearchFocusRequestConsumed: () -> Unit,
    onCloseSearch: () -> Unit,
    onBackFromSelection: () -> Unit = {},
) {
    val tapGuardInteractionSource = remember { MutableInteractionSource() }
    val moreButtonBounds = remember { mutableStateOf<IntRect?>(null) }

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
            Box(modifier = Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    modifier = Modifier.align(Alignment.CenterStart),
                    visible = isSelectionMode,
                    enter = fadeIn(animationSpec = visibilityAnimationSpec),
                    exit = fadeOut(animationSpec = visibilityAnimationSpec),
                ) {
                    TabActionButton(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        onClick = onBackFromSelection,
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TabActionButton(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.search),
                        onClick = onSearchClick,
                    )
                    MoreButton(
                        moreButtonBounds = moreButtonBounds,
                        enabled = !isSelectionMode || selectedTabCount > 0,
                        onMoreClick = onMoreClick,
                    )
                }
            }
        }
    }
}

/** その他ボタンのboundsを記録し、通常／選択メニューのアンカーとして利用する。 */
@Composable
private fun MoreButton(
    moreButtonBounds: androidx.compose.runtime.MutableState<IntRect?>,
    enabled: Boolean,
    onMoreClick: (IntRect) -> Unit,
) {
    Box(
        modifier = Modifier.onGloballyPositioned { coordinates ->
            val bounds = coordinates.boundsInWindow()
            moreButtonBounds.value = IntRect(
                left = bounds.left.roundToInt(),
                top = bounds.top.roundToInt(),
                right = bounds.right.roundToInt(),
                bottom = bounds.bottom.roundToInt(),
            )
        }
    ) {
        TabActionButton(
            imageVector = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.more),
            enabled = enabled,
            onClick = {
                moreButtonBounds.value?.let(onMoreClick)
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TabListTopControlsDefaultPreview() {
    TabListTopControls(
        hazeState = HazeState(),
        isSearchMode = false,
        isSelectionMode = false,
        selectedTabCount = 0,
        searchInputValue = TextFieldValue(""),
        searchFocusRequestId = null,
        onSearchClick = {},
        onMoreClick = {},
        onSearchInputChange = {},
        onSearchFocusRequestConsumed = {},
        onCloseSearch = {},
        onBackFromSelection = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun TabListTopControlsSearchPreview() {
    TabListTopControls(
        hazeState = HazeState(),
        isSearchMode = true,
        isSelectionMode = false,
        selectedTabCount = 0,
        searchInputValue = TextFieldValue(""),
        searchFocusRequestId = 1L,
        onSearchClick = {},
        onMoreClick = {},
        onSearchInputChange = {},
        onSearchFocusRequestConsumed = {},
        onCloseSearch = {},
        onBackFromSelection = {},
    )
}
