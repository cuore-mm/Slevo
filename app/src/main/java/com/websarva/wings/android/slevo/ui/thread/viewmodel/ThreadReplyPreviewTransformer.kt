package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.core.text.HtmlCompat
import com.websarva.wings.android.slevo.data.util.ReplyAnchorParser
import com.websarva.wings.android.slevo.ui.thread.state.PostReplyPreviewUiState
import com.websarva.wings.android.slevo.ui.thread.state.PostBodySegment
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetCandidateStatus
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetCandidateUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetPreviewUiState
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.util.extractImageUrls
import com.websarva.wings.android.slevo.ui.util.extractImageUrlSpans
import com.websarva.wings.android.slevo.ui.util.removeImageUrls

/** 通常の番号順投稿一覧から返信プレビュー用の不変表示データを導出する。 */
internal object ThreadReplyPreviewTransformer {
    private val singleAnchorRegex = Regex(">>\\d+")
    private const val MAX_REPLY_TARGET_CANDIDATES = 1_000

    /** 行ごとの返信先候補とNG除外済み返信元最大三件を投稿番号別に作成する。 */
    fun derive(
        posts: List<ThreadPostUiModel>,
        visibleReplySourceMap: Map<Int, List<Int>>,
        ngPostNumbers: Set<Int>,
        defaultName: String,
    ): Map<Int, PostReplyPreviewUiState> {
        val previewByNumber = mutableMapOf<Int, ReplyPreviewPostUiModel>()
        val replySourcePreviewByHost = mutableMapOf<Pair<Int, Int>, ReplyPreviewPostUiModel>()

        fun preview(number: Int, includeNg: Boolean = false): ReplyPreviewPostUiModel? {
            if (!includeNg && number in ngPostNumbers) return null
            return previewByNumber.getOrPut(number) {
                posts.getOrNull(number - 1)?.toReplyPreview(number, defaultName)
                    ?: return null
            }
        }

        /** 親レス番号に応じて単一安価を除いた返信元プレビューを返す。 */
        fun replySourcePreview(number: Int, hostNumber: Int): ReplyPreviewPostUiModel? {
            val basePreview = preview(number) ?: return null
            return replySourcePreviewByHost.getOrPut(number to hostNumber) {
                basePreview.copy(body = removeReplySourceAnchor(basePreview.body, hostNumber))
            }
        }

        // --- Reply-target selection and source candidate normalization ---
        return posts.mapIndexedNotNull { index, post ->
            val postNumber = index + 1
            val contentLines = splitContentLines(post.body.content)
            val anchorOnlyLines = contentLines.mapNotNull { contentLine ->
                val tokens = contentLine.text.takeIf(::isAnchorOnlyLine)?.let(ReplyAnchorParser::parseAnchors)
                    ?: return@mapNotNull null
                val limitedNumbers = ReplyAnchorParser.extractReferencedNumbersWithLimit(
                    content = contentLine.text,
                    limit = MAX_REPLY_TARGET_CANDIDATES,
                )
                // Guard: 有効な番号がない行は返信先候補を生成しない。
                if (limitedNumbers.numbers.isEmpty()) return@mapNotNull null
                val candidates = limitedNumbers.numbers.map { number ->
                    val targetPost = posts.getOrNull(number - 1)
                    val status = when {
                        targetPost == null -> ReplyTargetCandidateStatus.UNAVAILABLE
                        number in ngPostNumbers -> ReplyTargetCandidateStatus.NG
                        else -> ReplyTargetCandidateStatus.AVAILABLE
                    }
                    ReplyTargetCandidateUiModel(
                        postNumber = number,
                        status = status,
                        post = targetPost?.let { preview(number, includeNg = status == ReplyTargetCandidateStatus.NG) },
                    )
                }
                // 正常候補を優先し、なければNG、最後に未取得の先頭を選ぶ。
                val selectedCandidate = candidates.firstOrNull {
                    it.status == ReplyTargetCandidateStatus.AVAILABLE
                } ?: candidates.firstOrNull { it.status == ReplyTargetCandidateStatus.NG }
                    ?: candidates.first()
                AnchorOnlyLine(contentLine, candidates, selectedCandidate, limitedNumbers.hasMore)
            }

            val replyTargets = anchorOnlyLines.map { line ->
                ReplyTargetPreviewUiState(
                    lineIndex = line.contentLine.index,
                    lineStartOffset = line.contentLine.startOffset,
                    lineEndOffset = line.contentLine.endOffset,
                    lineBreakEndOffset = line.contentLine.lineBreakEndOffset,
                    anchorLine = line.contentLine.text,
                    candidates = line.candidates,
                    selectedCandidate = line.selectedCandidate,
                    hasMoreCandidates = line.hasMoreCandidates,
                )
            }

            val replyNumbers = visibleReplySourceMap[postNumber].orEmpty()
                .asSequence()
                .filter { it in 1..posts.size && it !in ngPostNumbers }
                .distinct()
                .sorted()
                .toList()
            val replyPreviews = replyNumbers.take(MAX_REPLY_PREVIEWS)
                .mapNotNull { number -> replySourcePreview(number, postNumber) }
            // --- Original-offset body segments and immutable row state ---
            val bodySegments = if (replyTargets.isEmpty()) {
                listOf(PostBodySegment.TextRange(0, post.body.content.length))
            } else {
                buildBodySegments(post.body.content, replyTargets)
            }
            if (replyTargets.isEmpty() && replyPreviews.isEmpty()) {
                null
            } else {
                postNumber to PostReplyPreviewUiState(
                    replyTargets = replyTargets,
                    replySourcePreviews = replyPreviews,
                    replySourceNumbers = replyNumbers,
                    bodySegments = bodySegments,
                )
            }
        }.toMap()
    }

    /** 一行の非空内容がすべて有効な安価tokenと空白で構成されるか判定する。 */
    private fun isAnchorOnlyLine(line: String): Boolean {
        val tokens = ReplyAnchorParser.parseAnchors(line)
        if (tokens.isEmpty()) return false
        var cursor = 0
        tokens.forEach { token ->
            if (line.substring(cursor, token.start).any { !it.isWhitespace() }) return false
            cursor = token.end
        }
        return line.substring(cursor).all(Char::isWhitespace)
    }

    /** 本文を改行単位に分け、改行を含む元offsetを保持する。 */
    private fun splitContentLines(content: String): List<ContentLine> {
        val lines = mutableListOf<ContentLine>()
        var startOffset = 0
        var lineIndex = 0
        while (startOffset <= content.length) {
            val newlineOffset = content.indexOf('\n', startOffset)
            val endOffset = if (newlineOffset == -1) content.length else newlineOffset
            lines += ContentLine(
                index = lineIndex,
                text = content.substring(startOffset, endOffset),
                startOffset = startOffset,
                endOffset = endOffset,
                lineBreakEndOffset = if (newlineOffset == -1) endOffset else newlineOffset + 1,
            )
            if (newlineOffset == -1) break
            startOffset = newlineOffset + 1
            lineIndex++
        }
        return lines
    }

    /** 対象行をそれぞれ置換し、通常本文のoffset順を維持する。 */
    private fun buildBodySegments(
        content: String,
        targetPreviews: List<ReplyTargetPreviewUiState>,
    ): List<PostBodySegment> = buildList {
        var cursor = 0
        targetPreviews.forEach { targetPreview ->
            val previousLineIsBlank = targetPreview.lineStartOffset > 0 &&
                previousLineText(content, targetPreview.lineStartOffset).isBlank()
            val prefixEnd = when {
                targetPreview.lineStartOffset == 0 -> cursor
                previousLineIsBlank -> targetPreview.lineStartOffset
                else -> (targetPreview.lineStartOffset - 1).coerceAtLeast(cursor)
            }
            if (prefixEnd > cursor) {
                add(PostBodySegment.TextRange(startOffset = cursor, endOffset = prefixEnd))
            }
            add(PostBodySegment.ReplyTarget(targetPreview))
            cursor = targetPreview.lineBreakEndOffset
        }
        if (cursor < content.length) {
            add(PostBodySegment.TextRange(cursor, content.length))
        }
    }

    /** 指定offset直前の行内容を返す。 */
    private fun previousLineText(content: String, lineStartOffset: Int): String {
        val previousLineEnd = lineStartOffset - 1
        val previousLineStart = content.lastIndexOf('\n', previousLineEnd - 1) + 1
        return content.substring(previousLineStart, previousLineEnd)
    }

    /** レス本文から画像URLを除き、プレビュー用の名前・本文・画像一覧を作る。 */
    private fun ThreadPostUiModel.toReplyPreview(
        number: Int,
        defaultName: String,
    ): ReplyPreviewPostUiModel {
        val images = extractImageUrls(body.content)
        val previewBody = splitContentLines(body.content).mapNotNull { line ->
            val hadImage = extractImageUrlSpans(line.text).isNotEmpty()
            val text = removeImageUrls(line.text)
            if (hadImage && text.isBlank()) null else text
        }.joinToString("\n")
        val normalizedDefaultName = normalizeName(defaultName)
        val normalizedPostName = normalizeName(header.name)
        return ReplyPreviewPostUiModel(
            postNumber = number,
            name = if (normalizedDefaultName.isNotEmpty() && normalizedPostName == normalizedDefaultName) {
                ""
            } else {
                header.name
            },
            id = header.id,
            body = previewBody,
            imageUrls = images,
        )
    }

    /** 返信元本文から親番号への単一安価と直後の空白を除き、安価だけの行を省く。 */
    private fun removeReplySourceAnchor(body: String, hostNumber: Int): String =
        splitContentLines(body).mapNotNull { line ->
            val (text, removedAnchor) = removeReplySourceAnchorFromLine(line.text, hostNumber)
            if (removedAnchor && text.isBlank()) null else text
        }.joinToString("\n")

    /** 一行から親番号への単一安価を除き、除去の有無と残りの文字列を返す。 */
    private fun removeReplySourceAnchorFromLine(line: String, hostNumber: Int): Pair<String, Boolean> {
        val tokens = ReplyAnchorParser.parseAnchors(line)
        if (tokens.isEmpty()) return line to false

        val result = StringBuilder(line.length)
        var cursor = 0
        var removedAnchor = false
        tokens.forEach { token ->
            val range = token.ranges.singleOrNull()
            val isSingleNumber = singleAnchorRegex.matches(token.rawText) &&
                range != null && range.first == hostNumber && range.last == hostNumber
            if (!isSingleNumber) return@forEach

            result.append(line, cursor, token.start)
            cursor = token.end
            while (cursor < line.length && line[cursor].isWhitespace()) cursor++
            removedAnchor = true
        }
        if (!removedAnchor) return line to false

        result.append(line, cursor, line.length)
        return result.toString() to true
    }

    /** DAT由来の名前と板設定名をHTML表示文字列に正規化して比較する。 */
    private fun normalizeName(name: String): String = HtmlCompat
        .fromHtml(name, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .trim()

    /** 行の本文とoffset範囲を保持する。 */
    private data class ContentLine(
        val index: Int,
        val text: String,
        val startOffset: Int,
        val endOffset: Int,
        val lineBreakEndOffset: Int,
    )

    /** 表示可能な参照先を含む、安価だけの本文行を表す。 */
    private data class AnchorOnlyLine(
        val contentLine: ContentLine,
        val candidates: List<ReplyTargetCandidateUiModel>,
        val selectedCandidate: ReplyTargetCandidateUiModel,
        val hasMoreCandidates: Boolean,
    )

    private const val MAX_REPLY_PREVIEWS = 3
}
