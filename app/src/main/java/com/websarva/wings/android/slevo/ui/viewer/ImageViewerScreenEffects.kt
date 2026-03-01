package com.websarva.wings.android.slevo.ui.viewer

import android.app.Activity
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.websarva.wings.android.slevo.ui.common.imagesave.ImageSaveUiEvent
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import me.saket.telephoto.zoomable.ZoomableState
import kotlin.math.abs

/**
 * 画像ビューア画面の副作用処理をまとめて実行する。
 *
 * 画面本体から副作用を分離し、UI 組み立てと依存監視の責務を分ける。
 */
@Composable
internal fun ImageViewerScreenEffects(
    viewModel: ImageViewerViewModel?,
    context: Context,
    activity: Activity?,
    useDarkSystemBarIcons: Boolean,
    isBarsVisible: Boolean,
    isTopBarMenuExpanded: Boolean,
    currentImageUrl: String,
    viewerImageLoadFailureByUrl: Map<String, ImageLoadFailureType>,
    viewerImageLoadingUrls: Set<String>,
    imageUrls: List<String>,
    pagerState: PagerState,
    thumbnailListState: LazyListState,
    thumbnailViewportWidthPx: MutableIntState,
    zoomableStates: List<MutableState<ZoomableState?>>,
    lastPage: MutableIntState,
    isThumbnailAutoScrolling: MutableState<Boolean>,
    shouldSkipIdleSync: MutableState<Boolean>,
    hasPendingIdleCenterSync: MutableState<Boolean>,
    hasUserInteracted: MutableState<Boolean>,
) {
    ImageViewerImageSaveEffect(
        viewModel = viewModel,
        context = context,
    )
    ImageViewerMenuSyncEffect(
        viewModel = viewModel,
        isBarsVisible = isBarsVisible,
        isTopBarMenuExpanded = isTopBarMenuExpanded,
    )
    ImageViewerUnavailableBarsEffect(
        viewModel = viewModel,
        isBarsVisible = isBarsVisible,
        currentImageUrl = currentImageUrl,
        viewerImageLoadFailureByUrl = viewerImageLoadFailureByUrl,
        viewerImageLoadingUrls = viewerImageLoadingUrls,
    )
    ImageViewerSystemBarEffect(
        activity = activity,
        useDarkSystemBarIcons = useDarkSystemBarIcons,
        isBarsVisible = isBarsVisible,
    )
    ImageViewerZoomResetEffect(
        pagerState = pagerState,
        lastPage = lastPage,
        zoomableStates = zoomableStates,
    )
    ImageViewerThumbnailSyncEffects(
        imageUrls = imageUrls,
        pagerState = pagerState,
        thumbnailListState = thumbnailListState,
        isBarsVisible = isBarsVisible,
        thumbnailViewportWidthPx = thumbnailViewportWidthPx,
        isThumbnailAutoScrolling = isThumbnailAutoScrolling,
        shouldSkipIdleSync = shouldSkipIdleSync,
        hasPendingIdleCenterSync = hasPendingIdleCenterSync,
        hasUserInteracted = hasUserInteracted,
    )
}

/**
 * 現在ページが未取得状態の間は上下バーを表示する。
 */
@Composable
private fun ImageViewerUnavailableBarsEffect(
    viewModel: ImageViewerViewModel?,
    isBarsVisible: Boolean,
    currentImageUrl: String,
    viewerImageLoadFailureByUrl: Map<String, ImageLoadFailureType>,
    viewerImageLoadingUrls: Set<String>,
) {
    LaunchedEffect(
        currentImageUrl,
        isBarsVisible,
        viewerImageLoadFailureByUrl,
        viewerImageLoadingUrls,
    ) {
        if (currentImageUrl.isBlank()) {
            // Guard: 空URLは可視制御対象にしない。
            return@LaunchedEffect
        }
        val isUnavailable =
            (currentImageUrl in viewerImageLoadFailureByUrl) ||
                (currentImageUrl in viewerImageLoadingUrls)
        if (isUnavailable && !isBarsVisible) {
            viewModel?.setBarsVisibility(true)
        }
    }
}

/**
 * 画像保存イベントを監視し、権限要求と通知表示を中継する。
 */
@Composable
private fun ImageViewerImageSaveEffect(
    viewModel: ImageViewerViewModel?,
    context: Context,
) {
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
}

/**
 * バー非表示時にトップバーメニューを閉じる。
 */
@Composable
private fun ImageViewerMenuSyncEffect(
    viewModel: ImageViewerViewModel?,
    isBarsVisible: Boolean,
    isTopBarMenuExpanded: Boolean,
) {
    LaunchedEffect(isBarsVisible, isTopBarMenuExpanded) {
        if (!isBarsVisible && isTopBarMenuExpanded) {
            // Guard: バー非表示中はメニューを閉じる。
            viewModel?.hideTopBarMenu()
        }
    }
}

/**
 * 画像ビューア表示中のシステムバー外観を制御し、終了時に復元する。
 */
@Composable
private fun ImageViewerSystemBarEffect(
    activity: Activity?,
    useDarkSystemBarIcons: Boolean,
    isBarsVisible: Boolean,
) {
    // --- Preserve and restore previous state ---
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

    // --- Apply current appearance and visibility ---
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
}

/**
 * ページ切替時に前ページのズーム状態をリセットする。
 */
@Composable
private fun ImageViewerZoomResetEffect(
    pagerState: PagerState,
    lastPage: MutableIntState,
    zoomableStates: List<MutableState<ZoomableState?>>,
) {
    LaunchedEffect(pagerState.currentPage) {
        val currentPage = pagerState.currentPage
        if (currentPage != lastPage.intValue) {
            // Guard: 別ページへ移動したときのみ前ページのズームを解除する。
            zoomableStates.getOrNull(lastPage.intValue)?.value?.resetZoom()
            lastPage.intValue = currentPage
        }
    }
}

/**
 * サムネイルバーとページャの同期を監視する副作用群を提供する。
 */
@Composable
private fun ImageViewerThumbnailSyncEffects(
    imageUrls: List<String>,
    pagerState: PagerState,
    thumbnailListState: LazyListState,
    isBarsVisible: Boolean,
    thumbnailViewportWidthPx: MutableIntState,
    isThumbnailAutoScrolling: MutableState<Boolean>,
    shouldSkipIdleSync: MutableState<Boolean>,
    hasPendingIdleCenterSync: MutableState<Boolean>,
    hasUserInteracted: MutableState<Boolean>,
) {
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
                if (isThumbnailAutoScrolling.value) {
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
                    scrollPagerToIndexSafely(pagerState, centeredIndex)
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
        isThumbnailAutoScrolling.value = true
        try {
            shouldSkipIdleSync.value = centerThumbnailAtIndex(
                listState = thumbnailListState,
                index = pagerState.currentPage,
                animate = hasUserInteracted.value,
            )
        } finally {
            // Guard: アニメーション終了後に同期停止を解除する。
            isThumbnailAutoScrolling.value = false
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
        isThumbnailAutoScrolling.value = true
        try {
            shouldSkipIdleSync.value = centerThumbnailAtIndex(
                listState = thumbnailListState,
                index = pagerState.currentPage,
                animate = false,
            )
        } finally {
            isThumbnailAutoScrolling.value = false
        }
    }

    // --- Thumbnail scroll stop -> center sync ---
    LaunchedEffect(thumbnailListState, pagerState, isBarsVisible, imageUrls) {
        snapshotFlow { thumbnailListState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isBarsVisible) {
                    hasPendingIdleCenterSync.value = false
                    return@collect
                }
                if (isScrolling) {
                    // Guard: スクロール中は中央寄せを行わない。
                    if (!isThumbnailAutoScrolling.value) {
                        // Guard: ユーザーのサムネイル操作後のみ自動同期アニメを有効化する。
                        hasUserInteracted.value = true
                        shouldSkipIdleSync.value = false
                    }
                    return@collect
                }
                if (thumbnailViewportWidthPx.intValue == 0) {
                    // Guard: 表示領域サイズが確定するまで待機する。
                    return@collect
                }
                if (shouldSkipIdleSync.value) {
                    // Guard: プログラムスクロール後の停止判定を無視する。
                    shouldSkipIdleSync.value = false
                    return@collect
                }
                if (pagerState.isScrollInProgress) {
                    // Guard: ページャ停止後に再センタリングするため保留する。
                    hasPendingIdleCenterSync.value = true
                    return@collect
                }
                hasPendingIdleCenterSync.value = false
                isThumbnailAutoScrolling.value = true
                try {
                    shouldSkipIdleSync.value = syncIdleThumbnailCenter(
                        thumbnailListState = thumbnailListState,
                        pagerState = pagerState,
                        animate = hasUserInteracted.value,
                    )
                } finally {
                    // Guard: アニメーション終了後に同期停止を解除する。
                    isThumbnailAutoScrolling.value = false
                }
            }
    }

    // --- Pager stop -> pending thumbnail center sync ---
    LaunchedEffect(thumbnailListState, pagerState, isBarsVisible) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isPagerScrolling ->
                if (!isBarsVisible) {
                    hasPendingIdleCenterSync.value = false
                    return@collect
                }
                if (isPagerScrolling) {
                    if (!isThumbnailAutoScrolling.value) {
                        // Guard: ユーザーのページャ操作後のみ自動同期アニメを有効化する。
                        hasUserInteracted.value = true
                    }
                    return@collect
                }
                if (!hasPendingIdleCenterSync.value) {
                    return@collect
                }
                if (thumbnailListState.isScrollInProgress) {
                    // Guard: ユーザーが再操作した場合は停止同期を中断する。
                    return@collect
                }
                if (thumbnailViewportWidthPx.intValue == 0) {
                    return@collect
                }
                hasPendingIdleCenterSync.value = false
                isThumbnailAutoScrolling.value = true
                try {
                    shouldSkipIdleSync.value = syncIdleThumbnailCenter(
                        thumbnailListState = thumbnailListState,
                        pagerState = pagerState,
                        animate = hasUserInteracted.value,
                    )
                } finally {
                    isThumbnailAutoScrolling.value = false
                }
            }
    }
}
