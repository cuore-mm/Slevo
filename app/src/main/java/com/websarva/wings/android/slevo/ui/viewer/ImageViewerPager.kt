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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import com.websarva.wings.android.slevo.ui.common.transition.ImageSharedTransitionKeyFactory
import com.websarva.wings.android.slevo.ui.util.ImageActionReuseRegistry
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
    transitionNamespace: String,
    pagerState: PagerState,
    zoomableStates: MutableList<MutableState<ZoomableState?>>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onToggleBars: () -> Unit,
) {
    // --- Pager ---
    val context = LocalContext.current
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
                        key = ImageSharedTransitionKeyFactory.buildKey(
                            transitionNamespace = transitionNamespace,
                            imageUrl = imageUrl,
                            imageIndex = page,
                        )
                    ),
                    animatedVisibilityScope = animatedVisibilityScope,
                    renderInOverlayDuringTransition = false
                )
            }
        } else {
            Modifier
        }
        val imageRequest = remember(imageUrl, context) {
            ImageRequest.Builder(context)
                .data(imageUrl)
                .listener(
                    onSuccess = { _, result ->
                        result.diskCacheKey?.let { key ->
                            ImageActionReuseRegistry.register(url = imageUrl, diskCacheKey = key)
                        }
                    }
                )
                .build()
        }
        ZoomableAsyncImage(
            model = imageRequest,
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
