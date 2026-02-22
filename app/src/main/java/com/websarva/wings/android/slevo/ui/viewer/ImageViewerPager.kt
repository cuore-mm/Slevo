package com.websarva.wings.android.slevo.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.request.ImageRequest
import coil3.size.Precision
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.transition.ImageSharedTransitionKeyFactory
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.util.ImageActionReuseRegistry
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressIndicator
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressRegistry
import com.websarva.wings.android.slevo.ui.util.toImageLoadFailureType
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
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType>,
    onImageLoadStart: (String) -> Unit,
    onImageLoadError: (String, ImageLoadFailureType) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onImageLoadCancel: (String) -> Unit,
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
        val failureType = imageLoadFailureByUrl[imageUrl]
        val isError = failureType != null
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
                    .memoryCacheKey(
                        "$imageUrl#viewer-${viewportWidthPx}x${viewportHeightPx}-$retryNonce"
                    )
                    .listener(
                        onStart = { _ ->
                            isLoadingByPage[page] = true
                            onImageLoadStart(imageUrl)
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
                        onError = { _, result ->
                            isLoadingByPage[page] = false
                            onImageLoadError(
                                imageUrl,
                                result.throwable.toImageLoadFailureType(),
                            )
                        },
                        onCancel = { _ ->
                            isLoadingByPage[page] = false
                            onImageLoadCancel(imageUrl)
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
            if (isError) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    when (failureType) {
                        ImageLoadFailureType.HTTP_404 -> {
                            FailureMessage(
                                code = "404",
                                message = stringResource(R.string.image_not_found),
                            )
                        }

                        ImageLoadFailureType.HTTP_410 -> {
                            FailureMessage(
                                code = "410",
                                message = stringResource(R.string.image_deleted),
                            )
                        }

                        else -> {
                            ImageRetryButton(
                                onRetry = {
                                    onImageRetry(imageUrl)
                                    retryNonceByPage[page] = retryNonce + 1
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FailureMessage(
    code: String,
    message: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = code,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun ImageRetryButton(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val iconScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = 600f,
        ),
        label = "retryIconScale",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledTonalIconButton(
            onClick = onRetry,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            interactionSource = interactionSource,
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.retry),
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.retry),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Preview(showBackground = true)
@Composable
private fun ImageViewerPagerPreview() {
    ImageViewerPagerPreviewWrapper(
        imageUrls = listOf(
            "https://placehold.jp/150x150.png",
            "https://placehold.jp/150x150.png",
            "https://placehold.jp/150x150.png"
        )
    )
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Preview(showBackground = true)
@Composable
private fun ImageViewerPagerPreview_Error() {
    val imageUrls = listOf("https://placehold.jp/150x150.png")
    ImageViewerPagerPreviewWrapper(
        imageUrls = imageUrls,
        imageLoadFailureByUrl = mapOf(imageUrls[0] to ImageLoadFailureType.UNKNOWN)
    )
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ImageViewerPagerPreviewWrapper(
    imageUrls: List<String>,
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType> = emptyMap()
) {
    SlevoTheme {
        SharedTransitionLayout {
            val pagerState = rememberPagerState(pageCount = { imageUrls.size })
            val zoomableStates = remember {
                mutableStateListOf<MutableState<ZoomableState?>>().apply {
                    repeat(imageUrls.size) { add(mutableStateOf(null)) }
                }
            }
            var visible by remember { mutableStateOf(true) }
            AnimatedVisibility(visible = visible, label = "preview") {
                ImageViewerPager(
                    imageUrls = imageUrls,
                    transitionNamespace = "preview",
                    pagerState = pagerState,
                    zoomableStates = zoomableStates,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    onToggleBars = {},
                    imageLoadFailureByUrl = imageLoadFailureByUrl,
                    onImageLoadError = { _, _ -> },
                    onImageLoadStart = {},
                    onImageLoadSuccess = {},
                    onImageLoadCancel = {},
                    onImageRetry = {}
                )
            }
        }
    }
}
