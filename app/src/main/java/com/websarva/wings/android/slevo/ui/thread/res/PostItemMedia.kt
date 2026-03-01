package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.ui.common.ImageThumbnailGrid
import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import com.websarva.wings.android.slevo.ui.util.extractImageUrls

/**
 * 投稿本文に含まれる画像URLを抽出し、サムネイル一覧を表示する。
 *
 * 画像タップ時は対象URLと同一レス内の画像一覧とタップ位置、長押し時も同一レス内画像一覧を通知する。
 * 読み込み開始/成功/失敗はコールバック経由で上位に伝播する。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun PostItemMedia(
    post: ThreadPostUiModel,
    transitionNamespace: String,
    onImageClick: (String, List<String>, Int, String) -> Unit,
    onImageLongPress: (String, List<String>) -> Unit,
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType> = emptyMap(),
    onImageLoadStart: (String) -> Unit = {},
    onImageLoadError: (String, ImageLoadFailureType) -> Unit = { _, _ -> },
    onImageLoadSuccess: (String) -> Unit = {},
    onImageRetry: (String) -> Unit = {},
    enableSharedElement: Boolean = true,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val imageUrls = remember(post.body.content, post.meta.urlFlags) {
        if (post.meta.urlFlags and ReplyInfo.HAS_IMAGE_URL != 0) {
            extractImageUrls(post.body.content)
        } else {
            emptyList()
        }
    }
    if (imageUrls.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        ImageThumbnailGrid(
            imageUrls = imageUrls,
            transitionNamespace = transitionNamespace,
            onImageClick = { url, urls, index, namespace ->
                onImageClick(url, urls, index, namespace)
            },
            onImageLongPress = onImageLongPress,
            imageLoadFailureByUrl = imageLoadFailureByUrl,
            onImageLoadStart = onImageLoadStart,
            onImageLoadError = onImageLoadError,
            onImageLoadSuccess = onImageLoadSuccess,
            onImageRetry = onImageRetry,
            enableSharedElement = enableSharedElement,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
private fun PostItemMediaPreview() {
    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            PostItemMedia(
                post = ThreadPostUiModel(
                    header = ThreadPostUiModel.Header(
                        name = "風吹けば名無し",
                        email = "sage",
                        date = "2025/12/16(火) 12:34:56.78",
                        id = "testid",
                    ),
                    body = ThreadPostUiModel.Body(
                        content = "画像 https://i.imgur.com/0KFBHTB.jpeg",
                    ),
                    meta = ThreadPostUiModel.Meta(
                        urlFlags = ReplyInfo.HAS_IMAGE_URL,
                    ),
                ),
                onImageClick = { _, _, _, _ -> },
                transitionNamespace = "preview-post",
                onImageLongPress = { _, _ -> },
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
            )
        }
    }
}
