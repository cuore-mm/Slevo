package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.data.util.ReplyAnchorParser
import com.websarva.wings.android.slevo.ui.common.transition.ImageReplyPreviewType
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.thread.item.rememberHighlightedText
import com.websarva.wings.android.slevo.ui.thread.state.PostBodySegment
import com.websarva.wings.android.slevo.ui.thread.state.PostReplyPreviewUiState
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetCandidateStatus
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetCandidateUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetPreviewUiState
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.util.ImageLoadFailureType
import com.websarva.wings.android.slevo.ui.util.ResolvedUrl
import com.websarva.wings.android.slevo.ui.util.rememberUrlAnnotatedString
import com.websarva.wings.android.slevo.ui.util.resolveUrl
import kotlinx.coroutines.CoroutineScope

private const val BodyUrlTag = "URL"
private const val BodyReplyTag = "REPLY"

/**
 * 本文内のタップ対象を表すヒット結果。
 *
 * URL/返信番号のどちらか一方のみを保持し、両方 null は未ヒット扱いとする。
 */
private data class BodyHit(
    val url: String?,
    val reply: String?,
)

/**
 * 本文のタップ判定と描画を担当する。
 *
 * URL/返信番号/本文押下の状態を切り替える。
 */
@Composable
internal fun PostItemBody(
    post: ThreadPostUiModel,
    bodyTextStyle: TextStyle,
    headerTextScale: Float,
    lineHeightEm: Float,
    searchQuery: String,
    pressedUrl: String?,
    pressedReply: String?,
    scope: CoroutineScope,
    onPressedUrlChange: (String?) -> Unit,
    onPressedReplyChange: (String?) -> Unit,
    onContentPressedChange: (Boolean) -> Unit,
    onRequestMenu: () -> Unit,
    onReplyClick: ((Int) -> Unit)?,
    onReplyNumbersClick: ((List<Int>) -> Unit)?,
    revealedReplyTargetNumbers: Set<Int> = emptySet(),
    onReplyTargetClick: ((Int) -> Unit)? = null,
    onReplyTargetNumbersClick: ((List<Int>) -> Unit)? = null,
    onReplyTargetDisclosureChange: ((Int, Boolean) -> Unit)? = null,
    maxPostNumber: Int,
    replyPreviewState: PostReplyPreviewUiState?,
    transitionNamespace: String,
    onImageClick: (String, List<String>, Int, String) -> Unit,
    onImageLongPress: (String, List<String>) -> Unit,
    imageLoadFailureByUrl: Map<String, ImageLoadFailureType>,
    onImageLoadStart: (String) -> Unit,
    onImageLoadError: (String, ImageLoadFailureType) -> Unit,
    onImageLoadSuccess: (String) -> Unit,
    onImageRetry: (String) -> Unit,
    enableSharedElement: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onUrlClick: (String) -> Unit,
    onThreadUrlClick: (AppRoute.Thread) -> Unit,
    onBodyClick: (() -> Unit)? = null,
) {
    // --- フィードバック ---
    val haptic = LocalHapticFeedback.current

    // --- 文字列処理 ---
    val annotatedText = rememberUrlAnnotatedString(
        text = post.body.content,
        pressedUrl = pressedUrl,
        pressedReply = pressedReply,
    )
    val highlightBackground = MaterialTheme.colorScheme.tertiaryContainer
    val highlightedText = rememberHighlightedText(
        baseText = annotatedText,
        rawContent = post.body.content,
        searchQuery = searchQuery,
        highlightColor = highlightBackground
    )

    Column(horizontalAlignment = Alignment.Start) {
        // --- BEアイコン ---
        if (post.header.beIconUrl.isNotBlank()) {
            AsyncImage(
                model = post.header.beIconUrl,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
        }
        // --- Offset-preserving text and reply-preview segments ---
        val bodySegments = replyPreviewState?.bodySegments?.takeIf { it.isNotEmpty() }
            ?: listOf(PostBodySegment.TextRange(0, post.body.content.length))
        bodySegments.forEach { segment ->
            when (segment) {
                is PostBodySegment.TextRange -> {
                    val start = segment.startOffset.coerceIn(0, post.body.content.length)
                    val end = segment.endOffset.coerceIn(start, post.body.content.length)
                    if (start < end) {
                        PostBodyTextSegment(
                            text = highlightedText.subSequence(start, end),
                            bodyTextStyle = bodyTextStyle,
                            lineHeightEm = lineHeightEm,
                            scope = scope,
                            haptic = haptic,
                            onPressedUrlChange = onPressedUrlChange,
                            onPressedReplyChange = onPressedReplyChange,
                            onContentPressedChange = onContentPressedChange,
                            onRequestMenu = onRequestMenu,
                            onReplyClick = onReplyClick,
                            onReplyNumbersClick = onReplyNumbersClick,
                            maxPostNumber = maxPostNumber,
                            onUrlClick = onUrlClick,
                            onThreadUrlClick = onThreadUrlClick,
                            onBodyClick = onBodyClick,
                        )
                    }
                }

                is PostBodySegment.ReplyTarget -> {
                    val target = segment.preview
                    val transitionScope = sharedTransitionScope ?: return@forEach
                    val visibilityScope = animatedVisibilityScope ?: return@forEach
                    val selected = target.selectedCandidate
                    val openCandidates = {
                        onReplyTargetNumbersClick?.invoke(target.referencedNumbers)
                            ?: onReplyNumbersClick?.invoke(target.referencedNumbers)
                    }
                    if (selected == null) {
                        ReplyTargetUnavailableRangesRow(
                            ranges = target.unavailableRanges,
                        )
                    } else {
                        val isRevealed = selected.postNumber in revealedReplyTargetNumbers
                        val selectedPost = selected.post
                        if (selectedPost != null && selected.status != ReplyTargetCandidateStatus.UNAVAILABLE) {
                            val isNgTarget = selected.status == ReplyTargetCandidateStatus.NG
                            ReplyPreviewCard(
                                modifier = Modifier.padding(vertical = 4.dp),
                                post = selectedPost,
                                hostNamespace = transitionNamespace,
                                previewType = ImageReplyPreviewType.REPLY_TARGET,
                                overflowCount = target.overflowCount,
                                overflowNumbers = target.referencedNumbers,
                                unavailableRanges = target.unavailableRanges,
                                onHide = if (isNgTarget && isRevealed) {
                                    { onReplyTargetDisclosureChange?.invoke(selected.postNumber, false) }
                                } else null,
                                isNgHidden = isNgTarget && !isRevealed,
                                onReveal = if (isNgTarget && !isRevealed) {
                                    { onReplyTargetDisclosureChange?.invoke(selected.postNumber, true) }
                                } else null,
                                headerTextScale = headerTextScale,
                                bodyTextScale = bodyTextStyle.fontSize.value /
                                        MaterialTheme.typography.bodyMedium.fontSize.value,
                                lineHeightEm = lineHeightEm,
                                onOpenPost = {
                                    if (onReplyTargetClick != null) {
                                        onReplyTargetClick(selected.postNumber)
                                    } else {
                                        onReplyClick?.invoke(selected.postNumber)
                                    }
                                },
                                onOpenOverflow = { openCandidates() },
                                onImageClick = onImageClick,
                                onImageLongPress = onImageLongPress,
                                imageLoadFailureByUrl = imageLoadFailureByUrl,
                                onImageLoadStart = onImageLoadStart,
                                onImageLoadError = onImageLoadError,
                                onImageLoadSuccess = onImageLoadSuccess,
                                onImageRetry = onImageRetry,
                                enableSharedElement = enableSharedElement,
                                sharedTransitionScope = transitionScope,
                                animatedVisibilityScope = visibilityScope,
                            )
                        } else {
                            // Guard: 未取得候補は本文へ追加せず、未取得範囲だけを表示する。
                            ReplyTargetUnavailableRangesRow(
                                ranges = target.unavailableRanges,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 本文の一範囲を元の注釈・タップ・長押し操作付きで描画する。 */
@Composable
private fun PostBodyTextSegment(
    text: AnnotatedString,
    bodyTextStyle: TextStyle,
    lineHeightEm: Float,
    scope: CoroutineScope,
    haptic: HapticFeedback,
    onPressedUrlChange: (String?) -> Unit,
    onPressedReplyChange: (String?) -> Unit,
    onContentPressedChange: (Boolean) -> Unit,
    onRequestMenu: () -> Unit,
    onReplyClick: ((Int) -> Unit)?,
    onReplyNumbersClick: ((List<Int>) -> Unit)?,
    maxPostNumber: Int,
    onUrlClick: (String) -> Unit,
    onThreadUrlClick: (AppRoute.Thread) -> Unit,
    onBodyClick: (() -> Unit)?,
) {
    // --- Segment-local hit-test layout ---
    var contentLayout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    // --- Existing annotated text interactions ---
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .postBodyGestures(
                highlightedText = text,
                layoutProvider = { contentLayout },
                scope = scope,
                haptic = haptic,
                onPressedUrlChange = onPressedUrlChange,
                onPressedReplyChange = onPressedReplyChange,
                onContentPressedChange = onContentPressedChange,
                onRequestMenu = onRequestMenu,
                onReplyClick = onReplyClick,
                onReplyNumbersClick = onReplyNumbersClick,
                maxPostNumber = maxPostNumber,
                onUrlClick = onUrlClick,
                onThreadUrlClick = onThreadUrlClick,
                onBodyClick = onBodyClick,
            ),
        text = text,
        style = bodyTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        lineHeight = lineHeightEm.em,
        onTextLayout = { contentLayout = it },
    )
}

/**
 * 本文のタップ対象を判定する。
 *
 * URL → 返信番号の順で検索し、見つからない場合は未ヒットを返す。
 */
private fun findBodyHit(
    text: AnnotatedString,
    layout: TextLayoutResult,
    offset: Offset,
): BodyHit {
    val pos = layout.getOffsetForPosition(offset)
    val url = text.getStringAnnotations(BodyUrlTag, pos, pos).firstOrNull()?.item
    if (url != null) {
        return BodyHit(url = url, reply = null)
    }
    val reply = text.getStringAnnotations(BodyReplyTag, pos, pos).firstOrNull()?.item
    return BodyHit(url = null, reply = reply)
}

/**
 * 本文のジェスチャー処理をまとめる。
 *
 * レイアウト未取得時は本文押下/長押しメニューとして扱う。
 */
private fun Modifier.postBodyGestures(
    highlightedText: AnnotatedString,
    layoutProvider: () -> TextLayoutResult?,
    scope: CoroutineScope,
    haptic: HapticFeedback,
    onPressedUrlChange: (String?) -> Unit,
    onPressedReplyChange: (String?) -> Unit,
    onContentPressedChange: (Boolean) -> Unit,
    onRequestMenu: () -> Unit,
    onReplyClick: ((Int) -> Unit)?,
    onReplyNumbersClick: ((List<Int>) -> Unit)?,
    maxPostNumber: Int,
    onUrlClick: (String) -> Unit,
    onThreadUrlClick: (AppRoute.Thread) -> Unit,
    onBodyClick: (() -> Unit)?,
): Modifier {
    return pointerInput(Unit) {
        // --- タップ判定 ---
        detectTapGestures(
            // --- 押下 ---
            onPress = { offset ->
                layoutProvider()?.let { layout ->
                    // --- ヒット判定 ---
                    val hit = findBodyHit(highlightedText, layout, offset)
                    handleBodyPressFeedback(
                        scope = scope,
                        hit = hit,
                        onPressedUrlChange = onPressedUrlChange,
                        onPressedReplyChange = onPressedReplyChange,
                        onContentPressedChange = onContentPressedChange,
                        awaitRelease = { awaitRelease() }
                    )
                } ?: run {
                    // レイアウト未取得時は本文押下として扱う。
                    handlePressFeedback(
                        scope = scope,
                        onFeedbackStart = { onContentPressedChange(true) },
                        onFeedbackEnd = { onContentPressedChange(false) },
                        awaitRelease = { awaitRelease() }
                    )
                }
            },
            // --- タップ ---
            onTap = { offset ->
                layoutProvider()?.let { layout ->
                    val hit = findBodyHit(highlightedText, layout, offset)
                    hit.url?.let { url ->
                        val resolved = resolveUrl(url)
                        if (resolved is ResolvedUrl.Thread) {
                            val boardUrl = "https://${resolved.host}/${resolved.boardKey}/"
                            val route = AppRoute.Thread(
                                threadKey = resolved.threadKey,
                                boardUrl = boardUrl,
                                boardName = resolved.boardKey,
                                threadTitle = null
                            )
                            onThreadUrlClick(route)
                        } else {
                            // スレッドURLでない場合は通常のURLとして開く。
                            onUrlClick(url)
                        }
                    }
                    hit.reply?.let { anchor ->
                        val numbers = ReplyAnchorParser.extractReferencedNumbers(
                            content = if (anchor.startsWith(">>")) anchor else ">>$anchor",
                            maxPostNumber = maxPostNumber,
                        )
                        when {
                            numbers.size == 1 -> onReplyClick?.invoke(numbers.single())
                            numbers.size > 1 -> onReplyNumbersClick?.invoke(numbers)
                        }
                    }
                    if (hit.url == null && hit.reply == null) {
                        onBodyClick?.invoke()
                    }
                }
            },
            // --- 長押し ---
            onLongPress = { offset ->
                layoutProvider()?.let { layout ->
                    val hit = findBodyHit(highlightedText, layout, offset)
                    if (hit.url == null && hit.reply == null) {
                        // リンク/返信以外は投稿メニューを表示する。
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRequestMenu()
                    }
                } ?: run {
                    // レイアウト未取得時は長押しメニュー扱いにする。
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRequestMenu()
                }
            }
        )
    }
}

/**
 * 押下対象に応じたフィードバック処理を実行する。
 *
 * URL/返信/本文のどれを押したかに応じて押下状態を切り替える。
 */
private suspend fun handleBodyPressFeedback(
    scope: CoroutineScope,
    hit: BodyHit,
    onPressedUrlChange: (String?) -> Unit,
    onPressedReplyChange: (String?) -> Unit,
    onContentPressedChange: (Boolean) -> Unit,
    awaitRelease: suspend () -> Unit,
) {
    when {
        hit.url != null -> {
            handlePressFeedback(
                scope = scope,
                feedbackDelayMillis = 0L,
                onFeedbackStart = { onPressedUrlChange(hit.url) },
                onFeedbackEnd = { onPressedUrlChange(null) },
                awaitRelease = awaitRelease
            )
        }

        hit.reply != null -> {
            handlePressFeedback(
                scope = scope,
                feedbackDelayMillis = 0L,
                onFeedbackStart = { onPressedReplyChange(hit.reply) },
                onFeedbackEnd = { onPressedReplyChange(null) },
                awaitRelease = awaitRelease
            )
        }

        else -> {
            handlePressFeedback(
                scope = scope,
                onFeedbackStart = { onContentPressedChange(true) },
                onFeedbackEnd = { onContentPressedChange(false) },
                awaitRelease = awaitRelease
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PostItemBodyPreview() {
    val scope = rememberCoroutineScope()
    var pressedUrl by remember { mutableStateOf<String?>(null) }
    var pressedReply by remember { mutableStateOf<String?>(null) }

    PostItemBody(
        post = ThreadPostUiModel(
            header = ThreadPostUiModel.Header(
                name = "風吹けば名無し",
                email = "sage",
                date = "2025/12/16(火) 12:34:56.78",
                id = "testid",
                beRank = "PLT(2000)",
                beIconUrl = "https://img.5ch.net/ico/1fu.gif",
            ),
            body = ThreadPostUiModel.Body(
                content = "リンク https://example.com と >>12 を含む本文",
            ),
            meta = ThreadPostUiModel.Meta(
                urlFlags = ReplyInfo.HAS_OTHER_URL or ReplyInfo.HAS_THREAD_URL
            ),
        ),
        bodyTextStyle = MaterialTheme.typography.bodyMedium,
        headerTextScale = 1f,
        lineHeightEm = 1.4f,
        searchQuery = "本文",
        pressedUrl = pressedUrl,
        pressedReply = pressedReply,
        scope = scope,
        onPressedUrlChange = { },
        onPressedReplyChange = { },
        onContentPressedChange = {},
        onRequestMenu = {},
        onReplyClick = {},
        onReplyNumbersClick = {},
        maxPostNumber = 1,
        replyPreviewState = null,
        transitionNamespace = "preview-post",
        onImageClick = { _, _, _, _ -> },
        onImageLongPress = { _, _ -> },
        imageLoadFailureByUrl = emptyMap(),
        onImageLoadStart = {},
        onImageLoadError = { _, _ -> },
        onImageLoadSuccess = {},
        onImageRetry = {},
        enableSharedElement = false,
        sharedTransitionScope = null,
        animatedVisibilityScope = null,
        onUrlClick = {},
        onThreadUrlClick = {},
    )
}

@Preview(showBackground = true, name = "Post body with reply target preview")
@Composable
private fun PostItemBodyWithReplyTargetPreview() {
    // --- Preview content and target state ---
    val body = ">>12-2147483647\n返信本文の一行目\n返信本文の二行目"
    val replyTarget = ReplyTargetPreviewUiState(
        lineIndex = 0,
        lineStartOffset = 0,
        lineEndOffset = 15,
        lineBreakEndOffset = 16,
        anchorLine = ">>12-2147483647",
        candidates = listOf(
            ReplyTargetCandidateUiModel(
                postNumber = 12,
                status = ReplyTargetCandidateStatus.AVAILABLE,
                post = ReplyPreviewPostUiModel(
                    postNumber = 12,
                    name = "返信先の投稿者",
                    id = "target-id",
                    body = "返信先本文の一行目\n返信先本文の二行目",
                    imageUrls = emptyList(),
                ),
            ),
        ),
        selectedCandidate = ReplyTargetCandidateUiModel(
            postNumber = 12,
            status = ReplyTargetCandidateStatus.AVAILABLE,
            post = ReplyPreviewPostUiModel(
                postNumber = 12,
                name = "返信先の投稿者",
                id = "target-id",
                body = "返信先本文の一行目\n返信先本文の二行目",
                imageUrls = emptyList(),
            ),
        ),
        unavailableRanges = listOf(ReplyTargetUnavailableRange(13, Int.MAX_VALUE)),
    )
    val replyPreviewState = PostReplyPreviewUiState(
        replyTargets = listOf(replyTarget),
        bodySegments = listOf(
            PostBodySegment.ReplyTarget(replyTarget),
            PostBodySegment.TextRange(startOffset = 16, endOffset = body.length),
        ),
    )

    // --- Body composition with shared-transition scopes ---
    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            PostItemBody(
                post = ThreadPostUiModel(
                    header = ThreadPostUiModel.Header(
                        name = "風吹けば名無し",
                        email = "sage",
                        date = "2025/12/16(火) 12:34:56.78",
                        id = "reply-post-id",
                    ),
                    body = ThreadPostUiModel.Body(content = body),
                    meta = ThreadPostUiModel.Meta(
                        urlFlags = ReplyInfo.HAS_OTHER_URL or ReplyInfo.HAS_THREAD_URL,
                    ),
                ),
                bodyTextStyle = MaterialTheme.typography.bodyMedium,
                headerTextScale = 1f,
                lineHeightEm = 1.4f,
                searchQuery = "",
                pressedUrl = null,
                pressedReply = null,
                scope = rememberCoroutineScope(),
                onPressedUrlChange = {},
                onPressedReplyChange = {},
                onContentPressedChange = {},
                onRequestMenu = {},
                onReplyClick = {},
                onReplyNumbersClick = {},
                maxPostNumber = 20,
                replyPreviewState = replyPreviewState,
                transitionNamespace = "preview-post-body-with-reply-target",
                onImageClick = { _, _, _, _ -> },
                onImageLongPress = { _, _ -> },
                imageLoadFailureByUrl = emptyMap(),
                onImageLoadStart = {},
                onImageLoadError = { _, _ -> },
                onImageLoadSuccess = {},
                onImageRetry = {},
                enableSharedElement = false,
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
                onUrlClick = {},
                onThreadUrlClick = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "Multiple reply target lines and NG state")
@Composable
private fun PostItemBodyWithMultipleReplyTargetsPreview() {
    val body = ">>1 >>10-2147483647\nmiddle\n>>8"
    val ngCandidate = ReplyTargetCandidateUiModel(
        postNumber = 1,
        status = ReplyTargetCandidateStatus.NG,
        post = ReplyPreviewPostUiModel(
            postNumber = 1,
            name = "",
            id = "hidden-id",
            body = "NG本文は初期表示しません",
            imageUrls = emptyList(),
        ),
    )
    val normalCandidate = ReplyTargetCandidateUiModel(
        postNumber = 8,
        status = ReplyTargetCandidateStatus.AVAILABLE,
        post = ReplyPreviewPostUiModel(
            postNumber = 8,
            name = "返信先の投稿者",
            id = "target-id",
            body = "取得済み返信先の本文です。",
            imageUrls = emptyList(),
        ),
    )
    val ngTarget = ReplyTargetPreviewUiState(
        lineIndex = 0,
        lineStartOffset = 0,
        lineEndOffset = 19,
        lineBreakEndOffset = 20,
        anchorLine = ">>1 >>10-2147483647",
        candidates = listOf(ngCandidate),
        selectedCandidate = ngCandidate,
        unavailableRanges = listOf(ReplyTargetUnavailableRange(10, Int.MAX_VALUE)),
    )
    val normalTarget = ReplyTargetPreviewUiState(
        lineIndex = 2,
        lineStartOffset = 27,
        lineEndOffset = 30,
        lineBreakEndOffset = 30,
        anchorLine = ">>8",
        candidates = listOf(normalCandidate),
        selectedCandidate = normalCandidate,
        unavailableRanges = emptyList(),
    )
    val state = PostReplyPreviewUiState(
        replyTargets = listOf(ngTarget, normalTarget),
        bodySegments = listOf(
            PostBodySegment.ReplyTarget(ngTarget),
            PostBodySegment.TextRange(startOffset = 20, endOffset = 26),
            PostBodySegment.ReplyTarget(normalTarget),
        ),
    )

    SharedTransitionLayout {
        AnimatedVisibility(visible = true) {
            PostItemBody(
                post = ThreadPostUiModel(
                    header = ThreadPostUiModel.Header(
                        name = "風吹けば名無し",
                        email = "",
                        date = "2026/10/22",
                        id = "host-id",
                    ),
                    body = ThreadPostUiModel.Body(content = body),
                ),
                bodyTextStyle = MaterialTheme.typography.bodyMedium,
                headerTextScale = 1f,
                lineHeightEm = 1.25f,
                searchQuery = "",
                pressedUrl = null,
                pressedReply = null,
                scope = rememberCoroutineScope(),
                onPressedUrlChange = {},
                onPressedReplyChange = {},
                onContentPressedChange = {},
                onRequestMenu = {},
                onReplyClick = {},
                onReplyNumbersClick = {},
                onReplyTargetClick = {},
                onReplyTargetNumbersClick = { _ -> },
                onReplyTargetDisclosureChange = { _, _ -> },
                maxPostNumber = 9,
                replyPreviewState = state,
                transitionNamespace = "preview-post-body-multiple-targets",
                onImageClick = { _, _, _, _ -> },
                onImageLongPress = { _, _ -> },
                imageLoadFailureByUrl = emptyMap(),
                onImageLoadStart = {},
                onImageLoadError = { _, _ -> },
                onImageLoadSuccess = {},
                onImageRetry = {},
                enableSharedElement = false,
                sharedTransitionScope = this@SharedTransitionLayout,
                animatedVisibilityScope = this,
                onUrlClick = {},
                onThreadUrlClick = {},
            )
        }
    }
}
