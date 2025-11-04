package com.websarva.wings.android.slevo.ui.viewer

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import me.saket.telephoto.zoomable.OverzoomEffect
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ImageViewerScreen(
    imageUrl: String,
    onDismissRequest: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(
            maxZoomFactor = 12f,
            overzoomEffect = OverzoomEffect.RubberBanding
        )
    )
    val imageState = rememberZoomableImageState(zoomableState)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val imageModifier = Modifier.fillMaxSize()
        
        val finalModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                imageModifier.sharedElement(
                    state = rememberSharedContentState(key = "image-$imageUrl"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = { _, _ ->
                        tween(durationMillis = 300)
                    }
                )
            }
        } else {
            imageModifier
        }
        
        ZoomableAsyncImage(
            model = imageUrl,
            contentDescription = null,
            state = imageState,
            modifier = finalModifier,
        )

        TopAppBar(
            title = { /* 必要であればタイトル */ },
            navigationIcon = {
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "戻る",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black.copy(alpha = 0.5f)
            ),
            windowInsets = WindowInsets.systemBars
        )
    }
}

class ImageViewerDialogState internal constructor() {
    var imageUrl by mutableStateOf<String?>(null)
        private set

    fun show(url: String) {
        imageUrl = url
    }

    fun dismiss() {
        imageUrl = null
    }
}

@Composable
fun rememberImageViewerDialogState(): ImageViewerDialogState {
    return remember { ImageViewerDialogState() }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ImageViewerDialog(
    state: ImageViewerDialogState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val imageUrl = state.imageUrl ?: return
    ImageViewerScreen(
        imageUrl = imageUrl,
        onDismissRequest = { state.dismiss() },
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope
    )
}
