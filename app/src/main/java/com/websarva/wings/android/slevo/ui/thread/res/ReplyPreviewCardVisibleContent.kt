package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibilityScope
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.common.ImageThumbnail
import com.websarva.wings.android.slevo.ui.common.transition.ImageReplyPreviewType
import com.websarva.wings.android.slevo.ui.common.transition.ImageSharedTransitionKeyFactory
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressState
import com.websarva.wings.android.slevo.ui.util.ImageLoadProgressRegistry

/** 返信プレビューの通常表示・開示表示を既存のカード形式で構成する。 */
@Composable
internal fun ReplyPreviewCardVisibleContent(
    post: ReplyPreviewPostUiModel,
    hostNamespace: String,
    previewType: ImageReplyPreviewType,
    overflowCount: Int,
    overflowNumbers: List<Int>,
    unavailableRanges: List<ReplyTargetUnavailableRange>,
    onHide: (() -> Unit)?,
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
    val progressByUrl by ImageLoadProgressRegistry.progressByUrl.collectAsState()
    val colors = MaterialTheme.colorScheme
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
    val isReplySource = previewType == ImageReplyPreviewType.REPLY_SOURCE

    // --- Existing preview surface and reply-source rail ---
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (previewType == ImageReplyPreviewType.REPLY_TARGET) {
                    Modifier.testTag("reply-preview-target-${post.postNumber}")
                } else {
                    Modifier
                },
            ),
        shape = if (isReplySource) RectangleShape else RoundedCornerShape(8.dp),
        color = if (isReplySource) Color.Transparent else colors.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isReplySource) {
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
                ReplyPreviewCardPostHeader(
                    post = post,
                    headerTextSize = previewHeaderTextSize,
                    lineHeightEm = lineHeightEm,
                    onHide = onHide,
                )
                ReplyPreviewCardPostContent(
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

            // --- Optional complete-list action ---
            if (overflowCount > 0 && overflowNumbers.isNotEmpty()) {
                ReplyPreviewCardOverflowAction(
                    previewType = previewType,
                    overflowCount = overflowCount,
                    overflowNumbers = overflowNumbers,
                    textSize = previewBodyTextSize,
                    lineHeightEm = lineHeightEm,
                    onOpenOverflow = onOpenOverflow,
                )
            }

            // --- Unavailable target ranges ---
            if (previewType == ImageReplyPreviewType.REPLY_TARGET) {
                ReplyTargetUnavailableRangesRow(
                    ranges = unavailableRanges,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
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
    val header = buildList {
        add(post.postNumber.toString())
        if (post.name.isNotBlank()) add(post.name)
        if (post.id.isNotBlank()) add("ID:${post.id}")
    }.joinToString("  ")
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
private fun ReplyPreviewCardPostContent(
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
    // --- Text-only preview ---
    if (post.imageUrls.isEmpty()) {
        if (post.body.isNotBlank()) {
            Text(
                text = post.body,
                modifier = Modifier.testTag("reply-preview-body-${post.postNumber}"),
                fontSize = textSize,
                lineHeight = lineHeightEm.em,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        return
    }

    // --- Text and thumbnail preview ---
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

/** 省略分の取得済み返信先を一覧で開く操作を表示する。 */
@Composable
private fun ReplyPreviewCardOverflowAction(
    previewType: ImageReplyPreviewType,
    overflowCount: Int,
    overflowNumbers: List<Int>,
    textSize: TextUnit,
    lineHeightEm: Float,
    onOpenOverflow: (List<Int>) -> Unit,
) {
    // --- Accessibility action ---
    val descriptionResource = if (previewType == ImageReplyPreviewType.REPLY_TARGET) {
        R.string.reply_preview_open_target_list
    } else {
        R.string.reply_preview_open_source_list
    }
    val overflowDescription = stringResource(descriptionResource, overflowNumbers.size)
    // --- Existing overflow row ---
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
        color = if (previewType == ImageReplyPreviewType.REPLY_SOURCE) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Text(
            text = stringResource(R.string.reply_preview_other_count, overflowCount),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            fontSize = textSize,
            lineHeight = lineHeightEm.em,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
