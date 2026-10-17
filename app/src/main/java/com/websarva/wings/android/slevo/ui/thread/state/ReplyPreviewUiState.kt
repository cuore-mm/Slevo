package com.websarva.wings.android.slevo.ui.thread.state

/** 返信プレビュー一件の表示に必要なレス情報を表す。 */
data class ReplyPreviewPostUiModel(
    val postNumber: Int,
    val name: String,
    val id: String,
    val body: String,
    val imageUrls: List<String>,
)

/** 安価だけの行に挿入する返信先プレビューと元行情報を表す。 */
data class ReplyTargetPreviewUiState(
    val lineIndex: Int,
    val lineStartOffset: Int,
    val lineEndOffset: Int,
    val lineBreakEndOffset: Int,
    val anchorLine: String,
    val isSingleAnchor: Boolean,
    val post: ReplyPreviewPostUiModel,
    val referencedNumbers: List<Int>,
)

/** 元本文の範囲を参照する、プレビュー挿入前後の本文セグメントを表す。 */
sealed interface PostBodySegment {
    /** `[startOffset, endOffset)` の本文範囲を表す。 */
    data class TextRange(
        val startOffset: Int,
        val endOffset: Int,
    ) : PostBodySegment

    /** 対応する安価行へ挿入する返信先プレビューを表す。 */
    data class ReplyTarget(
        val preview: ReplyTargetPreviewUiState,
    ) : PostBodySegment
}

/** 一つの通常レスに付随する返信先と返信元プレビューの集合を表す。 */
data class PostReplyPreviewUiState(
    val replyTarget: ReplyTargetPreviewUiState? = null,
    val replySourcePreviews: List<ReplyPreviewPostUiModel> = emptyList(),
    val replySourceNumbers: List<Int> = emptyList(),
    val replyTargetNumbers: List<Int> = emptyList(),
    val bodySegments: List<PostBodySegment> = emptyList(),
) {
    /** 先頭三件の表示後に省略される返信元件数。 */
    val replySourceOverflowCount: Int
        get() = (replySourceNumbers.size - replySourcePreviews.size).coerceAtLeast(0)

    /** 先頭の返信先プレビュー以外に省略される参照先件数。 */
    val replyTargetOverflowCount: Int
        get() = (replyTargetNumbers.size - if (replyTarget == null) 0 else 1).coerceAtLeast(0)
}
