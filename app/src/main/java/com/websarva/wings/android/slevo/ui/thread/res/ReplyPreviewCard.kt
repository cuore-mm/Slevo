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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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

/** 返信先または返信元一件を小型ヘッダー・本文・画像で表示する。 */
@Composable
internal fun ReplyPreviewCard(
    post: ReplyPreviewPostUiModel,
    hostNamespace: String,
    previewType: ImageReplyPreviewType,
    overflowCount: Int = 0,
    overflowNumbers: List<Int> = emptyList(),
    unavailableRanges: List<ReplyTargetUnavailableRange> = emptyList(),
    onHide: (() -> Unit)? = null,
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
    // --- Accessibility labels and display scale ---
    val colors = MaterialTheme.colorScheme
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

    // --- Preview surface and reply-source rail ---
    val isReplySource = previewType == ImageReplyPreviewType.REPLY_SOURCE
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = if (isReplySource) RectangleShape else RoundedCornerShape(8.dp),
        color = if (isReplySource) Color.Transparent else colors.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isReplySource) {
                        Modifier
                            .drawBehind {
                                val railWidth = 2.dp.toPx()
                                drawLine(
                                    color = colors.outlineVariant,
                                    start = Offset(x = railWidth / 2, y = 0f),
                                    end = Offset(x = railWidth / 2, y = size.height),
                                    strokeWidth = railWidth,
                                )
                            }
                            .testTag("reply-preview-source-rail-${post.postNumber}")
                    } else {
                        Modifier
                    },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = openPostDescription,
                        onClick = onOpenPost,
                    )
                    .heightIn(min = 48.dp)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val header = buildList {
                    add(post.postNumber.toString())
                    if (post.name.isNotBlank()) add(post.name)
                    if (post.id.isNotBlank()) add("ID:${post.id}")
                }.joinToString("  ")
                Text(
                    text = header,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = previewHeaderTextSize,
                    fontWeight = FontWeight.Normal,
                    lineHeight = lineHeightEm.em,
                    color = colors.onSurfaceVariant,
                )
                if (post.imageUrls.isEmpty()) {
                    if (post.body.isNotBlank()) {
                        Text(
                            text = post.body,
                            fontSize = previewBodyTextSize,
                            lineHeight = lineHeightEm.em,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.onSurface,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (post.body.isNotBlank()) {
                            Text(
                                text = post.body,
                                modifier = Modifier.weight(1f),
                                fontSize = previewBodyTextSize,
                                lineHeight = lineHeightEm.em,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                color = colors.onSurface,
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
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
                                    color = colors.primary.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(topStart = 6.dp),
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.reply_preview_image_more,
                                            post.imageUrls.size - 1,
                                        ),
                                        modifier = Modifier.padding(
                                            horizontal = 4.dp,
                                            vertical = 2.dp
                                        ),
                                        color = colors.onPrimary,
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
            // --- Optional complete-list action ---
            if (overflowCount > 0 && overflowNumbers.isNotEmpty()) {
                val overflowDescription = stringResource(
                    if (previewType == ImageReplyPreviewType.REPLY_TARGET) {
                        R.string.reply_preview_open_target_list
                    } else {
                        R.string.reply_preview_open_source_list
                    },
                    overflowNumbers.size,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = overflowDescription,
                            onClick = { onOpenOverflow(overflowNumbers) },
                        )
                        .semantics { contentDescription = overflowDescription },
                    color = if (isReplySource) Color.Transparent else colors.surfaceVariant,
                ) {
                    Text(
                        text = stringResource(R.string.reply_preview_other_count, overflowCount),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        fontSize = previewBodyTextSize,
                        lineHeight = lineHeightEm.em,
                        color = colors.primary,
                    )
                }
            }
            if (previewType == ImageReplyPreviewType.REPLY_TARGET) {
                ReplyTargetUnavailableRangesRow(
                    ranges = unavailableRanges,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            if (onHide != null) {
                val hideDescription = stringResource(
                    R.string.reply_preview_hide_target,
                    post.postNumber,
                )
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { contentDescription = hideDescription },
                    onClick = onHide,
                ) {
                    Text(stringResource(R.string.reply_preview_hide))
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
