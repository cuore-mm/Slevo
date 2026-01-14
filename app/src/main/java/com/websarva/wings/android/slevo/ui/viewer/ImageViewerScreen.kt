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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
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
    // --- Constants ---
    val barBackgroundColor = Color.Black.copy(alpha = 0.3f)
    val thumbnailWidth = 40.dp
    val thumbnailHeight = 56.dp
    val thumbnailShape = RoundedCornerShape(10.dp)
    val thumbnailSpacing = 8.dp
    val selectedThumbnailScale = 1.1f

    // --- UI state ---
    var isBarsVisible by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        topBar = {
            ImageViewerTopBar(
                isVisible = isBarsVisible,
                barBackgroundColor = barBackgroundColor,
                onNavigateUp = onNavigateUp,
            )
        },
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0)
    ) { _ ->
        if (imageUrls.isEmpty()) {
            // Guard: URLリストが空の場合は表示処理をスキップする。
            return@Scaffold
        }
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
        val thumbnailItemSizePx = remember(thumbnailWidth) { mutableFloatStateOf(0f) }

        // --- Zoom reset ---
        LaunchedEffect(pagerState.currentPage) {
            val currentPage = pagerState.currentPage
            if (currentPage != lastPage) {
                // Guard: 別ページへ移動したときのみ前ページのズームを解除する。
                zoomableStates.getOrNull(lastPage)?.value?.resetZoom()
                lastPage = currentPage
            }
        }

        val isPreview = LocalInspectionMode.current
        val boxModifier = Modifier
            .fillMaxSize()
            .background(if (isPreview) Color.White else Color.Black)

        Box(
            modifier = boxModifier
        ) {
            ImageViewerPager(
                imageUrls = imageUrls,
                pagerState = pagerState,
                zoomableStates = zoomableStates,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onToggleBars = { isBarsVisible = !isBarsVisible },
            )

            if (imageUrls.size > 1) {
                ImageViewerThumbnailBar(
                    imageUrls = imageUrls,
                    pagerState = pagerState,
                    isBarsVisible = isBarsVisible,
                    thumbnailListState = thumbnailListState,
                    thumbnailWidth = thumbnailWidth,
                    thumbnailHeight = thumbnailHeight,
                    thumbnailShape = thumbnailShape,
                    thumbnailSpacing = thumbnailSpacing,
                    selectedThumbnailScale = selectedThumbnailScale,
                    barBackgroundColor = barBackgroundColor,
                    thumbnailItemSizePx = thumbnailItemSizePx,
                    lastCenteredIndex = lastCenteredIndex,
                    onCenteredIndexChange = { lastCenteredIndex = it },
                    onThumbnailClick = { index ->
                        if (index != pagerState.currentPage) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * 画像ビューアのトップバーを表示する。
 *
 * ナビゲーションアイコンのみを持ち、表示可否は呼び出し元で制御する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageViewerTopBar(
    isVisible: Boolean,
    barBackgroundColor: Color,
    onNavigateUp: () -> Unit,
) {
    AnimatedVisibility(
        visible = isVisible,
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
                containerColor = barBackgroundColor
            )
        )
    }
}

/**
 * 画像スワイプと拡大縮小を担うページャを描画する。
 *
 * ページごとのズーム状態は呼び出し元のリストに保存する。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ImageViewerPager(
    imageUrls: List<String>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    zoomableStates: MutableList<androidx.compose.runtime.MutableState<ZoomableState?>>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onToggleBars: () -> Unit,
) {
    // --- Pager ---
    HorizontalPager(
        state = pagerState,
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
                onClick = onToggleBars,
                onDoubleClick = DoubleClickToZoomListener.cycle(
                    maxZoomFactor = 2f,
                ),
            )
        }
    }
}

/**
 * 同一レス内のサムネイル一覧を表示し、選択中の画像を中央に寄せる。
 *
 * 選択中サムネイルはサイズを拡大し、タップで表示画像を切り替える。
 */
@Composable
private fun ImageViewerThumbnailBar(
    imageUrls: List<String>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isBarsVisible: Boolean,
    thumbnailListState: androidx.compose.foundation.lazy.LazyListState,
    thumbnailWidth: androidx.compose.ui.unit.Dp,
    thumbnailHeight: androidx.compose.ui.unit.Dp,
    thumbnailShape: RoundedCornerShape,
    thumbnailSpacing: androidx.compose.ui.unit.Dp,
    selectedThumbnailScale: Float,
    barBackgroundColor: Color,
    thumbnailItemSizePx: androidx.compose.runtime.MutableFloatState,
    lastCenteredIndex: Int,
    onCenteredIndexChange: (Int) -> Unit,
    onThumbnailClick: (Int) -> Unit,
) {
    val maxThumbnailWidth = thumbnailWidth * selectedThumbnailScale

    // --- Centering ---
    LaunchedEffect(
        pagerState.currentPage,
        thumbnailListState.layoutInfo.viewportSize,
        thumbnailItemSizePx.floatValue,
    ) {
        val currentPage = pagerState.currentPage
        val viewportWidth = thumbnailListState.layoutInfo.viewportSize.width
        if (viewportWidth == 0 || currentPage == lastCenteredIndex) {
            // Guard: 画面幅が取得できない間はスクロールを待機する。
            return@LaunchedEffect
        }
        val itemSizePx = thumbnailItemSizePx.floatValue
        if (itemSizePx == 0f) {
            // Guard: 選択中サムネイルのサイズ取得後に中央寄せを行う。
            return@LaunchedEffect
        }
        val centerOffset = (itemSizePx / 2f - viewportWidth / 2f).roundToInt()
        thumbnailListState.animateScrollToItem(
            index = currentPage,
            scrollOffset = centerOffset,
        )
        onCenteredIndexChange(currentPage)
    }

    // --- Layout ---
    AnimatedVisibility(
        visible = isBarsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(barBackgroundColor),
        ) {
            val horizontalPadding = ((maxWidth - maxThumbnailWidth) / 2f).coerceAtLeast(0.dp)
            // --- Thumbnails ---
            LazyRow(
                state = thumbnailListState,
                contentPadding = PaddingValues(
                    horizontal = horizontalPadding,
                    vertical = 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(thumbnailSpacing),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(imageUrls.size) { index ->
                    val isSelected = index == pagerState.currentPage
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) selectedThumbnailScale else 1f,
                        label = "thumbnailScale",
                    )
                    SubcomposeAsyncImage(
                        model = imageUrls[index],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .size(
                                width = thumbnailWidth * scale,
                                height = thumbnailHeight * scale,
                            )
                            .clip(thumbnailShape)
                            .onSizeChanged { size ->
                                if (isSelected) {
                                    thumbnailItemSizePx.floatValue = size.width.toFloat()
                                }
                            }
                            .clickable { onThumbnailClick(index) }
                            .background(Color.DarkGray),
                    )
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
