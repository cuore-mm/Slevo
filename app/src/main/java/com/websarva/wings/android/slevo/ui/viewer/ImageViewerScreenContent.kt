package com.websarva.wings.android.slevo.ui.viewer

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.thread.dialog.NgDialogRoute
import com.websarva.wings.android.slevo.ui.thread.sheet.ImageMenuAction
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import me.saket.telephoto.zoomable.ZoomableState
import com.websarva.wings.android.slevo.ui.common.resolveImageActionMenuState

/**
 * 画像ビューア画面の表示構築を担うコンテンツホスト。
 *
 * Screen から UI 構築責務を分離し、状態に応じた表示だけを担当する。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun ImageViewerScreenContent(
    imageUrls: List<String>,
    transitionNamespace: String,
    pagerState: PagerState,
    zoomableStates: MutableList<MutableState<ZoomableState?>>,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    thumbnailListState: LazyListState,
    thumbnailViewportWidthPx: MutableIntState,
    uiState: ImageViewerUiState,
    isBarsVisible: Boolean,
    viewerBackgroundColor: Color,
    viewerContentColor: Color,
    barBackgroundColor: Color,
    tooltipBackgroundColor: Color,
    hazeState: HazeState,
    barExitDurationMillis: Int,
    onNavigateUp: () -> Unit,
    onRequestSaveCurrent: () -> Unit,
    onShareCurrent: () -> Unit,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onMenuActionClick: (ImageMenuAction) -> Unit,
    onToggleBars: () -> Unit,
    onThumbnailClick: (Int) -> Unit,
    onDismissNgDialog: () -> Unit,
    onViewerImageLoadStart: (String) -> Unit,
    onViewerImageLoadError: (String, ImageLoadFailureType) -> Unit,
    onViewerImageLoadSuccess: (String) -> Unit,
    onViewerImageLoadCancel: (String) -> Unit,
    onViewerImageRetry: (String) -> Unit,
    onThumbnailImageLoadError: (String, ImageLoadFailureType) -> Unit,
    onThumbnailImageLoadSuccess: (String) -> Unit,
) {
    // --- Root container ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
    ) {
        // --- Screen scaffold ---
        Scaffold(
            topBar = {
                val menuState by remember(
                    imageUrls,
                    pagerState.currentPage,
                    uiState.viewerImageLoadFailureByUrl,
                    uiState.viewerImageLoadingUrls,
                ) {
                    derivedStateOf {
                        val currentUrl = imageUrls.getOrNull(pagerState.currentPage).orEmpty()
                        resolveImageActionMenuState(
                            imageUrl = currentUrl,
                            imageUrls = imageUrls,
                            imageLoadFailureByUrl = uiState.viewerImageLoadFailureByUrl,
                            loadingImageUrls = uiState.viewerImageLoadingUrls,
                        )
                    }
                }
                ImageViewerTopBar(
                    isVisible = isBarsVisible,
                    isMenuExpanded = uiState.isTopBarMenuExpanded,
                    imageCount = imageUrls.size,
                    barBackgroundColor = barBackgroundColor,
                    foregroundColor = viewerContentColor,
                    tooltipBackgroundColor = tooltipBackgroundColor,
                    hazeState = hazeState,
                    barExitDurationMillis = barExitDurationMillis,
                    onNavigateUp = onNavigateUp,
                    onSaveClick = onRequestSaveCurrent,
                    onShareClick = onShareCurrent,
                    onMoreClick = onToggleMenu,
                    onDismissMenu = onDismissMenu,
                    onMenuActionClick = onMenuActionClick,
                    menuState = menuState,
                )
            },
            containerColor = viewerBackgroundColor,
            contentWindowInsets = WindowInsets(0),
        ) { _ ->
            // --- Main image and overlays ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(viewerBackgroundColor)
            ) {
                ImageViewerPager(
                    imageUrls = imageUrls,
                    transitionNamespace = transitionNamespace,
                    pagerState = pagerState,
                    zoomableStates = zoomableStates,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onToggleBars = onToggleBars,
                    imageLoadFailureByUrl = uiState.viewerImageLoadFailureByUrl,
                    onImageLoadStart = onViewerImageLoadStart,
                    onImageLoadError = onViewerImageLoadError,
                    onImageLoadSuccess = onViewerImageLoadSuccess,
                    onImageLoadCancel = onViewerImageLoadCancel,
                    onImageRetry = onViewerImageRetry,
                )
                if (isBarsVisible) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(barBackgroundColor)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(barBackgroundColor)
                    )
                }

                // --- Thumbnail bar ---
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
                            barBackgroundColor = barBackgroundColor,
                            barExitDurationMillis = barExitDurationMillis,
                            thumbnailViewportWidthPx = thumbnailViewportWidthPx,
                            onThumbnailClick = onThumbnailClick,
                            imageLoadFailureByUrl = uiState.thumbnailImageLoadFailureByUrl,
                            thumbnailRetryNonceByUrl = uiState.thumbnailRetryNonceByUrl,
                            onImageLoadError = onThumbnailImageLoadError,
                            onImageLoadSuccess = onThumbnailImageLoadSuccess,
                        )
                    }
                }

                // --- Dialog host ---
                if (uiState.showImageNgDialog) {
                    uiState.imageNgTargetUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        NgDialogRoute(
                            text = url,
                            type = NgType.WORD,
                            onDismiss = onDismissNgDialog,
                        )
                    }
                }
            }
        }
    }
}
