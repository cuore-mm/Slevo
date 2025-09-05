package com.websarva.wings.android.slevo.ui.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.BottomAppBarScrollBehavior
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberBottomAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberBottomBarShowOnBottomBehavior(
    listState: LazyListState,
    showThreshold: Dp = 4.dp,
    leaveThreshold: Dp = 48.dp,
): BottomAppBarScrollBehavior {
    val density = LocalDensity.current
    val showThPx = with(density) { showThreshold.toPx() }
    val leaveThPx = with(density) { leaveThreshold.toPx() }

    val barState = rememberBottomAppBarState()
    var atBottomSticky by remember { mutableStateOf(false) }

    val bottomInfo by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            if (last == null || info.totalItemsCount == 0) return@derivedStateOf 99999f
            val viewportEnd = info.viewportEndOffset
            val lastEnd = last.offset + last.size
            (viewportEnd - lastEnd).toFloat()
        }
    }
    val isLastItemVisible by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastIndex = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastIndex >= info.totalItemsCount - 1 && info.totalItemsCount > 0
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { isLastItemVisible to bottomInfo }
            .distinctUntilChanged()
            .collect { (lastVisible, gap) ->
                if (lastVisible && gap <= showThPx) {
                    if (!atBottomSticky) {
                        atBottomSticky = true
                        barState.heightOffset = 0f
                    }
                } else if (gap > leaveThPx) {
                    atBottomSticky = false
                }
            }
    }

    val flingSpec = rememberSplineBasedDecay<Float>()
    val snapSpec = remember { spring<Float>(stiffness = Spring.StiffnessMediumLow) }

    return BottomAppBarDefaults.exitAlwaysScrollBehavior(
        state = barState,
        canScroll = { !atBottomSticky },
        snapAnimationSpec = snapSpec,
        flingAnimationSpec = flingSpec,
    )
}

