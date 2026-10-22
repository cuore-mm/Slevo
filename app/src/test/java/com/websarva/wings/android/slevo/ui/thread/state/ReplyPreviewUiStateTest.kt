package com.websarva.wings.android.slevo.ui.thread.state

import org.junit.Assert.assertEquals
import org.junit.Test

/** 返信プレビューstateの行内残数と返信元残数を検証する。 */
class ReplyPreviewUiStateTest {
    /** 返信先残数はNGを含む取得済み候補だけから選択一件を引いて算出する。 */
    @Test
    fun replyTargetOverflowCountExcludesUnavailableRanges() {
        val available = candidate(1, ReplyTargetCandidateStatus.AVAILABLE)
        val ng = candidate(2, ReplyTargetCandidateStatus.NG)
        val target = ReplyTargetPreviewUiState(
            lineIndex = 0,
            lineStartOffset = 0,
            lineEndOffset = 11,
            lineBreakEndOffset = 12,
            anchorLine = ">>1 >>2 >>100-200",
            candidates = listOf(available, ng),
            selectedCandidate = available,
            unavailableRanges = listOf(ReplyTargetUnavailableRange(100, 200)),
        )

        assertEquals(listOf(1, 2), target.referencedNumbers)
        assertEquals(1, target.overflowCount)
        assertEquals(listOf(ReplyTargetUnavailableRange(100, 200)), target.unavailableRanges)
    }

    /** 未取得範囲だけの行には取得済み候補残数を出さない。 */
    @Test
    fun replyTargetWithOnlyUnavailableRangesHasNoOverflowCandidates() {
        val target = ReplyTargetPreviewUiState(
            lineIndex = 0,
            lineStartOffset = 0,
            lineEndOffset = 16,
            lineBreakEndOffset = 16,
            anchorLine = ">>101-2147483647",
            candidates = emptyList(),
            selectedCandidate = null,
            unavailableRanges = listOf(ReplyTargetUnavailableRange(101, Int.MAX_VALUE)),
        )

        assertEquals(emptyList<Int>(), target.referencedNumbers)
        assertEquals(0, target.overflowCount)
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
