package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.PostBodySegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [ThreadReplyPreviewTransformer] の返信先・返信元選択とプレビュー整形を検証するテスト。 */
@RunWith(RobolectricTestRunner::class)
class ThreadReplyPreviewTransformerTest {
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

        assertEquals(1, result.replyTarget?.lineIndex)
        assertEquals(" >>2,3 ", result.replyTarget?.anchorLine)
        assertFalse(result.replyTarget?.isSingleAnchor ?: true)
        assertEquals(2, result.replyTarget?.post?.postNumber)
        assertEquals(listOf(2, 4), result.replyTargetNumbers)
        assertEquals(listOf(2, 4, 6), result.replySourceNumbers)
        assertEquals(listOf(2, 4, 6), result.replySourcePreviews.map { it.postNumber })
        assertEquals(listOf(0 to 5, 6 to 13, 14 to 17), result.bodySegments
            .filterIsInstance<PostBodySegment.TextRange>()
            .map { it.startOffset to it.endOffset })
        assertEquals(1, result.bodySegments.filterIsInstance<PostBodySegment.ReplyTarget>().size)
    }

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

        assertEquals(1, result.replyTarget?.lineIndex)
        assertTrue(result.replyTarget?.isSingleAnchor == true)
        assertEquals("", result.replyTarget?.post?.name)
        assertEquals(">>1 text  tail", result.replyTarget?.post?.body)
        assertEquals(listOf("https://example.com/image.jpg"), result.replyTarget?.post?.imageUrls)
        assertNull(result.replyTarget?.post?.id?.takeIf(String::isBlank))
    }

    /** 安価だけの各形式から最初に表示可能な番号だけを選び、文中参照を候補から外す。 */
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

        assertEquals(1, result.replyTarget?.lineIndex)
        assertEquals(2, result.replyTarget?.post?.postNumber)
        assertFalse(result.replyTarget?.isSingleAnchor ?: true)
        assertEquals(listOf(2, 4, 5, 6, 7, 9), result.replyTargetNumbers)
        assertEquals(1, result.bodySegments.filterIsInstance<PostBodySegment.ReplyTarget>().size)
    }

    /** 前後空白を許容した単一安価行は本文から除き、本文の前後範囲を維持する。 */
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

        assertEquals(1, result.replyTarget?.lineIndex)
        assertTrue(result.replyTarget?.isSingleAnchor == true)
        assertEquals(listOf(0 to 6, 15 to 20), result.bodySegments
            .filterIsInstance<PostBodySegment.TextRange>()
            .map { it.startOffset to it.endOffset })
        assertEquals(1, result.bodySegments.filterIsInstance<PostBodySegment.ReplyTarget>().size)
    }

    @Test
    fun doesNotCreatePreviewsForInvalidOrUnavailableAnchors() {
        val posts = listOf(post("name", "id1", ">>3-1\n>>999\n>>0"))

        val result = ThreadReplyPreviewTransformer.derive(
            posts = posts,
            visibleReplySourceMap = emptyMap(),
            ngPostNumbers = emptySet(),
            defaultName = "",
        )

        assertTrue(result.isEmpty())
    }

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
        ).getValue(2).replyTarget?.post

        assertEquals("first https://example.com/page\n\nsecond", result?.body)
        assertEquals(listOf("https://example.com/a.jpg"), result?.imageUrls)
    }

    /** 同じURLの重複を含め、画像URLだけを元の出現順で除去・収集する。 */
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
        ).getValue(2).replyTarget?.post

        assertEquals("first https://example.com/page\ntext  tail ", result?.body)
        assertEquals(listOf(imageA, imageB, imageA, imageB), result?.imageUrls)
    }

    /** 画像だけの本文では空行を作らず、先頭画像を含む一覧は保持する。 */
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
        ).getValue(2).replyTarget?.post

        assertEquals("", result?.body)
        assertEquals(listOf("https://example.com/only.gif"), result?.imageUrls)
    }

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

        assertEquals(listOf(1, 3, 5), result.replyTargetNumbers)
        assertEquals(1, result.replyTarget?.post?.postNumber)
        assertEquals(2, result.replyTargetOverflowCount)
        assertEquals(listOf(2, 4, 6, 8, 9), result.replySourceNumbers)
        assertEquals(listOf(2, 4, 6), result.replySourcePreviews.map { it.postNumber })
        assertEquals(2, result.replySourceOverflowCount)
    }

    private fun post(name: String, id: String, content: String) = ThreadPostUiModel(
        header = ThreadPostUiModel.Header(name = name, email = "", date = "", id = id),
        body = ThreadPostUiModel.Body(content = content),
    )
}
