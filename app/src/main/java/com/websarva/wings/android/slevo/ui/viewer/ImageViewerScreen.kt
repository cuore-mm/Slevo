package com.websarva.wings.android.slevo.ui.viewer

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.websarva.wings.android.slevo.R
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

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
        val safeInitialIndex = initialIndex.coerceIn(0, imageUrls.lastIndex)
        val pagerState = rememberPagerState(
            initialPage = safeInitialIndex,
            pageCount = { imageUrls.size },
        )
        val zoomableStates = remember(imageUrls) {
            MutableList(imageUrls.size) { mutableStateOf<ZoomableState?>(null) }
        }
        var lastPage by rememberSaveable { mutableIntStateOf(safeInitialIndex) }

        // --- Zoom reset ---
        LaunchedEffect(pagerState.currentPage) {
            val currentPage = pagerState.currentPage
            if (currentPage != lastPage) {
                zoomableStates.getOrNull(lastPage)?.value?.resetZoom()
                lastPage = currentPage
            }
        }

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
    }
}
