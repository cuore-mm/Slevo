package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.transition.ImageReplyPreviewType
import com.websarva.wings.android.slevo.ui.thread.state.PostReplyPreviewUiState
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewPostUiModel
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import androidx.compose.ui.res.stringResource

/** 本文・通常画像の後に返信元プレビュー三件と省略操作を表示する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun PostReplyPreviews(
    state: PostReplyPreviewUiState,
    hostNamespace: String,
    headerTextScale: Float,
    bodyTextScale: Float,
    onReplyClick: (Int) -> Unit,
    onReplyNumbersClick: (List<Int>) -> Unit,
    onImageClick: (String, List<String>, Int, String) -> Unit,
    onImageLongPress: (String, List<String>) -> Unit,
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType>,
    onImageLoadStart: (String) -> Unit,
    onImageLoadError: (String, ImageLoadFailureType) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onImageRetry: (String) -> Unit,
    enableSharedElement: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    if (state.replySourcePreviews.isEmpty()) return

    // --- Reply-source heading ---
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.reply_preview_source_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // --- Oldest visible previews and optional overflow ---
        state.replySourcePreviews.forEachIndexed { index, preview ->
            val overflowCount = if (index == state.replySourcePreviews.lastIndex) {
                state.replySourceOverflowCount
            } else 0
            ReplyPreviewCard(
                post = preview,
                hostNamespace = hostNamespace,
                previewType = ImageReplyPreviewType.REPLY_SOURCE,
                overflowCount = overflowCount,
                overflowNumbers = state.replySourceNumbers,
                headerTextScale = headerTextScale,
                bodyTextScale = bodyTextScale,
                onOpenPost = { onReplyClick(preview.postNumber) },
                onOpenOverflow = onReplyNumbersClick,
                onImageClick = onImageClick,
                onImageLongPress = onImageLongPress,
                imageLoadFailureByUrl = imageLoadFailureByUrl,
                onImageLoadStart = onImageLoadStart,
                onImageLoadError = onImageLoadError,
                onImageLoadSuccess = onImageLoadSuccess,
                onImageRetry = onImageRetry,
                enableSharedElement = enableSharedElement,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true)
@Composable
private fun PostReplyPreviewsPreview() {
    SlevoTheme {
        SharedTransitionLayout {
            AnimatedVisibility(visible = true) {
                PostReplyPreviews(
                    state = PostReplyPreviewUiState(
                        replySourcePreviews = listOf(
                            ReplyPreviewPostUiModel(2, "名前", "abc123", "返信本文", emptyList()),
                        ),
                        replySourceNumbers = listOf(2, 4, 6, 8, 10),
                    ),
                    hostNamespace = "thread-post-12",
                    headerTextScale = 1f,
                    bodyTextScale = 1f,
                    onReplyClick = {},
                    onReplyNumbersClick = {},
                    onImageClick = { _, _, _, _ -> },
                    onImageLongPress = { _, _ -> },
                    imageLoadFailureByUrl = emptyMap(),
                    onImageLoadStart = {},
                    onImageLoadError = { _, _ -> },
                    onImageLoadSuccess = {},
                    onImageRetry = {},
                    enableSharedElement = true,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }
        }
    }
}
