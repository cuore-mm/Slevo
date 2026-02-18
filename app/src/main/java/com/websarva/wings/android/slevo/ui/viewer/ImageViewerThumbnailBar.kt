package com.websarva.wings.android.slevo.ui.viewer

import android.graphics.drawable.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.asDrawable
import coil3.compose.SubcomposeAsyncImage
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.util.ImageActionReuseRegistry
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressRegistry
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressState

/**
 * 同一レス内のサムネイル一覧を表示し、選択中の画像を中央に寄せる。
 *
 * 選択中サムネイルはサイズを拡大し、タップで表示画像を切り替える。
 */
@Composable
internal fun ImageViewerThumbnailBar(
    imageUrls: List<String>,
    pagerState: PagerState,
    isBarsVisible: Boolean,
    thumbnailListState: LazyListState,
    modifier: Modifier,
    barBackgroundColor: Color,
    barExitDurationMillis: Int,
    thumbnailViewportWidthPx: MutableIntState,
    onThumbnailClick: (Int) -> Unit,
) {
    val thumbnailWidth: Dp = 40.dp
    val thumbnailHeight: Dp = 56.dp
    val thumbnailShape = RoundedCornerShape(4.dp)
    val thumbnailSpacing: Dp = 4.dp
    val selectedThumbnailScale = 1.2f
    val resources = LocalResources.current
    val density = LocalDensity.current
    val loadProgressByUrl by ImageLoadProgressRegistry.progressByUrl.collectAsState()
    val isLoadingByIndex = remember(imageUrls) {
        mutableStateMapOf<Int, Boolean>().apply {
            imageUrls.indices.forEach { index ->
                this[index] = false
            }
        }
    }

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
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val baseScale =
                        if (isSelected) {
                            1f
                        } else {
                            1f / selectedThumbnailScale
                        }
                    val scale by animateFloatAsState(
                        targetValue = baseScale * if (isPressed) 0.95f else 1f,
                        animationSpec = tween(durationMillis = 100),
                        label = "thumbnailScale",
                    )
                    SubcomposeAsyncImage(
                        model = imageUrls[index],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        onLoading = {
                            isLoadingByIndex[index] = true
                            ImageLoadProgressRegistry.start(imageUrls[index])
                        },
                        onSuccess = { state ->
                            isLoadingByIndex[index] = false
                            ImageLoadProgressRegistry.finish(imageUrls[index])
                            state.result.diskCacheKey?.let { key ->
                                ImageActionReuseRegistry.register(
                                    url = imageUrls[index],
                                    diskCacheKey = key,
                                    extension = imageUrls[index].substringAfterLast('.', ""),
                                )
                            }
                            // Guard: GIFなどのアニメーションDrawableはサムネイルで再生させない。
                            (state.result.image.asDrawable(resources) as? Animatable)?.stop()
                        },
                        onError = {
                            isLoadingByIndex[index] = false
                            ImageLoadProgressRegistry.finish(imageUrls[index])
                        },
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
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                            ) { onThumbnailClick(index) }
                            .background(Color.DarkGray),
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isLoadingByIndex[index] == true) {
                                    ThumbnailBarLoadingIndicator(
                                        progressState = loadProgressByUrl[imageUrls[index]],
                                    )
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
 * ビューア下部サムネイル用の読み込み中インジケータを表示する。
 *
 * 進捗率が算出可能な場合は段階表示、算出不能な場合は無段階表示を行う。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThumbnailBarLoadingIndicator(
    progressState: ImageLoadProgressState?,
) {
    when (progressState) {
        is ImageLoadProgressState.Determinate -> {
            CircularWavyProgressIndicator(
                progress = { progressState.progress },
                modifier = Modifier.size(18.dp),
            )
        }

        ImageLoadProgressState.Indeterminate,
        null,
            -> {
            CircularWavyProgressIndicator(
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Preview
@Composable
private fun ImageViewerThumbnailBarPreview() {
    val imageUrls = listOf("", "", "", "", "")
    val pagerState = rememberPagerState { imageUrls.size }
    val thumbnailListState = rememberLazyListState()
    val thumbnailViewportWidthPx = remember { mutableIntStateOf(1000) }

    SlevoTheme {
        ImageViewerThumbnailBar(
            imageUrls = imageUrls,
            pagerState = pagerState,
            isBarsVisible = true,
            thumbnailListState = thumbnailListState,
            modifier = Modifier,
            barBackgroundColor = Color.Black.copy(alpha = 0.5f),
            barExitDurationMillis = 300,
            thumbnailViewportWidthPx = thumbnailViewportWidthPx,
            onThumbnailClick = {}
        )
    }
}
