package com.websarva.wings.android.slevo.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.transition.ImageSharedTransitionKeyFactory
import com.websarva.wings.android.slevo.ui.util.ImageActionReuseRegistry
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressState
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressIndicator
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressIndicatorStyle
import com.websarva.wings.android.slevo.ui.util.toImageLoadFailureType
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme

/** 画像グリッドと返信プレビューで共通利用する単一画像タイルを描画し、操作と読込状態を通知する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ImageThumbnail(
    url: String,
    imageUrls: List<String>,
    imageIndex: Int,
    transitionNamespace: String,
    modifier: Modifier = Modifier,
    onImageClick: (String, List<String>, Int, String) -> Unit,
    onImageLongPress: ((String, List<String>) -> Unit)? = null,
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType> = emptyMap(),
    loadProgressByUrl: Map<String, ImageLoadProgressState> = emptyMap(),
    onImageLoadStart: (String) -> Unit = {},
    onImageLoadError: (String, ImageLoadFailureType) -> Unit = { _, _ -> },
    onImageLoadSuccess: (String) -> Unit = {},
    onImageRetry: (String) -> Unit = {},
    enableSharedElement: Boolean = true,
    imageContentDescription: String,
    cacheKeyPrefix: String = "image",
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // --- Per-image state and request ---
    val context = LocalContext.current
    var canNavigate by remember(url, transitionNamespace, imageIndex) { mutableStateOf(false) }
    var retryNonce by remember(url, transitionNamespace, imageIndex) { mutableIntStateOf(0) }
    val failureType = imageLoadFailureByUrl[url]
    val isError = failureType != null
    val imageRequest = remember(url, retryNonce, cacheKeyPrefix, context) {
        ImageRequest.Builder(context)
            .data(url)
            .memoryCacheKey("$url#$cacheKeyPrefix-$retryNonce")
            .build()
    }

    // --- Transition identity and independent image actions ---
    with(sharedTransitionScope) {
        val sharedElementModifier = if (enableSharedElement) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(
                    key = ImageSharedTransitionKeyFactory.buildKey(
                        transitionNamespace = transitionNamespace,
                        imageUrl = url,
                        imageIndex = imageIndex,
                    ),
                ),
                animatedVisibilityScope = animatedVisibilityScope,
                renderInOverlayDuringTransition = false,
            )
        } else {
            Modifier
        }
        val tileModifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = imageContentDescription,
                onClick = {
                    // 恒久エラー画像は再試行せず、その他のエラーは明示タップで再試行する。
                    if (failureType == ImageLoadFailureType.HTTP_404 ||
                        failureType == ImageLoadFailureType.HTTP_410
                    ) return@combinedClickable
                    if (isError) {
                        onImageRetry(url)
                        retryNonce += 1
                        return@combinedClickable
                    }
                    // Coilの成功コールバック後だけ、既存ビューアへ遷移する。
                    if (shouldNavigateToImage(canNavigate, failureType)) {
                        onImageClick(url, imageUrls, imageIndex, transitionNamespace)
                    }
                },
                onLongClick = onImageLongPress?.let { { it(url, imageUrls) } },
            )
            .semantics { contentDescription = imageContentDescription }
            .then(sharedElementModifier)

        // --- Failure, loading, and success states ---
        if (isError) {
            Box(modifier = tileModifier, contentAlignment = Alignment.Center) {
                when (failureType) {
                    ImageLoadFailureType.HTTP_404 -> ErrorCodeLabel(
                        code = "404",
                        message = stringResource(R.string.image_not_found),
                    )
                    ImageLoadFailureType.HTTP_410 -> ErrorCodeLabel(
                        code = "410",
                        message = stringResource(R.string.image_deleted),
                    )
                    else -> Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.refresh),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        } else {
            key(imageIndex, retryNonce) {
                SubcomposeAsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    onSuccess = { state ->
                        canNavigate = true
                        onImageLoadSuccess(url)
                        state.result.diskCacheKey?.let { cacheKey ->
                            ImageActionReuseRegistry.register(
                                url = url,
                                diskCacheKey = cacheKey,
                                extension = url.substringAfterLast('.', ""),
                            )
                        }
                    },
                    onLoading = {
                        canNavigate = false
                        onImageLoadStart(url)
                    },
                    onError = { state ->
                        canNavigate = false
                        onImageLoadError(url, state.result.throwable.toImageLoadFailureType())
                    },
                    modifier = tileModifier,
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ImageLoadProgressIndicator(
                                progressState = loadProgressByUrl[url],
                                indicatorSize = 24.dp,
                                indicatorStyle = ImageLoadProgressIndicatorStyle.STANDARD,
                            )
                        }
                    },
                    error = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

/** 成功済みで失敗状態のない画像だけをビューアへ遷移可能にする。 */
internal fun shouldNavigateToImage(
    hasLoadedSuccessfully: Boolean,
    failureType: ImageLoadFailureType?,
): Boolean = hasLoadedSuccessfully && failureType == null

/** 404/410の状態と説明を画像タイル中央に表示する。 */
@Composable
private fun ErrorCodeLabel(code: String, message: String) {
    Column(
        modifier = Modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = code, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
private fun ImageThumbnailPreview() {
    SlevoTheme {
        SharedTransitionLayout {
            AnimatedVisibility(visible = true) {
                ImageThumbnail(
                    url = "https://example.com/image.jpg",
                    imageUrls = listOf("https://example.com/image.jpg"),
                    imageIndex = 0,
                    transitionNamespace = "thread-post-1",
                    modifier = Modifier.size(56.dp),
                    onImageClick = { _, _, _, _ -> },
                    imageContentDescription = stringResource(R.string.image_thumbnail_open),
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }
        }
    }
}
