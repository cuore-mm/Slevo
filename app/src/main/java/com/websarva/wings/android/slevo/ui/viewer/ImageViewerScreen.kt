package com.websarva.wings.android.slevo.ui.viewer

import android.app.Activity
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.SubcomposeAsyncImage
import com.websarva.wings.android.slevo.R
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.toClipEntry
import com.websarva.wings.android.slevo.data.model.NgType
import com.websarva.wings.android.slevo.ui.common.ImageMenuActionRunner
import com.websarva.wings.android.slevo.ui.common.ImageMenuActionRunnerParams
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent
import com.websarva.wings.android.slevo.ui.thread.dialog.NgDialogRoute
import com.websarva.wings.android.slevo.ui.thread.sheet.ImageMenuAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    val thumbnailShape = RoundedCornerShape(8.dp)
    val thumbnailSpacing = 8.dp
    val selectedThumbnailScale = 1.1f
    val barExitDurationMillis = 80
    val useDarkSystemBarIcons = barBackgroundColor.luminance() > 0.5f

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
        val previousSystemBarsVisible = ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.systemBars()) ?: true
        val previousLightStatusBars = insetsController.isAppearanceLightStatusBars
        val previousLightNavigationBars = insetsController.isAppearanceLightNavigationBars
        val previousNavigationBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            if (previousSystemBarsVisible) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = previousNavigationBarContrastEnforced ?: true
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
                barExitDurationMillis = barExitDurationMillis,
                onNavigateUp = onNavigateUp,
                onSaveClick = { viewModel?.requestImageSave(context, listOf(currentImageUrl)) },
                onMoreClick = { viewModel?.toggleTopBarMenu() },
                onDismissMenu = { viewModel?.hideTopBarMenu() },
                onMenuActionClick = onImageMenuActionClick,
            )
        },
        containerColor = Color.Black,
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
                        thumbnailWidth = thumbnailWidth,
                        thumbnailHeight = thumbnailHeight,
                        thumbnailShape = thumbnailShape,
                        thumbnailSpacing = thumbnailSpacing,
                        selectedThumbnailScale = selectedThumbnailScale,
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

/**
 * Context から Activity を辿って返す。
 *
 * Activity でない Context の場合は ContextWrapper をたどり、見つからないときは null を返す。
 */
private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

/**
 * 画像ビューアのトップバーを表示する。
 *
 * 戻る・保存・その他メニューの各アクションを持ち、表示可否は呼び出し元で制御する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageViewerTopBar(
    isVisible: Boolean,
    isMenuExpanded: Boolean,
    imageCount: Int,
    barBackgroundColor: Color,
    barExitDurationMillis: Int,
    onNavigateUp: () -> Unit,
    onSaveClick: () -> Unit,
    onMoreClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onMenuActionClick: (ImageMenuAction) -> Unit,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(barExitDurationMillis)),
    ) {
        TopAppBar(
            title = {},
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            navigationIcon = {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(onClick = onSaveClick) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = stringResource(R.string.save),
                        tint = Color.White,
                    )
                }
                Box {
                    IconButton(onClick = onMoreClick) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.more),
                            tint = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = onDismissMenu,
                    ) {
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.image_menu_add_ng)) },
                            onClick = { onMenuActionClick(ImageMenuAction.ADD_NG) },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.image_menu_copy_image)) },
                            onClick = { onMenuActionClick(ImageMenuAction.COPY_IMAGE) },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.image_menu_copy_image_url)) },
                            onClick = { onMenuActionClick(ImageMenuAction.COPY_IMAGE_URL) },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.image_menu_open_in_other_app)) },
                            onClick = { onMenuActionClick(ImageMenuAction.OPEN_IN_OTHER_APP) },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.image_menu_search_web)) },
                            onClick = { onMenuActionClick(ImageMenuAction.SEARCH_WEB) },
                        )
                        DropdownMenuItem(
                            text = { Text(text = stringResource(R.string.image_menu_share_image)) },
                            onClick = { onMenuActionClick(ImageMenuAction.SHARE_IMAGE) },
                        )
                        if (imageCount >= 2) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(
                                            R.string.image_menu_save_all_images_with_count,
                                            imageCount,
                                        )
                                    )
                                },
                                onClick = { onMenuActionClick(ImageMenuAction.SAVE_ALL_IMAGES) },
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = barBackgroundColor
            ),
            windowInsets = WindowInsets(0)
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
    barExitDurationMillis: Int,
    thumbnailViewportWidthPx: MutableIntState,
    onThumbnailClick: (Int) -> Unit,
) {
    val density = LocalDensity.current

    // --- Layout ---
    AnimatedVisibility(
        visible = isBarsVisible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(barExitDurationMillis)),
        modifier = modifier.windowInsetsPadding(WindowInsets.navigationBars),
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
            val horizontalPaddingPx =
                ((thumbnailViewportWidthPx.intValue - fallbackItemWidthPx) / 2f).coerceAtLeast(0f)
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
                        targetValue = if (isSelected) 1f else 1f / selectedThumbnailScale,
                        label = "thumbnailScale",
                    )
                    SubcomposeAsyncImage(
                        model = imageUrls[index],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .size(
                                width = thumbnailWidth * selectedThumbnailScale,
                                height = thumbnailHeight * selectedThumbnailScale,
                            )
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(thumbnailShape)
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

/**
 * 指定サムネイルの中心と表示領域中心の差分をピクセルで返す。
 *
 * サムネイルが可視領域外の場合は null を返す。
 */
private fun findThumbnailCenterDeltaPx(
    layoutInfo: LazyListLayoutInfo,
    index: Int,
): Int? {
    val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == index }
        ?: run {
            // Guard: 可視領域外のアイテムは距離計算できない。
            return null
        }
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val itemCenter = targetItem.offset + targetItem.size / 2
    return itemCenter - viewportCenter
}

/**
 * サムネイル停止時に中央へ最も近いサムネイルを選択し、必要に応じて中央へ寄せる。
 *
 * サムネイルバーを実際に自動スクロールした場合は true を返す。
 */
private suspend fun syncIdleThumbnailCenter(
    thumbnailListState: LazyListState,
    pagerState: PagerState,
    animate: Boolean = true,
): Boolean {
    val centeredIndex = findCenteredThumbnailIndex(thumbnailListState.layoutInfo)
        ?: return false
    if (!scrollPagerToIndexSafely(pagerState, centeredIndex)) {
        return false
    }
    return centerThumbnailAtIndex(
        listState = thumbnailListState,
        index = centeredIndex,
        animate = animate,
    )
}

/**
 * ページャを指定インデックスへ即時移動し、ユーザー割り込み時は監視継続のため失敗扱いで返す。
 */
private suspend fun scrollPagerToIndexSafely(
    pagerState: PagerState,
    index: Int,
): Boolean {
    if (pagerState.currentPage == index) {
        return true
    }
    return try {
        pagerState.scrollToPage(index)
        true
    } catch (cancellationException: CancellationException) {
        if (!currentCoroutineContext().isActive) {
            throw cancellationException
        }
        false
    }
}

/**
 * 指定したサムネイルが表示領域の中心に来るようスクロールする。
 *
 * 対象が可視外の場合は一度可視化してから中心に寄せる。
 */
private suspend fun centerThumbnailAtIndex(
    listState: LazyListState,
    index: Int,
    animate: Boolean = true,
): Boolean {
    return try {
        var didAutoScroll = false
        val initialDelta = findThumbnailCenterDeltaPx(
            layoutInfo = listState.layoutInfo,
            index = index,
        )
        if (initialDelta == null) {
            // Guard: まずは対象を可視化してから中心寄せを行う。
            if (animate) {
                listState.animateScrollToItem(index)
            } else {
                listState.scrollToItem(index)
            }
            didAutoScroll = true
        } else if (abs(initialDelta) <= 1) {
            // Guard: すでに中心に近い場合は処理しない。
            return false
        }
        val updatedDelta = findThumbnailCenterDeltaPx(
            layoutInfo = listState.layoutInfo,
            index = index,
        ) ?: snapshotFlow {
            findThumbnailCenterDeltaPx(
                layoutInfo = listState.layoutInfo,
                index = index,
            )
        }
            .filterNotNull()
            .first()
        if (abs(updatedDelta) <= 1) {
            // Guard: 既に中心に近い場合は処理しない。
            return didAutoScroll
        }
        if (animate) {
            listState.animateScrollBy(updatedDelta.toFloat())
        } else {
            listState.scrollBy(updatedDelta.toFloat())
        }
        true
    } catch (cancellationException: CancellationException) {
        if (!currentCoroutineContext().isActive) {
            throw cancellationException
        }
        false
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
