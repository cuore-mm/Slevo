package com.websarva.wings.android.slevo.ui.viewer

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import coil3.size.Precision
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.transition.ImageSharedTransitionKeyFactory
import com.websarva.wings.android.slevo.ui.util.ImageActionReuseRegistry
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressIndicator
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressRegistry
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
    failedImageUrls: Set<String>,
    onImageLoadError: (String) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onImageRetry: (String) -> Unit,
) {
    // --- Pager ---
    val context = LocalContext.current
    val density = LocalDensity.current
    val loadProgressByUrl by ImageLoadProgressRegistry.progressByUrl.collectAsState()
    val isLoadingByPage = remember(imageUrls) {
        mutableStateMapOf<Int, Boolean>().apply {
            imageUrls.indices.forEach { index ->
                this[index] = false
            }
        }
    }
    val retryNonceByPage = remember(imageUrls) {
        mutableStateMapOf<Int, Int>().apply {
            imageUrls.indices.forEach { index ->
                this[index] = 0
            }
        }
    }
    HorizontalPager(
        state = pagerState,
        pageSpacing = 8.dp,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        // --- Zoom state ---
        val imageUrl = imageUrls[page]
        val isError = imageUrl in failedImageUrls
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
        val retryNonce = retryNonceByPage[page] ?: 0
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
        BoxWithConstraints(
            modifier = imageModifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Guard: 初回表示で低解像度が固定化しないよう、現在の表示領域サイズをキーにする。
            val viewportWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
            val viewportHeightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
            val imageRequest = remember(
                imageUrl,
                context,
                viewportWidthPx,
                viewportHeightPx,
                retryNonce,
            ) {
                ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(viewportWidthPx, viewportHeightPx)
                    .precision(Precision.EXACT)
                    .memoryCacheKey("$imageUrl#viewer-$retryNonce")
                    .listener(
                        onStart = { _ ->
                            isLoadingByPage[page] = true
                        },
                        onSuccess = { _, result ->
                            isLoadingByPage[page] = false
                            onImageLoadSuccess(imageUrl)
                            result.diskCacheKey?.let { key ->
                                ImageActionReuseRegistry.register(
                                    url = imageUrl,
                                    diskCacheKey = key,
                                    extension = imageUrl.substringAfterLast('.', ""),
                                )
                            }
                        },
                        onError = { _, _ ->
                            isLoadingByPage[page] = false
                            onImageLoadError(imageUrl)
                        },
                        onCancel = { _ ->
                            isLoadingByPage[page] = false
                        }
                    )
                    .build()
            }
            if (!isError) {
                ZoomableAsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    state = imageState,
                    modifier = Modifier.fillMaxSize(),
                    onClick = { _ -> onToggleBars() },
                    onDoubleClick = DoubleClickToZoomListener.cycle(
                        maxZoomFactor = 2f,
                    ),
                )
            }
            if (isLoadingByPage[page] == true) {
                ImageLoadProgressIndicator(
                    progressState = loadProgressByUrl[imageUrl],
                    indicatorSize = 48.dp,
                )
            }
            if (isErrorByPage[page] == true) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = {
                            // Guard: 失敗ページのみ再読み込みトリガーを進める。
                            onImageRetry(imageUrl)
                            retryNonceByPage[page] = retryNonce + 1
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
        }
    }
}
