package com.websarva.wings.android.slevo.ui.viewer

import android.graphics.drawable.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.asDrawable

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
    val context = LocalContext.current
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
                        onSuccess = { state ->
                            // Guard: GIFなどのアニメーションDrawableはサムネイルで再生させない。
                            (state.result.image.asDrawable(context.resources) as? Animatable)?.stop()
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
                            .clickable { onThumbnailClick(index) }
                            .background(Color.DarkGray),
                    )
                }
            }
        }
    }
}
