package com.websarva.wings.android.slevo.ui.viewer

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

/**
 * 画像スワイプと拡大縮小を担うページャを描画する。
 *
 * ページごとのズーム状態は呼び出し元のリストに保存する。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ImageViewerPager(
    imageUrls: List<String>,
    pagerState: PagerState,
    zoomableStates: MutableList<MutableState<ZoomableState?>>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onToggleBars: () -> Unit,
) {
    // --- Pager ---
    HorizontalPager(
        state = pagerState,
        pageSpacing = 8.dp,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        // --- Zoom state ---
        val imageUrl = imageUrls[page]
        val zoomableState = rememberZoomableState(
            zoomSpec = ZoomSpec(
                maxZoomFactor = 12f,
                overzoomEffect = OverzoomEffect.RubberBanding,
            )
        )
        val imageState = rememberZoomableImageState(zoomableState)

        SideEffect {
            if (page in zoomableStates.indices) {
                zoomableStates[page].value = zoomableState
            }
        }

        // --- Image content ---
        val imageModifier = if (page == pagerState.settledPage) {
            // Guard: 共有トランジション対象は現在表示中ページのみとする。
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    sharedContentState = sharedTransitionScope.rememberSharedContentState(
                        key = "$imageUrl#$page"
                    ),
                    animatedVisibilityScope = animatedVisibilityScope,
                    renderInOverlayDuringTransition = false
                )
            }
        } else {
            Modifier
        }
        ZoomableAsyncImage(
            model = imageUrl,
            contentDescription = null,
            state = imageState,
            modifier = imageModifier.fillMaxSize(),
            onClick = { _ -> onToggleBars() },
            onDoubleClick = DoubleClickToZoomListener.cycle(
                maxZoomFactor = 2f,
            ),
        )
    }
}
