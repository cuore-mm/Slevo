package com.websarva.wings.android.slevo.ui.thread.viewmodel

import androidx.core.text.HtmlCompat
import com.websarva.wings.android.slevo.data.util.ReplyAnchorParser
import com.websarva.wings.android.slevo.ui.thread.state.PostReplyPreviewUiState
import com.websarva.wings.android.slevo.ui.thread.state.PostBodySegment
import com.websarva.wings.android.slevo.ui.thread.state.ReplyPreviewPostUiModel
import com.websarva.wings.android.slevo.ui.thread.state.ReplyTargetPreviewUiState
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import com.websarva.wings.android.slevo.ui.util.extractImageUrls
import com.websarva.wings.android.slevo.ui.util.extractImageUrlSpans
import com.websarva.wings.android.slevo.ui.util.removeImageUrls

/** 通常の番号順投稿一覧から返信プレビュー用の不変表示データを導出する。 */
internal object ThreadReplyPreviewTransformer {
    private val singleAnchorRegex = Regex(">>\\d+")

    /** NGを除き、返信先一件と番号順の返信元最大三件を投稿番号別に作成する。 */
    fun derive(
        posts: List<ThreadPostUiModel>,
        visibleReplySourceMap: Map<Int, List<Int>>,
        ngPostNumbers: Set<Int>,
        defaultName: String,
    ): Map<Int, PostReplyPreviewUiState> {
        val previewByNumber = mutableMapOf<Int, ReplyPreviewPostUiModel>()
        val replySourcePreviewByHost = mutableMapOf<Pair<Int, Int>, ReplyPreviewPostUiModel>()

        fun preview(number: Int): ReplyPreviewPostUiModel? {
            if (number in ngPostNumbers) return null
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
                val availableNumbers = tokens.flatMap { token ->
                    ReplyAnchorParser.extractReferencedNumbers(token.rawText, posts.size)
                }.distinct().filterNot { it in ngPostNumbers }
                if (availableNumbers.isEmpty()) return@mapNotNull null
                AnchorOnlyLine(contentLine, tokens, availableNumbers)
            }

            val allTargetNumbers = anchorOnlyLines.flatMap { it.availableNumbers }.distinct()
            val selectedLine = anchorOnlyLines.firstOrNull()
            val replyTarget = selectedLine?.availableNumbers?.firstOrNull()?.let { targetNumber ->
                preview(targetNumber)?.let { targetPost ->
                    val anchor = selectedLine.tokens.singleOrNull()
                    val isSingle = anchor != null &&
                        singleAnchorRegex.matches(anchor.rawText) &&
                        selectedLine.contentLine.text.trim() == anchor.rawText
                    ReplyTargetPreviewUiState(
                        lineIndex = selectedLine.contentLine.index,
                        lineStartOffset = selectedLine.contentLine.startOffset,
                        lineEndOffset = selectedLine.contentLine.endOffset,
                        lineBreakEndOffset = selectedLine.contentLine.lineBreakEndOffset,
                        anchorLine = selectedLine.contentLine.text,
                        isSingleAnchor = isSingle,
                        post = targetPost,
                        referencedNumbers = allTargetNumbers,
                    )
                }
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
            val bodySegments = if (replyTarget == null) {
                listOf(PostBodySegment.TextRange(0, post.body.content.length))
            } else {
                buildBodySegments(post.body.content, selectedLine!!, replyTarget)
            }
            if (replyTarget == null && replyPreviews.isEmpty()) {
                null
            } else {
                postNumber to PostReplyPreviewUiState(
                    replyTarget = replyTarget,
                    replySourcePreviews = replyPreviews,
                    replySourceNumbers = replyNumbers,
                    replyTargetNumbers = allTargetNumbers,
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

    /** 選択行だけを置換または保持し、本文offset範囲と挿入位置を組み立てる。 */
    private fun buildBodySegments(
        content: String,
        selectedLine: AnchorOnlyLine,
        targetPreview: ReplyTargetPreviewUiState,
    ): List<PostBodySegment> = buildList {
        val line = selectedLine.contentLine
        val previousLineIsBlank = if (line.startOffset > 0) {
            val previousLineStart = content.lastIndexOf('\n', line.startOffset - 2) + 1
            content.substring(previousLineStart, line.startOffset - 1).isBlank()
        } else {
            false
        }
        val prefixEnd = when {
            line.startOffset == 0 -> 0
            previousLineIsBlank -> line.startOffset
            else -> line.startOffset - 1
        }
        if (prefixEnd > 0) {
            add(PostBodySegment.TextRange(startOffset = 0, endOffset = prefixEnd))
        }
        if (!targetPreview.isSingleAnchor) {
            add(PostBodySegment.TextRange(line.startOffset, line.endOffset))
        }
        add(PostBodySegment.ReplyTarget(targetPreview))
        if (line.lineBreakEndOffset < content.length) {
            add(PostBodySegment.TextRange(line.lineBreakEndOffset, content.length))
        }
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
        val tokens: List<ReplyAnchorParser.AnchorToken>,
        val availableNumbers: List<Int>,
    )

    private const val MAX_REPLY_PREVIEWS = 3
}
