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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.websarva.wings.android.slevo.ui.common.transition.ImageSharedTransitionKeyFactory
import com.websarva.wings.android.slevo.ui.util.ImageActionReuseRegistry
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressRegistry
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressState

/**
 * 画像URL一覧をサムネイルのグリッドとして表示する。
 *
 * タップと長押しを分岐して通知し、サムネイルには共有トランジション用の要素を付与する。
 *
 * タップ時は対象URLと同一投稿内の画像URL一覧およびタップ位置を通知し、長押し時はURL一覧を通知する。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ImageThumbnailGrid(
    imageUrls: List<String>,
    modifier: Modifier = Modifier,
    transitionNamespace: String,
    onImageClick: (String, List<String>, Int, String) -> Unit,
    onImageLongPress: ((String, List<String>) -> Unit)? = null,
    enableSharedElement: Boolean = true,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // --- Thumbnail load state ---
    val canNavigateByIndex = remember(imageUrls) {
        mutableStateMapOf<Int, Boolean>().apply {
            imageUrls.indices.forEach { index ->
                this[index] = false
            }
        }
    }
    val loadProgressByUrl by ImageLoadProgressRegistry.progressByUrl.collectAsState()

    // --- Grid render ---
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        imageUrls.chunked(3).forEachIndexed { rowIndex, rowItems ->
            val baseIndex = rowIndex * 3
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rowItems.forEachIndexed { columnIndex, url ->
                    val imageIndex = baseIndex + columnIndex
                    with(sharedTransitionScope) {
                        val sharedElementModifier = if (enableSharedElement) {
                            Modifier.sharedElement(
                                sharedContentState = sharedTransitionScope.rememberSharedContentState(
                                    key = ImageSharedTransitionKeyFactory.buildKey(
                                        transitionNamespace = transitionNamespace,
                                        imageUrl = url,
                                        imageIndex = imageIndex,
                                    )
                                ),
                                animatedVisibilityScope = animatedVisibilityScope,
                                renderInOverlayDuringTransition = false,
                            )
                        } else {
                            Modifier
                        }
                        SubcomposeAsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            onSuccess = { state ->
                                canNavigateByIndex[imageIndex] = true
                                ImageLoadProgressRegistry.finish(url)
                                state.result.diskCacheKey?.let { key ->
                                    ImageActionReuseRegistry.register(
                                        url = url,
                                        diskCacheKey = key,
                                        extension = url.substringAfterLast('.', ""),
                                    )
                                }
                            },
                            onLoading = {
                                canNavigateByIndex[imageIndex] = false
                                ImageLoadProgressRegistry.start(url)
                            },
                            onError = {
                                canNavigateByIndex[imageIndex] = false
                                ImageLoadProgressRegistry.finish(url)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .combinedClickable(
                                    onClick = {
                                        // 表示成功したサムネイルのみビューア遷移を許可する。
                                        if (canNavigateByIndex[imageIndex] == true) {
                                            onImageClick(url, imageUrls, imageIndex, transitionNamespace)
                                        }
                                    },
                                    onLongClick = onImageLongPress?.let { { it(url, imageUrls) } },
                                )
                                .then(sharedElementModifier),
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    ThumbnailLoadingIndicator(
                                        progressState = loadProgressByUrl[url],
                                    )
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

/**
 * サムネイル読み込み中インジケータを表示する。
 *
 * 進捗率が算出可能な場合は段階表示、算出不能な場合は無段階表示を行う。
 */
@Composable
private fun ThumbnailLoadingIndicator(
    progressState: ImageLoadProgressState?,
) {
    when (progressState) {
        is ImageLoadProgressState.Determinate -> {
            CircularProgressIndicator(
                progress = { progressState.progress },
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }

        ImageLoadProgressState.Indeterminate,
        null,
        -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}
