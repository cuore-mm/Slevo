package com.websarva.wings.android.slevo.ui.viewer

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.websarva.wings.android.slevo.R
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import kotlin.math.roundToInt

/**
 * レス内画像の一覧をページング表示する画像ビューア。
 *
 * タップした画像を初期ページとして表示し、左右スワイプで同一レス内の画像を切り替える。
 */
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ImageViewerScreen(
    imageUrls: List<String>,
    initialIndex: Int,
    onNavigateUp: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // --- UI state ---
    var isBarsVisible by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        topBar = {
            // --- Top bar ---
            AnimatedVisibility(
                visible = isBarsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                                contentDescription = stringResource(R.string.back),
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.3f)
                    )
                )
            }
        },
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0)
    ) { _ ->
        if (imageUrls.isEmpty()) {
            // Guard: URLリストが空の場合は表示処理をスキップする。
            return@Scaffold
        }
        val thumbnailSize = 56.dp
        val thumbnailSpacing = 8.dp
        val safeInitialIndex = initialIndex.coerceIn(0, imageUrls.lastIndex)
        val pagerState = rememberPagerState(
            initialPage = safeInitialIndex,
            pageCount = { imageUrls.size },
        )
        val thumbnailListState =
            rememberLazyListState(initialFirstVisibleItemIndex = safeInitialIndex)
        val coroutineScope = rememberCoroutineScope()
        val zoomableStates = remember(imageUrls) {
            MutableList(imageUrls.size) { mutableStateOf<ZoomableState?>(null) }
        }
        var lastPage by rememberSaveable { mutableIntStateOf(safeInitialIndex) }
        var lastCenteredIndex by rememberSaveable { mutableIntStateOf(safeInitialIndex) }
        val thumbnailItemSizePx = remember(thumbnailSize) { mutableFloatStateOf(0f) }

        // --- Zoom reset ---
        LaunchedEffect(pagerState.currentPage) {
            val currentPage = pagerState.currentPage
            if (currentPage != lastPage) {
                zoomableStates.getOrNull(lastPage)?.value?.resetZoom()
                lastPage = currentPage
            }
        }
        LaunchedEffect(pagerState.currentPage, thumbnailListState.layoutInfo.viewportSize) {
            val currentPage = pagerState.currentPage
            val viewportWidth = thumbnailListState.layoutInfo.viewportSize.width
            if (viewportWidth == 0 || currentPage == lastCenteredIndex) {
                return@LaunchedEffect
            }
            val itemSizePx = thumbnailItemSizePx.floatValue
            if (itemSizePx == 0f) {
                return@LaunchedEffect
            }
            val centerOffset = (itemSizePx / 2f - viewportWidth / 2f).roundToInt()
            thumbnailListState.animateScrollToItem(
                index = currentPage,
                scrollOffset = centerOffset,
            )
            lastCenteredIndex = currentPage
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // --- Pager ---
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val imageUrl = imageUrls[page]
                val zoomableState = rememberZoomableState(
                    zoomSpec = ZoomSpec(
                        maxZoomFactor = 12f,                  // ← ここを例えば 8x や 12x に
                        overzoomEffect = OverzoomEffect.RubberBanding // 端でビヨン効果（好みで）
                    )
                )
                val imageState = rememberZoomableImageState(zoomableState)

                SideEffect {
                    if (page in zoomableStates.indices) {
                        zoomableStates[page].value = zoomableState
                    }
                }

                with(sharedTransitionScope) {
                    ZoomableAsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        state = imageState,
                        modifier = Modifier
                            .sharedElement(
                                sharedContentState = sharedTransitionScope.rememberSharedContentState(
                                    key = imageUrl
                                ),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                            .fillMaxSize(),
                        onClick = { isBarsVisible = !isBarsVisible },
                        onDoubleClick = DoubleClickToZoomListener.cycle(
                            maxZoomFactor = 2f,   // ← 好きな倍率にする（例: 2f, 3f など）
                        ),
                    )
                }
            }

            // --- Thumbnails ---
            AnimatedVisibility(
                visible = isBarsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                LazyRow(
                    state = thumbnailListState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(thumbnailSpacing),
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    items(imageUrls.size) { index ->
                        val isSelected = index == pagerState.currentPage
                        val scale by animateFloatAsState(
                            targetValue = if (isSelected) 1.1f else 1f,
                            label = "thumbnailScale",
                        )
                        SubcomposeAsyncImage(
                            model = imageUrls[index],
                            contentDescription = null,
                            modifier = Modifier
                                .size(thumbnailSize)
                                .onSizeChanged { size ->
                                    if (thumbnailItemSizePx.floatValue == 0f) {
                                        thumbnailItemSizePx.floatValue = size.width.toFloat()
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .clickable {
                                    if (index != pagerState.currentPage) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                                }
                                .background(Color.DarkGray),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
private fun ImageViewerScreenPreview() {
    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            ImageViewerScreen(
                imageUrls = listOf(
                    "https://via.placeholder.com/800x600/FF0000/FFFFFF?text=Image1",
                    "https://via.placeholder.com/800x600/00FF00/FFFFFF?text=Image2",
                    "https://via.placeholder.com/800x600/0000FF/FFFFFF?text=Image3"
                ),
                initialIndex = 0,
                onNavigateUp = {},
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this
            )
        }
    }
}
