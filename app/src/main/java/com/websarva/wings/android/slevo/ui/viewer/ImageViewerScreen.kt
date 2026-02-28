package com.websarva.wings.android.slevo.ui.viewer

import android.annotation.SuppressLint
import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.slevo.ui.common.ImageMenuActionRunner
import com.websarva.wings.android.slevo.ui.common.ImageMenuActionRunnerParams
import com.websarva.wings.android.slevo.ui.thread.sheet.ImageMenuAction
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomableState
import dev.chrisbanes.haze.rememberHazeState

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
    transitionNamespace: String,
    onNavigateUp: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // --- Constants ---
    val colorScheme = MaterialTheme.colorScheme
    val viewerBackgroundColor = colorScheme.background
    val viewerContentColor = colorScheme.onSurface
    val barBackgroundColor = colorScheme.surface.copy(alpha = 0.40f)
    val tooltipBackgroundColor = colorScheme.surfaceBright.copy(alpha = 0.80f)
    val barExitDurationMillis = 80
    val useDarkSystemBarIcons = viewerBackgroundColor.luminance() > 0.5f
    val hazeState = rememberHazeState()

    // --- UI state ---
    val isPreview = LocalInspectionMode.current
    val viewModel: ImageViewerViewModel? = if (isPreview) {
        null
    } else {
        hiltViewModel()
    }
    val uiState = viewModel?.uiState?.collectAsState()?.value ?: ImageViewerUiState()
    val isTopBarMenuExpanded = uiState.isTopBarMenuExpanded
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val activity = remember(context) { context.findActivity() }
    val coroutineScope = rememberCoroutineScope()
    var previewIsBarsVisible by rememberSaveable { mutableStateOf(true) }

    if (imageUrls.isEmpty()) {
        // Guard: URLリストが空の場合は表示処理をスキップする。
        return
    }
    val safeInitialIndex = initialIndex.coerceIn(0, imageUrls.lastIndex)
    val pagerState = rememberPagerState(
        initialPage = safeInitialIndex,
        pageCount = { imageUrls.size },
    )
    val thumbnailListState =
        rememberLazyListState(initialFirstVisibleItemIndex = safeInitialIndex)
    val zoomableStates = remember(imageUrls) {
        MutableList(imageUrls.size) { mutableStateOf<ZoomableState?>(null) }
    }
    val lastPage = rememberSaveable { mutableIntStateOf(safeInitialIndex) }
    val thumbnailViewportWidthPx = remember { mutableIntStateOf(0) }
    val isThumbnailAutoScrolling = remember { mutableStateOf(false) }
    val shouldSkipIdleSync = remember { mutableStateOf(false) }
    val hasPendingIdleCenterSync = remember { mutableStateOf(false) }
    val hasUserInteracted = remember(imageUrls, safeInitialIndex) {
        mutableStateOf(false)
    }
    val isBarsVisible = if (viewModel != null) uiState.isBarsVisible else previewIsBarsVisible
    val currentImageUrl = imageUrls.getOrNull(pagerState.currentPage).orEmpty()

    androidx.compose.runtime.LaunchedEffect(imageUrls, viewModel) {
        viewModel?.synchronizeFailedImageUrls(imageUrls)
    }

    // --- Menu actions ---
    val onImageMenuActionClick: (ImageMenuAction) -> Unit = { action ->
        ImageMenuActionRunner.run(
            action = action,
            params = ImageMenuActionRunnerParams(
                context = context,
                coroutineScope = coroutineScope,
                currentImageUrl = currentImageUrl,
                imageUrls = imageUrls,
                onOpenNgDialog = { url -> viewModel?.openImageNgDialog(url) },
                onRequestSaveSingle = { url -> viewModel?.requestImageSave(context, listOf(url)) },
                onRequestSaveAll = { urls -> viewModel?.requestImageSave(context, urls) },
                onActionHandled = { viewModel?.hideTopBarMenu() },
                onSetClipboardText = { text ->
                    val clip = ClipData.newPlainText("", text).toClipEntry()
                    clipboard.setClipEntry(clip)
                },
                onSetClipboardImageUri = { uri ->
                    val clip = ClipData.newUri(context.contentResolver, "", uri).toClipEntry()
                    clipboard.setClipEntry(clip)
                },
            ),
        )
    }

    ImageViewerScreenEffects(
        viewModel = viewModel,
        context = context,
        activity = activity,
        useDarkSystemBarIcons = useDarkSystemBarIcons,
        isBarsVisible = isBarsVisible,
        isTopBarMenuExpanded = isTopBarMenuExpanded,
        currentImageUrl = currentImageUrl,
        viewerImageLoadFailureByUrl = uiState.viewerImageLoadFailureByUrl,
        viewerImageLoadingUrls = uiState.viewerImageLoadingUrls,
        imageUrls = imageUrls,
        pagerState = pagerState,
        thumbnailListState = thumbnailListState,
        thumbnailViewportWidthPx = thumbnailViewportWidthPx,
        zoomableStates = zoomableStates,
        lastPage = lastPage,
        isThumbnailAutoScrolling = isThumbnailAutoScrolling,
        shouldSkipIdleSync = shouldSkipIdleSync,
        hasPendingIdleCenterSync = hasPendingIdleCenterSync,
        hasUserInteracted = hasUserInteracted,
    )

    ImageViewerScreenContent(
        imageUrls = imageUrls,
        transitionNamespace = transitionNamespace,
        pagerState = pagerState,
        zoomableStates = zoomableStates,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        thumbnailListState = thumbnailListState,
        thumbnailViewportWidthPx = thumbnailViewportWidthPx,
        uiState = uiState,
        isBarsVisible = isBarsVisible,
        viewerBackgroundColor = viewerBackgroundColor,
        viewerContentColor = viewerContentColor,
        barBackgroundColor = barBackgroundColor,
        tooltipBackgroundColor = tooltipBackgroundColor,
        hazeState = hazeState,
        barExitDurationMillis = barExitDurationMillis,
        onNavigateUp = onNavigateUp,
        onRequestSaveCurrent = { viewModel?.requestImageSave(context, listOf(currentImageUrl)) },
        onShareCurrent = { onImageMenuActionClick(ImageMenuAction.SHARE_IMAGE) },
        onToggleMenu = { viewModel?.toggleTopBarMenu() },
        onDismissMenu = { viewModel?.hideTopBarMenu() },
        onMenuActionClick = onImageMenuActionClick,
        onToggleBars = {
            if (viewModel != null) {
                viewModel.toggleBarsVisibility()
            } else {
                previewIsBarsVisible = !previewIsBarsVisible
            }
        },
        onThumbnailClick = { index ->
            if (index != pagerState.currentPage) {
                hasUserInteracted.value = true
                coroutineScope.launch {
                    pagerState.scrollToPage(index)
                }
            }
        },
        onDismissNgDialog = { viewModel?.closeImageNgDialog() },
        onViewerImageLoadStart = { url -> viewModel?.onViewerImageLoadStart(url) },
        onViewerImageLoadError = { url, failureType ->
            viewModel?.onViewerImageLoadError(url, failureType)
        },
        onViewerImageLoadSuccess = { url -> viewModel?.onViewerImageLoadSuccess(url) },
        onViewerImageRetry = { url -> viewModel?.onViewerImageRetry(url) },
        onThumbnailImageLoadError = { url, failureType ->
            viewModel?.onThumbnailImageLoadError(url, failureType)
        },
        onThumbnailImageLoadSuccess = { url -> viewModel?.onThumbnailImageLoadSuccess(url) },
    )
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
                transitionNamespace = "preview-image-viewer",
                onNavigateUp = {},
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this
            )
        }
    }
}
