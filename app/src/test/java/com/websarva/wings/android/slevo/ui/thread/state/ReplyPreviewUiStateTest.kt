package com.websarva.wings.android.slevo.ui.thread.state

import org.junit.Assert.assertEquals
import org.junit.Test

/** 返信プレビューstateの行内残数と返信元残数を検証する。 */
class ReplyPreviewUiStateTest {
    /** 返信先残数は候補の状態を問わず、行の選択一件を引いて算出する。 */
    @Test
    fun replyTargetOverflowCountIncludesNgAndUnavailableCandidates() {
        val available = candidate(1, ReplyTargetCandidateStatus.AVAILABLE)
        val ng = candidate(2, ReplyTargetCandidateStatus.NG)
        val unavailable = candidate(3, ReplyTargetCandidateStatus.UNAVAILABLE)
        val target = ReplyTargetPreviewUiState(
            lineIndex = 0,
            lineStartOffset = 0,
            lineEndOffset = 11,
            lineBreakEndOffset = 12,
            anchorLine = ">>1 >>2 >>3",
            candidates = listOf(available, ng, unavailable),
            selectedCandidate = available,
            hasMoreCandidates = false,
        )

        assertEquals(listOf(1, 2, 3), target.referencedNumbers)
        assertEquals(2, target.overflowCount)
    }

    /** 返信元残数は従来どおり、全候補数から表示済みプレビュー数を引く。 */
    @Test
    fun replySourceOverflowCountUsesDisplayedPreviewCount() {
        val state = PostReplyPreviewUiState(
            replySourcePreviews = listOf(preview(2), preview(4), preview(6)),
            replySourceNumbers = listOf(2, 4, 6, 8, 10),
        )

        assertEquals(2, state.replySourceOverflowCount)
    }

    /** 番号と状態からテスト用候補を作る。 */
    private fun candidate(
        number: Int,
        status: ReplyTargetCandidateStatus,
    ) = ReplyTargetCandidateUiModel(
        postNumber = number,
        status = status,
        post = if (status == ReplyTargetCandidateStatus.UNAVAILABLE) null else preview(number),
    )

    /** 番号だけを持つテスト用プレビューレスを作る。 */
    private fun preview(number: Int) = ReplyPreviewPostUiModel(
        postNumber = number,
        name = "name$number",
        id = "id$number",
        body = "body$number",
        imageUrls = emptyList(),
    )
}
