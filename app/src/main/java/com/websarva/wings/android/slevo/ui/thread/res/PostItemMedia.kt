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
import com.websarva.wings.android.slevo.ui.util.extractImageUrls

/**
 * 投稿本文に含まれる画像URLを抽出し、サムネイル一覧を表示する。
 *
 * 画像タップ/長押し時はURLをコールバックで通知する。
 *
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun PostItemMedia(
    post: ThreadPostUiModel,
    onImageClick: (String) -> Unit,
    onImageLongPress: (String) -> Unit,
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
            onImageClick = onImageClick,
            onImageLongPress = onImageLongPress,
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
                onImageClick = {},
                onImageLongPress = {},
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
            )
        }
    }
}
