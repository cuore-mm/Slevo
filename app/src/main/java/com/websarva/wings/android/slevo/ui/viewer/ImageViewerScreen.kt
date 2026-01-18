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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
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
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.websarva.wings.android.slevo.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import kotlin.math.abs

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
        val thumbnailItemSizePx = remember(thumbnailWidth) { mutableFloatStateOf(0f) }
        val thumbnailViewportWidthPx = remember { mutableIntStateOf(0) }
        var isThumbnailAutoScrolling by remember { mutableStateOf(false) }

        // --- Zoom reset ---
        LaunchedEffect(pagerState.currentPage) {
            val currentPage = pagerState.currentPage
            if (currentPage != lastPage) {
                // Guard: 別ページへ移動したときのみ前ページのズームを解除する。
                zoomableStates.getOrNull(lastPage)?.value?.resetZoom()
                lastPage = currentPage
            }
        }

        // --- Thumbnail scroll -> pager sync ---
        LaunchedEffect(thumbnailListState, pagerState) {
            snapshotFlow { thumbnailListState.layoutInfo }
                .map { layoutInfo -> findCenteredThumbnailIndex(layoutInfo) }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { centeredIndex ->
                    if (isThumbnailAutoScrolling) {
                        // Guard: 自動スクロール中はサムネイル同期を停止する。
                        return@collect
                    }
                    if (!thumbnailListState.isScrollInProgress) {
                        // Guard: ユーザー操作がないときは表示画像の更新を行わない。
                        return@collect
                    }
                    if (pagerState.isScrollInProgress) {
                        // Guard: ページャ操作中は競合を避けるため同期しない。
                        return@collect
                    }
                    if (centeredIndex != pagerState.currentPage) {
                        pagerState.scrollToPage(centeredIndex)
                    }
                }
        }

        // --- Pager -> thumbnail scroll sync ---
        LaunchedEffect(
            pagerState.currentPage,
            thumbnailViewportWidthPx.intValue,
            thumbnailItemSizePx.floatValue,
        ) {
            if (thumbnailViewportWidthPx.intValue == 0) {
                // Guard: 表示領域サイズが確定するまでスクロールを待機する。
                return@LaunchedEffect
            }
            if (thumbnailListState.isScrollInProgress) {
                // Guard: サムネイルの手動スクロールを優先する。
                return@LaunchedEffect
            }
            isThumbnailAutoScrolling = true
            try {
                thumbnailListState.animateScrollToItem(
                    index = pagerState.currentPage,
                    scrollOffset = 0,
                )
            } finally {
                // Guard: アニメーション終了後に同期停止を解除する。
                isThumbnailAutoScrolling = false
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    ImageViewerThumbnailBar(
                        imageUrls = imageUrls,
                        pagerState = pagerState,
                        isBarsVisible = isBarsVisible,
                        thumbnailListState = thumbnailListState,
                        modifier = Modifier.fillMaxWidth(),
                        thumbnailWidth = thumbnailWidth,
                        thumbnailHeight = thumbnailHeight,
                        thumbnailShape = thumbnailShape,
                        thumbnailSpacing = thumbnailSpacing,
                        selectedThumbnailScale = selectedThumbnailScale,
                        barBackgroundColor = barBackgroundColor,
                        thumbnailItemSizePx = thumbnailItemSizePx,
                        thumbnailViewportWidthPx = thumbnailViewportWidthPx,
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
    pagerState: PagerState,
    zoomableStates: MutableList<MutableState<ZoomableState?>>,
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
                onClick = { _ -> onToggleBars() },
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
    pagerState: PagerState,
    isBarsVisible: Boolean,
    thumbnailListState: LazyListState,
    modifier: Modifier,
    thumbnailWidth: Dp,
    thumbnailHeight: Dp,
    thumbnailShape: RoundedCornerShape,
    thumbnailSpacing: Dp,
    selectedThumbnailScale: Float,
    barBackgroundColor: Color,
    thumbnailItemSizePx: MutableFloatState,
    thumbnailViewportWidthPx: MutableIntState,
    onThumbnailClick: (Int) -> Unit,
) {
    val density = LocalDensity.current

    // --- Layout ---
    AnimatedVisibility(
        visible = isBarsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(barBackgroundColor),
        ) {
            // --- Padding ---
            val fallbackItemWidthPx = with(density) {
                (thumbnailWidth * selectedThumbnailScale).toPx()
            }
            val itemWidthPx = if (thumbnailItemSizePx.floatValue > 0f) {
                thumbnailItemSizePx.floatValue
            } else {
                fallbackItemWidthPx
            }
            val horizontalPaddingPx =
                ((thumbnailViewportWidthPx.intValue - itemWidthPx) / 2f).coerceAtLeast(0f)
            val horizontalPadding = with(density) { horizontalPaddingPx.toDp() }

            // --- Thumbnails ---
            LazyRow(
                state = thumbnailListState,
                contentPadding = PaddingValues(
                    horizontal = horizontalPadding,
                    vertical = 8.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(thumbnailSpacing),
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        thumbnailViewportWidthPx.intValue = size.width
                    },
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

/**
 * サムネイル一覧の表示領域中心に最も近いアイテムのインデックスを返す。
 *
 * 表示アイテムが存在しない場合は null を返す。
 */
private fun findCenteredThumbnailIndex(
    layoutInfo: LazyListLayoutInfo,
): Int? {
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) {
        // Guard: まだ表示対象がない場合は判定できない。
        return null
    }
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    return visibleItems.minByOrNull { item ->
        val itemCenter = item.offset + item.size / 2
        abs(itemCenter - viewportCenter)
    }?.index
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
