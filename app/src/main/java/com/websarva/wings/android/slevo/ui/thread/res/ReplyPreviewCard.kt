package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.data.model.TextDisplaySettingsConstraints
import com.websarva.wings.android.slevo.ui.common.ImageThumbnail
import com.websarva.wings.android.slevo.ui.common.transition.ImageReplyPreviewType
import com.websarva.wings.android.slevo.ui.common.transition.ImageSharedTransitionKeyFactory
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressRegistry
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressState

private val REPLY_PREVIEW_CONTENT_HORIZONTAL_PADDING = 12.dp
private val REPLY_PREVIEW_CONTENT_VERTICAL_PADDING = 8.dp

/** 返信先または返信元の内容と未取得範囲を状態に応じてカード内に表示する。 */
@Composable
internal fun ReplyPreviewCard(
    modifier: Modifier = Modifier,
    post: ReplyPreviewPostUiModel?,
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
    val colors = MaterialTheme.colorScheme
    val isReplySource = previewType == ImageReplyPreviewType.REPLY_SOURCE

    // --- Existing card surface and reply-source rail ---
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (previewType == ImageReplyPreviewType.REPLY_TARGET) {
                    Modifier.testTag("reply-preview-target-${post?.postNumber ?: "unavailable"}")
                } else {
                    Modifier
                },
            ),
        shape = if (isReplySource) RectangleShape else MaterialTheme.shapes.medium,
        color = if (isReplySource) Color.Transparent else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isReplySource && post != null) {
                        val railWidth = 2.dp
                        Modifier
                            .drawBehind {
                                val strokeWidth = railWidth.toPx()
                                drawLine(
                                    color = colors.outlineVariant,
                                    start = Offset(x = strokeWidth / 2, y = 0f),
                                    end = Offset(x = strokeWidth / 2, y = size.height),
                                    strokeWidth = strokeWidth,
                                )
                            }
                            .testTag("reply-preview-source-rail-${post.postNumber}")
                    } else {
                        Modifier
                    },
                ),
        ) {
            // --- State-specific header and post content ---
            // 未取得の投稿にはヘッダーや本文を表示しない。
            if (post != null && isNgHidden) {
                ReplyPreviewHiddenHeader(
                    postNumber = post.postNumber,
                    onReveal = onReveal,
                    headerTextScale = headerTextScale,
                    lineHeightEm = lineHeightEm,
                )
            } else if (post != null) {
                ReplyPreviewPostContent(
                    post = post,
                    hostNamespace = hostNamespace,
                    previewType = previewType,
                    onHide = onHide,
                    headerTextScale = headerTextScale,
                    bodyTextScale = bodyTextScale,
                    lineHeightEm = lineHeightEm,
                    onOpenPost = onOpenPost,
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

            // --- Shared overflow and unavailable-range footer ---
            ReplyPreviewFooter(
                previewType = previewType,
                overflowCount = overflowCount,
                overflowNumbers = overflowNumbers,
                unavailableRanges = unavailableRanges,
                onOpenOverflow = onOpenOverflow,
            )
        }
    }
}

/** 非表示中は投稿モデルを受け取らず、番号・状態・開示操作だけを構成する。 */
@Composable
private fun ReplyPreviewHiddenHeader(
    postNumber: Int,
    onReveal: (() -> Unit)?,
    headerTextScale: Float,
    lineHeightEm: Float,
) {
    // --- Hidden status and disclosure row ---
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {})
            .padding(
                horizontal = REPLY_PREVIEW_CONTENT_HORIZONTAL_PADDING,
                vertical = REPLY_PREVIEW_CONTENT_VERTICAL_PADDING,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reply-preview-header-$postNumber"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .testTag("reply-preview-hidden-status-$postNumber"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = postNumber.toString(),
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize * headerTextScale * 0.9f,
                    fontWeight = FontWeight.Medium,
                    lineHeight = lineHeightEm.em,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.reply_preview_ng_hidden),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            onReveal?.let { reveal ->
                ReplyTargetDisclosureAction(
                    postNumber = postNumber,
                    isRevealed = false,
                    onClick = reveal,
                )
            }
        }
    }
}

/** 通常表示・開示表示のヘッダーと本文・画像を既存のカード形式で構成する。 */
@Composable
private fun ReplyPreviewPostContent(
    post: ReplyPreviewPostUiModel,
    hostNamespace: String,
    previewType: ImageReplyPreviewType,
    onHide: (() -> Unit)?,
    headerTextScale: Float,
    bodyTextScale: Float,
    lineHeightEm: Float,
    onOpenPost: () -> Unit,
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
    // --- Content semantics and transition metadata ---
    val progressByUrl by ImageLoadProgressRegistry.progressByUrl.collectAsState()
    val openPostDescription = stringResource(
        if (previewType == ImageReplyPreviewType.REPLY_TARGET) {
            R.string.reply_preview_open_target_post
        } else {
            R.string.reply_preview_open_source_post
        },
        post.postNumber,
    )
    val imageDescription = stringResource(
        R.string.reply_preview_image_description,
        post.imageUrls.size,
        (post.imageUrls.size - 1).coerceAtLeast(0),
        post.postNumber,
    )
    val imageNamespace = ImageSharedTransitionKeyFactory.replyPreviewNamespace(
        hostNamespace = hostNamespace,
        previewType = previewType,
        targetPostNumber = post.postNumber,
    )
    val previewHeaderTextSize =
        MaterialTheme.typography.bodyMedium.fontSize * headerTextScale * 0.9f
    val previewBodyTextSize = MaterialTheme.typography.bodyMedium.fontSize * bodyTextScale * 0.9f

    // --- Existing clickable post content and inner spacing ---
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = openPostDescription,
                onClick = onOpenPost,
            )
            .padding(
                horizontal = REPLY_PREVIEW_CONTENT_HORIZONTAL_PADDING,
                vertical = REPLY_PREVIEW_CONTENT_VERTICAL_PADDING,
            ),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ReplyPreviewCardPostHeader(
            post = post,
            headerTextSize = previewHeaderTextSize,
            lineHeightEm = lineHeightEm,
            onHide = onHide,
        )
        ReplyPreviewPostBody(
            post = post,
            imageNamespace = imageNamespace,
            imageDescription = imageDescription,
            progressByUrl = progressByUrl,
            imageLoadFailureByUrl = imageLoadFailureByUrl,
            textSize = previewBodyTextSize,
            lineHeightEm = lineHeightEm,
            onImageClick = onImageClick,
            onImageLongPress = onImageLongPress,
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

/** 番号・名前・IDと開示操作を既存の一行ヘッダーに表示する。 */
@Composable
private fun ReplyPreviewCardPostHeader(
    post: ReplyPreviewPostUiModel,
    headerTextSize: TextUnit,
    lineHeightEm: Float,
    onHide: (() -> Unit)?,
) {
    // --- Metadata mapping ---
    val header = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
            append(post.postNumber.toString())
        }
        if (post.name.isNotBlank()) append("  ${post.name}")
        if (post.id.isNotBlank()) append("  ID:${post.id}")
    }
    // --- Existing header layout ---
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reply-preview-header-${post.postNumber}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = header,
            modifier = Modifier
                .weight(1f)
                .testTag("reply-preview-metadata-${post.postNumber}"),
            fontSize = headerTextSize,
            fontWeight = FontWeight.Normal,
            lineHeight = lineHeightEm.em,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        onHide?.let { hide ->
            ReplyTargetDisclosureAction(
                postNumber = post.postNumber,
                isRevealed = true,
                onClick = hide,
            )
        }
    }
}

/** 本文と先頭画像を表示し、画像操作を既存の共有遷移へ接続する。 */
@Composable
private fun ReplyPreviewPostBody(
    post: ReplyPreviewPostUiModel,
    imageNamespace: String,
    imageDescription: String,
    progressByUrl: Map<String, ImageLoadProgressState>,
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType>,
    textSize: TextUnit,
    lineHeightEm: Float,
    onImageClick: (String, List<String>, Int, String) -> Unit,
    onImageLongPress: (String, List<String>) -> Unit,
    onImageLoadStart: (String) -> Unit,
    onImageLoadError: (String, ImageLoadFailureType) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onImageRetry: (String) -> Unit,
    enableSharedElement: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    // --- Body and optional thumbnail preview ---
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (post.body.isNotBlank()) {
            Text(
                text = post.body,
                modifier = Modifier
                    .weight(1f)
                    .testTag("reply-preview-body-${post.postNumber}"),
                fontSize = textSize,
                lineHeight = lineHeightEm.em,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (post.imageUrls.isNotEmpty()) {
            Box(modifier = Modifier.size(56.dp)) {
                ImageThumbnail(
                    url = post.imageUrls.first(),
                    imageUrls = post.imageUrls,
                    imageIndex = 0,
                    transitionNamespace = imageNamespace,
                    modifier = Modifier.size(56.dp),
                    onImageClick = onImageClick,
                    onImageLongPress = onImageLongPress,
                    imageLoadFailureByUrl = imageLoadFailureByUrl,
                    loadProgressByUrl = progressByUrl,
                    onImageLoadStart = onImageLoadStart,
                    onImageLoadError = onImageLoadError,
                    onImageLoadSuccess = onImageLoadSuccess,
                    onImageRetry = onImageRetry,
                    enableSharedElement = enableSharedElement,
                    imageContentDescription = imageDescription,
                    cacheKeyPrefix = "reply-preview-${post.postNumber}",
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
                if (post.imageUrls.size > 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .clearAndSetSemantics { },
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(topStart = 6.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.reply_preview_image_more,
                                post.imageUrls.size - 1,
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
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
