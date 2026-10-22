package com.websarva.wings.android.slevo.ui.thread.state

/** 返信プレビュー一件の表示に必要なレス情報を表す。 */
data class ReplyPreviewPostUiModel(
    val postNumber: Int,
    val name: String,
    val id: String,
    val body: String,
    val imageUrls: List<String>,
)

/** 返信先候補が取得状態のどれに該当するかを表す。 */
enum class ReplyTargetCandidateStatus {
    AVAILABLE,
    NG,
    UNAVAILABLE,
}

/** 安価行に含まれる一つの返信先番号と、その最新の表示状態を表す。 */
data class ReplyTargetCandidateUiModel(
    val postNumber: Int,
    val status: ReplyTargetCandidateStatus,
    val post: ReplyPreviewPostUiModel?,
)

/** NG返信先の一時開示状態を表示箇所ごとに識別する。 */
data class ReplyTargetDisclosureKey(
    val hostPostNumber: Int,
    val lineIndex: Int,
    val postNumber: Int,
    val popupId: Long? = null,
)

/** 安価だけの行に挿入する返信先と候補一覧を表す。 */
data class ReplyTargetPreviewUiState(
    val lineIndex: Int,
    val lineStartOffset: Int,
    val lineEndOffset: Int,
    val lineBreakEndOffset: Int,
    val anchorLine: String,
    val candidates: List<ReplyTargetCandidateUiModel>,
    val selectedCandidate: ReplyTargetCandidateUiModel,
    val hasMoreCandidates: Boolean,
) {
    /** 安価行に含まれる全候補番号を出現順で返す。 */
    val referencedNumbers: List<Int>
        get() = candidates.map(ReplyTargetCandidateUiModel::postNumber)

    /** 選択済み候補以外の件数を返す。 */
    val overflowCount: Int
        get() = (candidates.size - 1).coerceAtLeast(0)
}

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
    val replyTargets: List<ReplyTargetPreviewUiState> = emptyList(),
    val replySourcePreviews: List<ReplyPreviewPostUiModel> = emptyList(),
    val replySourceNumbers: List<Int> = emptyList(),
    val bodySegments: List<PostBodySegment> = emptyList(),
) {
    /** 先頭三件の表示後に省略される返信元件数。 */
    val replySourceOverflowCount: Int
        get() = (replySourceNumbers.size - replySourcePreviews.size).coerceAtLeast(0)
}
