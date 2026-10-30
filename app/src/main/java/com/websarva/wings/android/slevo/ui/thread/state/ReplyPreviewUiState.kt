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

/** 取得済みレス数を超える未取得レス番号の連続範囲を表す。 */
data class ReplyTargetUnavailableRange(
    val firstPostNumber: Int,
    val lastPostNumber: Int,
)

/** 返信プレビュー末尾に表示する一覧の総件数と未取得範囲を表す。 */
sealed interface ReplyPreviewFooterUiState {
    /** 一覧に表示する取得済み候補の総件数。 */
    data class More(val count: Int) : ReplyPreviewFooterUiState

    /** 取得できない返信先番号の範囲。 */
    data class Unavailable(val ranges: List<ReplyTargetUnavailableRange>) : ReplyPreviewFooterUiState

    /** 省略候補と未取得範囲を併記する状態。 */
    data class MoreAndUnavailable(
        val count: Int,
        val ranges: List<ReplyTargetUnavailableRange>,
    ) : ReplyPreviewFooterUiState
}

/** 省略候補と未取得範囲から表示するフッター状態を組み立てる。 */
fun replyPreviewFooterState(
    overflowCount: Int,
    overflowNumbers: List<Int>,
    unavailableRanges: List<ReplyTargetUnavailableRange>,
): ReplyPreviewFooterUiState? {
    val hasMore = overflowCount > 0 && overflowNumbers.isNotEmpty()
    val totalCount = overflowNumbers.size
    return when {
        hasMore && unavailableRanges.isNotEmpty() ->
            ReplyPreviewFooterUiState.MoreAndUnavailable(totalCount, unavailableRanges)
        hasMore -> ReplyPreviewFooterUiState.More(totalCount)
        unavailableRanges.isNotEmpty() -> ReplyPreviewFooterUiState.Unavailable(unavailableRanges)
        else -> null
    }
}

/** 安価だけの行に挿入する返信先と候補一覧を表す。 */
data class ReplyTargetPreviewUiState(
    val lineIndex: Int,
    val lineStartOffset: Int,
    val lineEndOffset: Int,
    val lineBreakEndOffset: Int,
    val anchorLine: String,
    val candidates: List<ReplyTargetCandidateUiModel>,
    val selectedCandidate: ReplyTargetCandidateUiModel?,
    val unavailableRanges: List<ReplyTargetUnavailableRange>,
) {
    /** 安価行に含まれる取得済み候補番号を出現順で返す。 */
    val referencedNumbers: List<Int>
        get() = candidates.map(ReplyTargetCandidateUiModel::postNumber)

    /** 選択済み候補以外の取得済み候補件数を返す。 */
    val overflowCount: Int
        get() = (candidates.size - if (selectedCandidate == null) 0 else 1).coerceAtLeast(0)

    /** 返信先の候補と未取得範囲に対応するフッター状態。 */
    val footerState: ReplyPreviewFooterUiState?
        get() = replyPreviewFooterState(overflowCount, referencedNumbers, unavailableRanges)
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

    /** 返信元一覧末尾に表示する省略候補の状態。 */
    val replySourceFooterState: ReplyPreviewFooterUiState?
        get() = replyPreviewFooterState(replySourceOverflowCount, replySourceNumbers, emptyList())
}
