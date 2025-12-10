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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.websarva.wings.android.slevo.R
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ImageViewerScreen(
    imageUrl: String,
    onNavigateUp: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    var isBarsVisible by rememberSaveable { mutableStateOf(true) }

    Scaffold(
        topBar = {
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
        val zoomableState = rememberZoomableState(
            zoomSpec = ZoomSpec(
                maxZoomFactor = 12f,                  // ← ここを例えば 8x や 12x に
                overzoomEffect = OverzoomEffect.RubberBanding // 端でビヨン効果（好みで）
            )
        )
        val imageState = rememberZoomableImageState(zoomableState)

        with(sharedTransitionScope) {
            ZoomableAsyncImage(
                model = imageUrl,
                contentDescription = null,
                state = imageState,
                modifier = Modifier
                    .sharedElement(
                        sharedContentState = sharedTransitionScope.rememberSharedContentState(key = imageUrl),
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
