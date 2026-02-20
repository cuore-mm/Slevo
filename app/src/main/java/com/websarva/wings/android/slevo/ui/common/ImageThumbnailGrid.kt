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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.transition.ImageSharedTransitionKeyFactory
import com.websarva.wings.android.slevo.ui.util.ImageActionReuseRegistry
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressIndicator
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressRegistry
import com.websarva.wings.android.slevo.ui.util.toImageLoadFailureType

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
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType> = emptyMap(),
    onImageLoadError: (String, ImageLoadFailureType) -> Unit = { _, _ -> },
    onImageLoadSuccess: (String) -> Unit = {},
    onImageRetry: (String) -> Unit = {},
    enableSharedElement: Boolean = true,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // --- Thumbnail load state ---
    val context = LocalContext.current
    val canNavigateByIndex = remember(imageUrls) {
        mutableStateMapOf<Int, Boolean>().apply {
            imageUrls.indices.forEach { index ->
                this[index] = false
            }
        }
    }
    val retryNonceByIndex = remember(imageUrls) {
        mutableStateMapOf<Int, Int>().apply {
            imageUrls.indices.forEach { index ->
                this[index] = 0
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
                        val failureType = imageLoadFailureByUrl[url]
                        val isError = failureType != null
                        val retryNonce = retryNonceByIndex[imageIndex] ?: 0
                        val imageRequest = remember(url, retryNonce, context) {
                            ImageRequest.Builder(context)
                                .data(url)
                                .memoryCacheKey("$url#grid-$retryNonce")
                                .build()
                        }
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
                        val tileModifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .combinedClickable(
                                onClick = {
                                    // Guard: 404/410 は恒久エラー表示として扱い、再試行を開始しない。
                                    if (failureType == ImageLoadFailureType.HTTP_404 ||
                                        failureType == ImageLoadFailureType.HTTP_410
                                    ) {
                                        return@combinedClickable
                                    }
                                    // Guard: 失敗サムネイルは明示操作時のみ再読み込みを行う。
                                    if (isError) {
                                        onImageRetry(url)
                                        retryNonceByIndex[imageIndex] = retryNonce + 1
                                        return@combinedClickable
                                    }
                                    // Guard: 表示成功したサムネイルのみビューア遷移を許可する。
                                    if (canNavigateByIndex[imageIndex] == true) {
                                        onImageClick(
                                            url,
                                            imageUrls,
                                            imageIndex,
                                            transitionNamespace
                                        )
                                        return@combinedClickable
                                    }
                                },
                                onLongClick = onImageLongPress?.let { { it(url, imageUrls) } },
                            )
                            .then(sharedElementModifier)
                        if (isError) {
                            Box(
                                modifier = tileModifier,
                                contentAlignment = Alignment.Center,
                            ) {
                                when (failureType) {
                                    ImageLoadFailureType.HTTP_404 -> {
                                        ErrorCodeLabel(
                                            code = "404",
                                            message = stringResource(R.string.image_not_found),
                                        )
                                    }

                                    ImageLoadFailureType.HTTP_410 -> {
                                        ErrorCodeLabel(
                                            code = "410",
                                            message = stringResource(R.string.image_deleted),
                                        )
                                    }

                                    else -> {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = stringResource(R.string.refresh),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                        } else {
                            key(imageIndex, retryNonce) {
                                SubcomposeAsyncImage(
                                    model = imageRequest,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    onSuccess = { state ->
                                        canNavigateByIndex[imageIndex] = true
                                        onImageLoadSuccess(url)
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
                                    },
                                    onError = { state ->
                                        canNavigateByIndex[imageIndex] = false
                                        onImageLoadError(
                                            url,
                                            state.result.throwable.toImageLoadFailureType(),
                                        )
                                    },
                                    modifier = tileModifier,
                                    loading = {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            ImageLoadProgressIndicator(
                                                progressState = loadProgressByUrl[url],
                                                indicatorSize = 24.dp,
                                            )
                                        }
                                    },
                                    error = {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Refresh,
                                                contentDescription = stringResource(R.string.refresh),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ErrorCodeLabel(
    code: String,
    message: String,
) {
    Column(
        modifier = Modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
