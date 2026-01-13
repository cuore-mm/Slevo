package com.websarva.wings.android.slevo.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

/**
 * 画像URL一覧をサムネイルのグリッドとして表示する。
 *
 * タップと長押しを分岐して通知し、サムネイルには共有トランジション用の要素を付与する。
 *
 * 長押し時は対象URLと同一投稿内の画像URL一覧を通知する。
 * 共有トランジションが不要な場合は無効化できる。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ImageThumbnailGrid(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit,
    onImageLongPress: ((String, List<String>) -> Unit)? = null,
    enableSharedElement: Boolean = true,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        imageUrls.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEach { url ->
                    with(sharedTransitionScope) {
                        val sharedElementModifier = if (enableSharedElement) {
                            Modifier.sharedElement(
                                sharedContentState = sharedTransitionScope.rememberSharedContentState(
                                    key = url
                                ),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        } else {
                            Modifier
                        }
                        SubcomposeAsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .combinedClickable(
                                    onClick = { onImageClick(url) },
                                    onLongClick = onImageLongPress?.let { { it(url, imageUrls) } },
                                )
                                .then(sharedElementModifier),
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            },
                        )
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
