package com.websarva.wings.android.slevo.ui.thread.res

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.websarva.wings.android.slevo.ui.common.transition.ImageReplyPreviewType
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetCandidateStatus
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetCandidateUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetDisclosureKey
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetPreviewUiState
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetUnavailableRange

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true, name = "Reply target with disjoint ranges")
@Composable
private fun ReplyTargetDisjointRangesPreview() {
    val candidate = replyTargetCandidate(8, ReplyTargetCandidateStatus.AVAILABLE)
    val target = replyTargetPreview(
        anchorLine = ">>8,101-103,105-200",
        candidates = listOf(candidate),
        selectedCandidate = candidate,
        unavailableRanges = listOf(
            ReplyTargetUnavailableRange(101, 103),
            ReplyTargetUnavailableRange(105, 200),
        ),
    )
    ReplyTargetScenarioContent(
        target = target,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(
    showBackground = true,
    name = "Reply target in narrow width",
    widthDp = 280,
    heightDp = 540,
    fontScale = 1.3f,
)
@Composable
private fun ReplyTargetNarrowLongRangePreview() {
    val candidate = replyTargetCandidate(12, ReplyTargetCandidateStatus.AVAILABLE)
    val target = replyTargetPreview(
        anchorLine = ">>12-2147483647",
        candidates = listOf(candidate),
        selectedCandidate = candidate,
        unavailableRanges = listOf(ReplyTargetUnavailableRange(13, Int.MAX_VALUE)),
    )
    ReplyTargetScenarioContent(
        target = target,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true, name = "NG target and unavailable range")
@Composable
private fun ReplyTargetNgAndRangePreview() {
    val candidate = replyTargetCandidate(4, ReplyTargetCandidateStatus.NG)
    val target = replyTargetPreview(
        anchorLine = ">>4,101-120",
        candidates = listOf(candidate),
        selectedCandidate = candidate,
        unavailableRanges = listOf(ReplyTargetUnavailableRange(101, 120)),
    )
    ReplyTargetScenarioContent(
        target = target,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true, name = "Revealed NG target")
@Composable
private fun ReplyTargetRevealedNgPreview() {
    val candidate = replyTargetCandidate(4, ReplyTargetCandidateStatus.NG)
    val target = replyTargetPreview(
        anchorLine = ">>4,101-120",
        candidates = listOf(candidate),
        selectedCandidate = candidate,
        unavailableRanges = listOf(ReplyTargetUnavailableRange(101, 120)),
    )
    ReplyTargetScenarioContent(
        target = target,
        revealedReplyTargetKeys = setOf(
            ReplyTargetDisclosureKey(
                hostPostNumber = 20,
                lineIndex = target.lineIndex,
                postNumber = candidate.postNumber,
            ),
        ),
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Preview(showBackground = true, name = "Unavailable range only")
@Composable
private fun ReplyTargetUnavailableOnlyPreview() {
    val target = replyTargetPreview(
        anchorLine = ">>101-2147483647",
        candidates = emptyList(),
        selectedCandidate = null,
        unavailableRanges = listOf(ReplyTargetUnavailableRange(101, Int.MAX_VALUE)),
    )
    ReplyTargetScenarioContent(
        target = target,
    )
}

/** 行別stateに応じて取得済みカードまたは未取得状態を描画する。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ReplyTargetScenarioContent(
    target: ReplyTargetPreviewUiState,
    revealedReplyTargetKeys: Set<ReplyTargetDisclosureKey> = emptySet(),
) {
    // --- Shared-transition scopes and preview layout ---
    SlevoTheme {
        SharedTransitionLayout {
            val transitionScope = this
            AnimatedVisibility(visible = true) {
                val visibilityScope = this
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // --- Selected candidate or range-only state ---
                    val selected = target.selectedCandidate
                    if (selected == null) {
                        ReplyTargetUnavailableRangesRow(ranges = target.unavailableRanges)
                    } else {
                        val disclosureKey = ReplyTargetDisclosureKey(
                            hostPostNumber = 20,
                            lineIndex = target.lineIndex,
                            postNumber = selected.postNumber,
                        )
                        val isRevealed = disclosureKey in revealedReplyTargetKeys
                        if ((selected.status == ReplyTargetCandidateStatus.NG) && !isRevealed) {
                            ReplyTargetStatusRow(
                                postNumber = selected.postNumber,
                                status = selected.status,
                                overflowCount = target.overflowCount,
                                overflowNumbers = target.referencedNumbers,
                                unavailableRanges = target.unavailableRanges,
                                onReveal = {},
                                onOpenOverflow = if (target.overflowCount > 0) ({}) else null,
                            )
                        } else {
                            selected.post?.let { post ->
                                ReplyPreviewCard(
                                    post = post,
                                    hostNamespace = "reply-target-preview-samples",
                                    previewType = ImageReplyPreviewType.REPLY_TARGET,
                                    overflowCount = target.overflowCount,
                                    overflowNumbers = target.referencedNumbers,
                                    unavailableRanges = target.unavailableRanges,
                                    onHide = if (selected.status == ReplyTargetCandidateStatus.NG) ({}) else null,
                                    headerTextScale = 1f,
                                    bodyTextScale = 1f,
                                    lineHeightEm = 1.4f,
                                    onOpenPost = {},
                                    onOpenOverflow = {},
                                    onImageClick = { _, _, _, _ -> },
                                    onImageLongPress = { _, _ -> },
                                    imageLoadFailureByUrl = emptyMap(),
                                    onImageLoadStart = {},
                                    onImageLoadError = { _, _ -> },
                                    onImageLoadSuccess = {},
                                    onImageRetry = {},
                                    enableSharedElement = false,
                                    sharedTransitionScope = transitionScope,
                                    animatedVisibilityScope = visibilityScope,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Preview本文の先頭行に対応する返信先stateを作る。 */
private fun replyTargetPreview(
    anchorLine: String,
    candidates: List<ReplyTargetCandidateUiModel>,
    selectedCandidate: ReplyTargetCandidateUiModel?,
    unavailableRanges: List<ReplyTargetUnavailableRange>,
) = ReplyTargetPreviewUiState(
    lineIndex = 0,
    lineStartOffset = 0,
    lineEndOffset = anchorLine.length,
    lineBreakEndOffset = anchorLine.length + 1,
    anchorLine = anchorLine,
    candidates = candidates,
    selectedCandidate = selectedCandidate,
    unavailableRanges = unavailableRanges,
)

/** Preview用の取得済み返信先候補と本文fixtureを作る。 */
private fun replyTargetCandidate(
    postNumber: Int,
    status: ReplyTargetCandidateStatus,
) = ReplyTargetCandidateUiModel(
    postNumber = postNumber,
    status = status,
    post = ReplyPreviewPostUiModel(
        postNumber = postNumber,
        name = "返信先の投稿者$postNumber",
        id = "preview-target-id-$postNumber",
        body = "返信先本文のプレビューです。\n複数の行で表示されます。",
        imageUrls = emptyList(),
    ),
)
