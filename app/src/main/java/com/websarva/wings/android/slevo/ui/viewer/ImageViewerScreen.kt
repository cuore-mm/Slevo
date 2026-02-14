package com.websarva.wings.android.slevo.ui.viewer

import android.annotation.SuppressLint
import android.content.ClipData
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.common.ImageMenuActionRunner
import com.websarva.wings.android.slevo.ui.common.ImageMenuActionRunnerParams
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent
import com.websarva.wings.android.slevo.ui.thread.dialog.NgDialogRoute
import com.websarva.wings.android.slevo.ui.thread.sheet.ImageMenuAction
import com.websarva.wings.android.slevo.ui.theme.LocalIsDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.ZoomableState
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
    transitionNamespace: String,
    onNavigateUp: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // --- Constants ---
    val isDarkTheme = LocalIsDarkTheme.current
    val viewerBackgroundColor = if (isDarkTheme) Color.Black else Color.White
    val viewerContentColor = if (isDarkTheme) Color.White else Color.Black
    val barBackgroundColor = if (isDarkTheme) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)
    val tooltipBackgroundColor = barBackgroundColor
    val barExitDurationMillis = 80
    val useDarkSystemBarIcons = !isDarkTheme

    // --- UI state ---
    var isBarsVisible by rememberSaveable { mutableStateOf(true) }
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
    var lastPage by rememberSaveable { mutableIntStateOf(safeInitialIndex) }
    val thumbnailViewportWidthPx = remember { mutableIntStateOf(0) }
    var isThumbnailAutoScrolling by remember { mutableStateOf(false) }
    var shouldSkipIdleSync by remember { mutableStateOf(false) }
    var hasPendingIdleCenterSync by remember { mutableStateOf(false) }
    var hasUserInteracted by remember(imageUrls, safeInitialIndex) {
        mutableStateOf(false)
    }
    val currentImageUrl = imageUrls.getOrNull(pagerState.currentPage).orEmpty()

    // --- Image save event ---
    val imageSavePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel?.onImageSavePermissionResult(context, granted)
    }
    LaunchedEffect(viewModel) {
        viewModel?.imageSaveEvents?.collect { event ->
            when (event) {
                is ImageSaveUiEvent.RequestPermission -> {
                    imageSavePermissionLauncher.launch(event.permission)
                }

                is ImageSaveUiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
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

    // --- Menu sync ---
    LaunchedEffect(isBarsVisible, isTopBarMenuExpanded) {
        if (!isBarsVisible && isTopBarMenuExpanded) {
            // Guard: バー非表示中はメニューを閉じる。
            viewModel?.hideTopBarMenu()
        }
    }

    // --- System bar appearance ---
    DisposableEffect(activity) {
        val currentActivity = activity ?: return@DisposableEffect onDispose { }
        val window = currentActivity.window
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        val rootInsets = ViewCompat.getRootWindowInsets(window.decorView)
        val previousStatusBarsVisible =
            rootInsets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val previousNavigationBarsVisible =
            rootInsets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val previousLightStatusBars = insetsController.isAppearanceLightStatusBars
        val previousLightNavigationBars = insetsController.isAppearanceLightNavigationBars
        val previousNavigationBarContrastEnforced =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced
            } else {
                null
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        onDispose {
            insetsController.isAppearanceLightStatusBars = previousLightStatusBars
            insetsController.isAppearanceLightNavigationBars = previousLightNavigationBars
            if (previousStatusBarsVisible) {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
            }
            if (previousNavigationBarsVisible) {
                insetsController.show(WindowInsetsCompat.Type.navigationBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.navigationBars())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced =
                    previousNavigationBarContrastEnforced ?: true
            }
        }
    }
    // --- System bar icon appearance ---
    LaunchedEffect(activity, useDarkSystemBarIcons, isBarsVisible) {
        val currentActivity = activity ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(
            currentActivity.window,
            currentActivity.window.decorView,
        ).apply {
            isAppearanceLightStatusBars = useDarkSystemBarIcons
            isAppearanceLightNavigationBars = useDarkSystemBarIcons
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (isBarsVisible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    Scaffold(
        topBar = {
            ImageViewerTopBar(
                isVisible = isBarsVisible,
                isMenuExpanded = isTopBarMenuExpanded,
                imageCount = imageUrls.size,
                barBackgroundColor = barBackgroundColor,
                foregroundColor = viewerContentColor,
                tooltipBackgroundColor = tooltipBackgroundColor,
                barExitDurationMillis = barExitDurationMillis,
                onNavigateUp = onNavigateUp,
                onSaveClick = { viewModel?.requestImageSave(context, listOf(currentImageUrl)) },
                onMoreClick = { viewModel?.toggleTopBarMenu() },
                onDismissMenu = { viewModel?.hideTopBarMenu() },
                onMenuActionClick = onImageMenuActionClick,
            )
        },
        containerColor = viewerBackgroundColor,
        contentWindowInsets = WindowInsets(0)
    ) { _ ->
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
        LaunchedEffect(thumbnailListState, pagerState, isBarsVisible) {
            snapshotFlow { thumbnailListState.layoutInfo }
                .map { layoutInfo -> findCenteredThumbnailIndex(layoutInfo) }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { centeredIndex ->
                    if (!isBarsVisible) {
                        // Guard: サムネイルバー非表示中は同期を停止する。
                        return@collect
                    }
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
                        try {
                            pagerState.scrollToPage(centeredIndex)
                        } catch (cancellationException: CancellationException) {
                            // Guard: ユーザー割り込み時は監視を継続する。
                            if (!currentCoroutineContext().isActive) {
                                throw cancellationException
                            }
                        }
                    }
                }
        }

        // --- Pager -> thumbnail scroll sync ---
        LaunchedEffect(
            pagerState.currentPage,
            thumbnailViewportWidthPx.intValue,
        ) {
            if (!isBarsVisible) {
                // Guard: サムネイルバー非表示中は同期を停止する。
                return@LaunchedEffect
            }
            if (thumbnailViewportWidthPx.intValue == 0) {
                // Guard: 表示領域サイズが確定するまでスクロールを待機する。
                return@LaunchedEffect
            }
            if (thumbnailListState.isScrollInProgress) {
                // Guard: サムネイルの手動スクロールを優先する。
                return@LaunchedEffect
            }
            val centerDeltaPx = findThumbnailCenterDeltaPx(
                layoutInfo = thumbnailListState.layoutInfo,
                index = pagerState.currentPage,
            )
            if (centerDeltaPx != null && abs(centerDeltaPx) <= 1) {
                // Guard: すでに中心に近い場合は再スクロールしない。
                return@LaunchedEffect
            }
            isThumbnailAutoScrolling = true
            try {
                shouldSkipIdleSync = centerThumbnailAtIndex(
                    listState = thumbnailListState,
                    index = pagerState.currentPage,
                    animate = hasUserInteracted,
                )
            } finally {
                // Guard: アニメーション終了後に同期停止を解除する。
                isThumbnailAutoScrolling = false
            }
        }

        // --- Thumbnail bar show -> center sync (no animation) ---
        LaunchedEffect(isBarsVisible, thumbnailViewportWidthPx.intValue) {
            if (!isBarsVisible) {
                return@LaunchedEffect
            }
            if (thumbnailViewportWidthPx.intValue == 0) {
                return@LaunchedEffect
            }
            if (thumbnailListState.isScrollInProgress) {
                // Guard: ユーザー操作中は自動センタリングしない。
                return@LaunchedEffect
            }
            isThumbnailAutoScrolling = true
            try {
                shouldSkipIdleSync = centerThumbnailAtIndex(
                    listState = thumbnailListState,
                    index = pagerState.currentPage,
                    animate = false,
                )
            } finally {
                isThumbnailAutoScrolling = false
            }
        }

        // --- Thumbnail scroll stop -> center sync ---
        LaunchedEffect(thumbnailListState, pagerState, isBarsVisible) {
            snapshotFlow { thumbnailListState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { isScrolling ->
                    if (!isBarsVisible) {
                        hasPendingIdleCenterSync = false
                        return@collect
                    }
                    if (isScrolling) {
                        // Guard: スクロール中は中央寄せを行わない。
                        if (!isThumbnailAutoScrolling) {
                            // Guard: ユーザーのサムネイル操作後のみ自動同期アニメを有効化する。
                            hasUserInteracted = true
                            shouldSkipIdleSync = false
                        }
                        return@collect
                    }
                    if (thumbnailViewportWidthPx.intValue == 0) {
                        // Guard: 表示領域サイズが確定するまで待機する。
                        return@collect
                    }
                    if (shouldSkipIdleSync) {
                        // Guard: プログラムスクロール後の停止判定を無視する。
                        shouldSkipIdleSync = false
                        return@collect
                    }
                    if (pagerState.isScrollInProgress) {
                        // Guard: ページャ停止後に再センタリングするため保留する。
                        hasPendingIdleCenterSync = true
                        return@collect
                    }
                    hasPendingIdleCenterSync = false
                    isThumbnailAutoScrolling = true
                    try {
                        shouldSkipIdleSync = syncIdleThumbnailCenter(
                            thumbnailListState = thumbnailListState,
                            pagerState = pagerState,
                            animate = hasUserInteracted,
                        )
                    } finally {
                        // Guard: アニメーション終了後に同期停止を解除する。
                        isThumbnailAutoScrolling = false
                    }
                }
        }

        // --- Pager stop -> pending thumbnail center sync ---
        LaunchedEffect(thumbnailListState, pagerState, isBarsVisible) {
            snapshotFlow { pagerState.isScrollInProgress }
                .distinctUntilChanged()
                .collect { isPagerScrolling ->
                    if (!isBarsVisible) {
                        hasPendingIdleCenterSync = false
                        return@collect
                    }
                    if (isPagerScrolling) {
                        if (!isThumbnailAutoScrolling) {
                            // Guard: ユーザーのページャ操作後のみ自動同期アニメを有効化する。
                            hasUserInteracted = true
                        }
                        return@collect
                    }
                    if (!hasPendingIdleCenterSync) {
                        return@collect
                    }
                    if (thumbnailListState.isScrollInProgress) {
                        // Guard: ユーザーが再操作した場合は停止同期を中断する。
                        return@collect
                    }
                    if (thumbnailViewportWidthPx.intValue == 0) {
                        return@collect
                    }
                    hasPendingIdleCenterSync = false
                    isThumbnailAutoScrolling = true
                    try {
                        shouldSkipIdleSync = syncIdleThumbnailCenter(
                            thumbnailListState = thumbnailListState,
                            pagerState = pagerState,
                            animate = hasUserInteracted,
                        )
                    } finally {
                        isThumbnailAutoScrolling = false
                    }
                }
        }

        val boxModifier = Modifier
            .fillMaxSize()
            .background(viewerBackgroundColor)

        Box(
            modifier = boxModifier
        ) {
            ImageViewerPager(
                imageUrls = imageUrls,
                transitionNamespace = transitionNamespace,
                pagerState = pagerState,
                zoomableStates = zoomableStates,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onToggleBars = {
                    isBarsVisible = !isBarsVisible
                    if (!isBarsVisible) {
                        viewModel?.hideTopBarMenu()
                    }
                },
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
                        onThumbnailClick = { index ->
                            if (index != pagerState.currentPage) {
                                hasUserInteracted = true
                                coroutineScope.launch {
                                    pagerState.scrollToPage(index)
                                }
                            }
                        },
                    )
                }
            }

            if (uiState.showImageNgDialog) {
                uiState.imageNgTargetUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    NgDialogRoute(
                        text = url,
                        type = NgType.WORD,
                        onDismiss = { viewModel?.closeImageNgDialog() },
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
                transitionNamespace = "preview-image-viewer",
                onNavigateUp = {},
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this
            )
        }
    }
}
