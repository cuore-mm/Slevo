package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.PostBodySegment
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetCandidateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [ThreadReplyPreviewTransformer] の返信先・返信元選択とプレビュー整形を検証するテスト。 */
@RunWith(RobolectricTestRunner::class)
class ThreadReplyPreviewTransformerTest {
    /** 最初の安価行の候補選択と、返信元三件の表示上限を検証する。 */
    @Test
    fun selectsFirstVisibleAnchorOnlyLineAndThreeOldestReplies() {
        val posts = (1..6).map { number ->
            post(
                name = if (number == 2) "名無し" else "name$number",
                id = "id$number",
                content = if (number == 5) " >>1 \n >>2,3 \n>>4" else "レス$number",
            )
        }

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = mapOf(5 to listOf(2, 3, 4, 6)),
            ngPostNumbers = setOf(1, 3),
            defaultName = "名無し",
        ).getValue(5)

        assertEquals(listOf(0, 1, 2), result.replyTargets.map { it.lineIndex })
        assertEquals(1, result.replyTargets.first().selectedCandidate.postNumber)
        assertEquals(ReplyTargetCandidateStatus.NG, result.replyTargets.first().selectedCandidate.status)
        assertEquals(" >>2,3 ", result.replyTargets[1].anchorLine)
        assertEquals(2, result.replyTargets[1].selectedCandidate.postNumber)
        assertEquals(listOf(1), result.replyTargets.first().referencedNumbers)
        assertEquals(listOf(2, 4, 6), result.replySourceNumbers)
        assertEquals(listOf(2, 4, 6), result.replySourcePreviews.map { it.postNumber })
        assertEquals(emptyList<Pair<Int, Int>>(), result.bodySegments
            .filterIsInstance<PostBodySegment.TextRange>()
            .map { it.startOffset to it.endOffset })
        assertEquals(3, result.bodySegments.filterIsInstance<PostBodySegment.ReplyTarget>().size)
    }

    /** 文中参照を除外し、返信先名・本文・画像をプレビュー向けに整形する。 */
    @Test
    fun excludesInlineTextAndHidesDefaultNameAndImageUrls() {
        val posts = listOf(
            post("名無し", "id1", ">>1 text https://example.com/image.jpg tail"),
            post("custom", "id2", ">>1 ありがとう\n>>1"),
        )

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = emptySet(),
            defaultName = "<b>名無し</b>",
        ).getValue(2)

        assertEquals(1, result.replyTargets.firstOrNull()?.lineIndex)
        assertEquals(1, result.replyTargets.size)
        assertEquals(1, result.replyTargets.firstOrNull()?.lineIndex)
        assertEquals("", result.replyTargets.firstOrNull()?.selectedCandidate?.post?.name)
        assertEquals(">>1 text  tail", result.replyTargets.firstOrNull()?.selectedCandidate?.post?.body)
        assertEquals(listOf("https://example.com/image.jpg"), result.replyTargets.firstOrNull()?.selectedCandidate?.post?.imageUrls)
        assertNull(result.replyTargets.firstOrNull()?.selectedCandidate?.post?.id?.takeIf(String::isBlank))
    }

    /** HTML表記の名無しだけを省略し、独自名とトリップ付き名を保持する。 */
    /** 板名と同じ名前だけを省略し、独自名・トリップ付き名を保持する。 */
    @Test
    fun omitsOnlyNormalizedBoardDefaultName() {
        val posts = listOf(
            post("名無し", "id1", "target default"),
            post("独自名", "id2", "target custom"),
            post("名無し◆trip", "id3", "target trip"),
            post("host", "id4", ">>1"),
            post("host", "id5", ">>2"),
            post("host", "id6", ">>3"),
        )

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = emptySet(),
            defaultName = "<b>名無し</b>",
        )

        assertEquals("", result.getValue(4).replyTargets.firstOrNull()?.selectedCandidate?.post?.name)
        assertEquals("独自名", result.getValue(5).replyTargets.firstOrNull()?.selectedCandidate?.post?.name)
        assertEquals("名無し◆trip", result.getValue(6).replyTargets.firstOrNull()?.selectedCandidate?.post?.name)
    }

    /** 安価だけの各形式から最初に表示可能な番号だけを選び、文中参照を候補から外す。 */
    /** 単一・範囲・カンマ・空白区切りの安価行と文中安価を区別する。 */
    @Test
    fun selectsFirstVisibleAnchorAcrossSupportedLineForms() {
        val posts = (1..10).map { number ->
            post(
                name = "name$number",
                id = "id$number",
                content = if (number == 10) {
                    " >>1 \n>>2-3\n>>4,5\n>>6 >>7\n>>8 ありがとう\n>>9"
                } else {
                    "post $number"
                },
            )
        }

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = setOf(1, 3),
            defaultName = "",
        ).getValue(10)

        assertEquals(listOf(0, 1, 2, 3, 5), result.replyTargets.map { it.lineIndex })
        assertEquals(1, result.replyTargets[0].selectedCandidate.postNumber)
        assertEquals(2, result.replyTargets[1].selectedCandidate.postNumber)
        assertEquals(listOf(2, 3), result.replyTargets[1].referencedNumbers)
        assertEquals(listOf(2), result.replyTargets[1].referencedNumbers
            .filter { result.replyTargets[1].candidates.first { candidate -> candidate.postNumber == it }.status == ReplyTargetCandidateStatus.AVAILABLE })
        assertEquals(5, result.bodySegments.filterIsInstance<PostBodySegment.ReplyTarget>().size)
    }

    /** 前後空白を許容した単一安価行は本文から除き、本文の前後範囲を維持する。 */
    /** 空白付き安価行を置換し、先行・後続の本文offsetを維持する。 */
    @Test
    fun singleAnchorWithWhitespaceBecomesOneInlinePreview() {
        val posts = listOf(
            post("name1", "id1", "target"),
            post("name2", "id2", "before\n  >>1  \nafter"),
        )

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = emptySet(),
            defaultName = "",
        ).getValue(2)

        assertEquals(1, result.replyTargets.firstOrNull()?.lineIndex)
        assertEquals(1, result.replyTargets.size)
        assertEquals(listOf(0 to 6, 15 to 20), result.bodySegments
            .filterIsInstance<PostBodySegment.TextRange>()
            .map { it.startOffset to it.endOffset })
        assertEquals(1, result.bodySegments.filterIsInstance<PostBodySegment.ReplyTarget>().size)
    }

    /** 無効構文を除外し、有効な未取得番号は未取得候補として保持する。 */
    @Test
    fun doesNotCreatePreviewsForInvalidOrUnavailableAnchors() {
        val posts = listOf(post("name", "id1", ">>3-1\n>>999\n>>0"))

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = emptySet(),
            defaultName = "",
        )

        assertEquals(1, result.getValue(1).replyTargets.size)
        assertEquals(ReplyTargetCandidateStatus.UNAVAILABLE, result.getValue(1).replyTargets.firstOrNull()?.selectedCandidate?.status)
    }

    /** 画像URLだけの行を省き、通常URL・空行・残り本文を保持する。 */
    @Test
    fun removesImageOnlyLineButPreservesTextAndNonImageUrls() {
        val posts = listOf(
            post("name", "id1", "first https://example.com/page\nhttps://example.com/a.jpg\n\nsecond"),
            post("name", "id2", ">>1"),
        )

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = emptySet(),
            defaultName = "",
        ).getValue(2).replyTargets.firstOrNull()?.selectedCandidate?.post

        assertEquals("first https://example.com/page\n\nsecond", result?.body)
        assertEquals(listOf("https://example.com/a.jpg"), result?.imageUrls)
    }

    /** 同じURLの重複を含め、画像URLだけを元の出現順で除去・収集する。 */
    /** 画像URLを全件収集し、重複を含む順序と本文除去を検証する。 */
    @Test
    fun removesEveryRecognizedImageUrlAndKeepsDuplicatesInTheImageList() {
        val imageA = "https://example.com/a.jpg"
        val imageB = "https://example.com/b.png"
        val posts = listOf(
            post("name", "id1", "first https://example.com/page\n$imageA\ntext $imageB tail $imageA\n$imageB"),
            post("name", "id2", ">>1"),
        )

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = emptySet(),
            defaultName = "",
        ).getValue(2).replyTargets.firstOrNull()?.selectedCandidate?.post

        assertEquals("first https://example.com/page\ntext  tail ", result?.body)
        assertEquals(listOf(imageA, imageB, imageA, imageB), result?.imageUrls)
    }

    /** 画像だけの本文では空行を作らず、先頭画像を含む一覧は保持する。 */
    /** 画像のみの本文から空本文行を作らず、画像一覧を保持する。 */
    @Test
    fun imageOnlyPreviewHasNoEmptyBodyLines() {
        val posts = listOf(
            post("name", "id1", "https://example.com/only.gif"),
            post("name", "id2", ">>1"),
        )

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = emptySet(),
            defaultName = "",
        ).getValue(2).replyTargets.firstOrNull()?.selectedCandidate?.post

        assertEquals("", result?.body)
        assertEquals(listOf("https://example.com/only.gif"), result?.imageUrls)
    }

    /** 返信元だけ親レス単一安価と後続空白を省き、複数指定と返信先本文を保つ。 */
    /** 返信元だけ親安価を除去し、返信先本文は変更しないことを検証する。 */
    @Test
    fun omitsOnlySingleAnchorToHostFromReplySourcePreview() {
        val sourceBody = listOf(
            ">>2 ありがとう",
            ">>2",
            ">>2,3 カンマ指定",
            ">>2-3 範囲指定",
            ">>2 https://example.com/reply.jpg",
            ">>1 別レス",
            "続き >>2\t本文",
        ).joinToString("\n")
        val posts = listOf(
            post("source", "id1", sourceBody),
            post("host", "id2", ">>1"),
            post("viewer", "id3", ">>1"),
        )

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = mapOf(2 to listOf(1)),
            ngPostNumbers = emptySet(),
            defaultName = "",
        )

        assertEquals(
            "ありがとう\n>>2,3 カンマ指定\n>>2-3 範囲指定\n>>1 別レス\n続き 本文",
            result.getValue(2).replySourcePreviews.single().body,
        )
        assertEquals(
            listOf("https://example.com/reply.jpg"),
            result.getValue(2).replySourcePreviews.single().imageUrls,
        )
        assertTrue(result.getValue(3).replyTargets.firstOrNull()?.selectedCandidate?.post?.body?.contains(">>2") == true)
        assertEquals(sourceBody, posts.first().body.content)
    }

    /** 複数行の返信先候補を独立して保持し、返信元一覧を三件に制限する。 */
    @Test
    fun keepsAllTargetAndSourceCandidatesWhileLimitingDisplayedPreviews() {
        val posts = (1..10).map { number ->
            post("name$number", "id$number", if (number == 10) ">>1\n>>3\n>>1\n>>5" else "post$number")
        }

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = mapOf(10 to listOf(9, 8, 6, 4, 2, 2)),
            ngPostNumbers = emptySet(),
            defaultName = "",
        ).getValue(10)

        assertEquals(listOf(1, 3, 1, 5), result.replyTargets.map { it.selectedCandidate.postNumber })
        assertEquals(listOf(listOf(1), listOf(3), listOf(1), listOf(5)), result.replyTargets.map { it.referencedNumbers })
        assertEquals(listOf(2, 4, 6, 8, 9), result.replySourceNumbers)
        assertEquals(listOf(2, 4, 6), result.replySourcePreviews.map { it.postNumber })
        assertEquals(2, result.replySourceOverflowCount)
    }

    /** 返信先候補は正常、NG、未取得の優先順で行ごとに一件選択する。 */
    @Test
    fun prefersAvailableThenNgThenUnavailableCandidatesPerLine() {
        val posts = (1..6).map { number ->
            val content = when (number) {
                6 -> ">>1 >>2 >>3 >>4 >>5"
                else -> "post$number"
            }
            post("name$number", "id$number", content)
        }

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = setOf(1, 2, 4),
            defaultName = "",
        ).getValue(6).replyTargets.single()

        assertEquals(listOf(1, 2, 3, 4, 5), result.referencedNumbers)
        assertEquals(3, result.selectedCandidate.postNumber)
        assertEquals(ReplyTargetCandidateStatus.AVAILABLE, result.selectedCandidate.status)
        assertEquals(4, result.overflowCount)
    }

    /** 全候補がNGなら最初のNGを選択し、未取得候補も残数に含める。 */
    @Test
    fun selectsNgBeforeUnavailableAndRetainsBothKinds() {
        val posts = listOf(
            post("name1", "id1", "target"),
            post("name2", "id2", ">>1 >>2 >>4"),
        )

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = setOf(1, 2),
            defaultName = "",
        ).getValue(2).replyTargets.single()

        assertEquals(1, result.selectedCandidate.postNumber)
        assertEquals(ReplyTargetCandidateStatus.NG, result.selectedCandidate.status)
        assertEquals(ReplyTargetCandidateStatus.NG, result.candidates[1].status)
        assertEquals(ReplyTargetCandidateStatus.UNAVAILABLE, result.candidates[2].status)
        assertEquals(2, result.overflowCount)
    }

    /** 正常3件とNG2件では最初の正常を選び、ほか4件を候補に含める。 */
    @Test
    fun keepsAllFiveCandidatesWhenThreeAvailableAndTwoNg() {
        val posts = (1..6).map { number ->
            post("name$number", "id$number", if (number == 6) ">>1-5" else "target$number")
        }

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = setOf(4, 5),
            defaultName = "",
        ).getValue(6).replyTargets.single()

        assertEquals(1, result.selectedCandidate.postNumber)
        assertEquals(4, result.overflowCount)
        assertEquals(listOf(4, 5), result.candidates
            .filter { it.status == ReplyTargetCandidateStatus.NG }
            .map { it.postNumber })
    }

    /** 正常一件とNG四件のときは正常候補を表示し、NG候補を全件保持する。 */
    @Test
    fun prefersSingleAvailableCandidateOverFourNgCandidates() {
        val posts = (1..6).map { number ->
            post("name$number", "id$number", if (number == 6) ">>1-5" else "target$number")
        }

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = setOf(2, 3, 4, 5),
            defaultName = "",
        ).getValue(6).replyTargets.single()

        assertEquals(1, result.selectedCandidate.postNumber)
        assertEquals(4, result.overflowCount)
        assertEquals(4, result.candidates.count { it.status == ReplyTargetCandidateStatus.NG })
    }

    /** 全候補がNGのときは最初のNG状態を選び、残り二件を省略数に含める。 */
    @Test
    fun selectsFirstNgWhenAllThreeCandidatesAreNg() {
        val posts = (1..4).map { number ->
            post("name$number", "id$number", if (number == 4) ">>1-3" else "target$number")
        }

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = setOf(1, 2, 3),
            defaultName = "",
        ).getValue(4).replyTargets.single()

        assertEquals(1, result.selectedCandidate.postNumber)
        assertEquals(ReplyTargetCandidateStatus.NG, result.selectedCandidate.status)
        assertEquals(2, result.overflowCount)
    }

    /** 全候補が未取得のときは最初の番号を未取得状態として保持する。 */
    @Test
    fun selectsFirstUnavailableWhenAllCandidatesAreMissing() {
        val posts = listOf(post("host", "host-id", ">>20-22"))

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = emptySet(),
            defaultName = "",
        ).getValue(1).replyTargets.single()

        assertEquals(listOf(20, 21, 22), result.referencedNumbers)
        assertEquals(20, result.selectedCandidate.postNumber)
        assertEquals(ReplyTargetCandidateStatus.UNAVAILABLE, result.selectedCandidate.status)
        assertEquals(2, result.overflowCount)
    }

    /** 先頭候補が未取得でも、後続の正常候補を優先して選択する。 */
    @Test
    fun selectsAvailableCandidateAfterUnavailableFirstCandidate() {
        val posts = listOf(
            post("available", "id1", "target"),
            post("host", "host-id", ">>99 >>1"),
        )

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = emptySet(),
            defaultName = "",
        ).getValue(2).replyTargets.single()

        assertEquals(listOf(99, 1), result.referencedNumbers)
        assertEquals(ReplyTargetCandidateStatus.UNAVAILABLE, result.candidates.first().status)
        assertEquals(1, result.selectedCandidate.postNumber)
        assertEquals(ReplyTargetCandidateStatus.AVAILABLE, result.selectedCandidate.status)
    }

    /** 一行の候補数は上限内に保ち、後続の別行も独立に変換する。 */
    @Test
    fun capsEachLineIndependentlyAndProcessesFollowingLines() {
        val posts = (1..1_005).map { number ->
            post("name$number", "id$number", if (number == 1_005) ">>1-2147483647\n>>2" else "post$number")
        }

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = emptySet(),
            defaultName = "",
        ).getValue(1_005)

        assertEquals(1_000, result.replyTargets[0].candidates.size)
        assertEquals(true, result.replyTargets[0].hasMoreCandidates)
        assertEquals(listOf(2), result.replyTargets[1].referencedNumbers)
        assertEquals(2, result.bodySegments.filterIsInstance<PostBodySegment.ReplyTarget>().size)
    }

    /** 千件目より後ろに正常候補があっても候補上限外から選ばない。 */
    @Test
    fun doesNotSelectAvailableCandidateOutsideRetainedLimit() {
        val posts = (1..1_002).map { number ->
            post("name$number", "id$number", if (number == 1_002) ">>1-1001" else "target$number")
        }

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = (1..1_000).toSet(),
            defaultName = "",
        ).getValue(1_002).replyTargets.single()

        assertEquals(1_000, result.candidates.size)
        assertEquals(true, result.hasMoreCandidates)
        assertEquals(1, result.selectedCandidate.postNumber)
        assertEquals(ReplyTargetCandidateStatus.NG, result.selectedCandidate.status)
        assertEquals(false, 1_001 in result.referencedNumbers)
    }

    /** 複数対象行の境界改行だけを置換し、空行・不正安価・文中安価を保持する。 */
    @Test
    fun replacesLeadingMiddleTrailingConsecutiveAndBlankSeparatedTargetLines() {
        val cases = listOf(
            ">>1\nbody" to listOf(0),
            "before\n>>1\nafter" to listOf(1),
            "before\n>>1" to listOf(1),
            ">>1\n>>2\nbody" to listOf(0, 1),
            "before\n\n>>1\n\nafter" to listOf(2),
        )

        cases.forEach { (content, expectedLineIndexes) ->
            val posts = listOf(
                post("target1", "id1", "target one"),
                post("target2", "id2", "target two"),
                post("host", "host-id", content),
            )
            val state = ThreadReplyPreviewTransformer.derive(
                posts = posts,
                visibleReplySourceMap = emptyMap(),
                ngPostNumbers = emptySet(),
                defaultName = "",
            ).getValue(3)
            val renderedContent = state.bodySegments.joinToString(separator = "") { segment ->
                when (segment) {
                    is PostBodySegment.TextRange -> content.substring(segment.startOffset, segment.endOffset)
                    is PostBodySegment.ReplyTarget -> "[target]"
                }
            }
            val expectedRenderedContent = when (content) {
                ">>1\nbody" -> "[target]body"
                "before\n>>1\nafter" -> "before[target]after"
                "before\n>>1" -> "before[target]"
                ">>1\n>>2\nbody" -> "[target][target]body"
                else -> "before\n\n[target]\nafter"
            }

            assertEquals(expectedLineIndexes, state.replyTargets.map { it.lineIndex })
            assertEquals(expectedRenderedContent, renderedContent)
        }
    }

    /** 文中・不正安価は通常本文に残り、対象行だけをカードへ置換する。 */
    @Test
    fun preservesInlineAndInvalidAnchorOffsetsAroundTargetLine() {
        val content = "before >>1 inline\n>>0\n>>2\nend"
        val posts = listOf(
            post("target1", "id1", "target one"),
            post("target2", "id2", "target two"),
            post("host", "host-id", content),
        )

        val state = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = emptySet(),
            defaultName = "",
        ).getValue(3)
        val textRanges = state.bodySegments.filterIsInstance<PostBodySegment.TextRange>()

        assertEquals(listOf(2), state.replyTargets.map { it.lineIndex })
        assertEquals(listOf("before >>1 inline\n>>0", "end"), textRanges.map {
            content.substring(it.startOffset, it.endOffset)
        })
    }

    private fun post(name: String, id: String, content: String) = ThreadPostUiModel(
        header = ThreadPostUiModel.Header(name = name, email = "", date = "", id = id),
        body = ThreadPostUiModel.Body(content = content),
    )
}
