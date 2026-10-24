package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.websarva.wings.android.slevo.data.model.TextDisplaySettingsConstraints
import com.websarva.wings.android.slevo.ui.common.transition.ImageReplyPreviewType
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType

/** 返信先または返信元一件を既存の小型ヘッダー・本文・画像形式で表示する。 */
@Composable
internal fun ReplyPreviewCard(
    post: ReplyPreviewPostUiModel,
    hostNamespace: String,
    previewType: ImageReplyPreviewType,
    overflowCount: Int = 0,
    overflowNumbers: List<Int> = emptyList(),
    unavailableRanges: List<ReplyTargetUnavailableRange> = emptyList(),
    onHide: (() -> Unit)? = null,
    isNgHidden: Boolean = false,
    onReveal: (() -> Unit)? = null,
    headerTextScale: Float,
    bodyTextScale: Float,
    lineHeightEm: Float,
    onOpenPost: () -> Unit,
    onOpenOverflow: (List<Int>) -> Unit,
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
    // 非表示経路には番号だけを渡し、投稿情報・本文・画像をComposableへ渡さない。
    if (isNgHidden) {
        ReplyPreviewCardHiddenContent(
            postNumber = post.postNumber,
            overflowCount = overflowCount,
            overflowNumbers = overflowNumbers,
            unavailableRanges = unavailableRanges,
            onReveal = onReveal,
            bodyTextScale = bodyTextScale,
            headerTextScale = headerTextScale,
            lineHeightEm = lineHeightEm,
            onOpenOverflow = onOpenOverflow,
        )
    } else {
        ReplyPreviewCardVisibleContent(
            post = post,
            hostNamespace = hostNamespace,
            previewType = previewType,
            overflowCount = overflowCount,
            overflowNumbers = overflowNumbers,
            unavailableRanges = unavailableRanges,
            onHide = onHide,
            headerTextScale = headerTextScale,
            bodyTextScale = bodyTextScale,
            lineHeightEm = lineHeightEm,
            onOpenPost = onOpenPost,
            onOpenOverflow = onOpenOverflow,
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

@Preview(showBackground = true)
@Composable
private fun ReplyPreviewCardPreview() {
    SlevoTheme {
        SharedTransitionLayout {
            AnimatedVisibility(visible = true) {
                ReplyPreviewCard(
                    post = ReplyPreviewPostUiModel(
                        postNumber = 12,
                        name = "投稿者名",
                        id = "sample-id",
                        body = "返信内容のプレビューを表示します。",
                        imageUrls = emptyList(),
                    ),
                    hostNamespace = "thread-post-20",
                    previewType = ImageReplyPreviewType.REPLY_SOURCE,
                    headerTextScale = 1f,
                    bodyTextScale = 1f,
                    lineHeightEm = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
                    onOpenPost = {},
                    onOpenOverflow = {},
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

@Preview(showBackground = true, name = "Reply preview with images")
@Composable
private fun ReplyPreviewCardWithImagesPreview() {
    SlevoTheme {
        SharedTransitionLayout {
            AnimatedVisibility(visible = true) {
                ReplyPreviewCard(
                    post = ReplyPreviewPostUiModel(
                        postNumber = 12,
                        name = "投稿者名",
                        id = "sample-id",
                        body = "画像の説明と本文を表示します。",
                        imageUrls = listOf(
                            "https://example.com/first.jpg",
                            "https://example.com/second.jpg",
                            "https://example.com/third.jpg",
                        ),
                    ),
                    hostNamespace = "thread-post-20",
                    previewType = ImageReplyPreviewType.REPLY_TARGET,
                    headerTextScale = 1f,
                    bodyTextScale = 1f,
                    lineHeightEm = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
                    onOpenPost = {},
                    onOpenOverflow = {},
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

@Preview(showBackground = true, name = "Image-only reply preview")
@Composable
private fun ReplyPreviewCardImageOnlyPreview() {
    SlevoTheme {
        SharedTransitionLayout {
            AnimatedVisibility(visible = true) {
                ReplyPreviewCard(
                    post = ReplyPreviewPostUiModel(
                        postNumber = 13,
                        name = "",
                        id = "sample-id",
                        body = "",
                        imageUrls = listOf("https://example.com/only.jpg"),
                    ),
                    hostNamespace = "thread-post-20",
                    previewType = ImageReplyPreviewType.REPLY_SOURCE,
                    headerTextScale = 1f,
                    bodyTextScale = 1f,
                    lineHeightEm = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
                    onOpenPost = {},
                    onOpenOverflow = {},
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

@Preview(showBackground = true, name = "Reply target with disjoint ranges")
@Composable
private fun ReplyPreviewCardWithDisjointUnavailableRangesPreview() {
    SlevoTheme {
        SharedTransitionLayout {
            AnimatedVisibility(visible = true) {
                ReplyPreviewCard(
                    post = ReplyPreviewPostUiModel(
                        postNumber = 8,
                        name = "返信先の投稿者",
                        id = "reply-target-id",
                        body = "取得済み候補の本文です。",
                        imageUrls = emptyList(),
                    ),
                    hostNamespace = "thread-post-20",
                    previewType = ImageReplyPreviewType.REPLY_TARGET,
                    unavailableRanges = listOf(
                        ReplyTargetUnavailableRange(101, 103),
                        ReplyTargetUnavailableRange(105, 200),
                    ),
                    headerTextScale = 1f,
                    bodyTextScale = 1f,
                    lineHeightEm = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
                    onOpenPost = {},
                    onOpenOverflow = {},
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

@Preview(showBackground = true, name = "Revealed NG reply target")
@Composable
private fun RevealedNgReplyPreviewCardPreview() {
    SlevoTheme {
        SharedTransitionLayout {
            AnimatedVisibility(visible = true) {
                ReplyPreviewCard(
                    post = ReplyPreviewPostUiModel(
                        postNumber = 14,
                        name = "一時表示中の投稿者",
                        id = "temporarily-visible-id",
                        body = "開示されたNG本文のプレビューです。\n二行目\n三行目\n四行目は省略されます。",
                        imageUrls = emptyList(),
                    ),
                    hostNamespace = "thread-post-20",
                    previewType = ImageReplyPreviewType.REPLY_TARGET,
                    overflowCount = 2,
                    overflowNumbers = listOf(14, 16, 18),
                    unavailableRanges = listOf(ReplyTargetUnavailableRange(15, Int.MAX_VALUE)),
                    onHide = {},
                    headerTextScale = 1f,
                    bodyTextScale = 1f,
                    lineHeightEm = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
                    onOpenPost = {},
                    onOpenOverflow = {},
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

@Preview(showBackground = true, name = "Hidden NG reply target")
@Composable
private fun HiddenNgReplyPreviewCardPreview() {
    SlevoTheme {
        SharedTransitionLayout {
            AnimatedVisibility(visible = true) {
                ReplyPreviewCard(
                    post = ReplyPreviewPostUiModel(
                        postNumber = 14,
                        name = "非表示中の投稿者",
                        id = "hidden-id",
                        body = "この本文は非表示中にcompositionへ入りません。",
                        imageUrls = emptyList(),
                    ),
                    hostNamespace = "thread-post-20",
                    previewType = ImageReplyPreviewType.REPLY_TARGET,
                    isNgHidden = true,
                    onReveal = {},
                    headerTextScale = 1f,
                    bodyTextScale = 1f,
                    lineHeightEm = TextDisplaySettingsConstraints.DEFAULT_LINE_HEIGHT,
                    onOpenPost = {},
                    onOpenOverflow = {},
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
