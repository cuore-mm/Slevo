package com.websarva.wings.android.slevo.data.notification

import com.websarva.wings.android.slevo.data.model.ReplyInfo
import com.websarva.wings.android.slevo.data.util.ReplyAnchorParser

/**
 * 一回の取得で検出された、通知対象の返信レスを表す値。
 *
 * 返信レス番号を一意キーとして扱い、複数の自レスを参照する場合も一候補にまとめる。
 */
data class ReplyNotificationCandidate(
    val replyResNo: Int,
    val targetOwnResNumbers: List<Int>,
    val messagePreview: String,
)

/**
 * 新着レスから自分宛ての返信候補を抽出する純粋な検出器。
 */
object ReplyNotificationDetector {
    /**
     * 取得前境界より後のレスから、他者による自レス参照だけを検出する。
     * 初回取得またはレス数が減少した取得では過去レスを通知対象にしない。
     */
    fun detect(
        posts: List<ReplyInfo>,
        previousResCount: Int?,
        ownPostNumbers: Set<Int>,
    ): List<ReplyNotificationCandidate> {
        val boundary = previousResCount ?: return emptyList()
        if (posts.size <= boundary || posts.size < previousResCount) {
            return emptyList()
        }
        if (ownPostNumbers.isEmpty()) {
            return emptyList()
        }

        return posts
            .drop(boundary)
            .mapIndexedNotNull { offset, post ->
                val replyResNo = boundary + offset + 1
                if (replyResNo in ownPostNumbers) {
                    return@mapIndexedNotNull null
                }
                val referencedOwnPosts = ReplyAnchorParser
                    .extractReferencedNumbers(post.content)
                    .filter { number -> number in ownPostNumbers }
                if (referencedOwnPosts.isEmpty()) {
                    return@mapIndexedNotNull null
                }
                ReplyNotificationCandidate(
                    replyResNo = replyResNo,
                    targetOwnResNumbers = referencedOwnPosts,
                    messagePreview = post.content.toNotificationPreview(),
                )
            }
    }

    /** 通知本文として読みやすい一行のプレビューへ整形する。 */
    private fun String.toNotificationPreview(): String = replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_PREVIEW_LENGTH)

    private const val MAX_PREVIEW_LENGTH = 120
}
