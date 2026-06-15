package com.websarva.wings.android.slevo.ui.thread.state

/**
 * スレッド一覧の LazyColumn に表示する最終行を表す。
 *
 * 投稿行と将来追加される divider 系行を統一的に扱うための sealed 型。
 */
sealed interface ThreadListItem {

    /**
     * LazyColumn の item key として使用する一意な文字列。
     */
    val stableKey: String

    /**
     * 投稿行。
     *
     * @param displayPost 投稿の表示属性
     * @param groupIndex 所属する更新グループの index
     * @param role 一覧上の表示ロール
     * @param occurrenceIndex 同一 (groupIndex, role, num) 内での出現順
     * @param stableKey LazyColumn で使用する一意な key
     */
    data class PostRow(
        val displayPost: DisplayPost,
        val groupIndex: Int,
        val role: PostDisplayRole,
        val occurrenceIndex: Int,
        override val stableKey: String,
    ) : ThreadListItem
}

/**
 * 投稿行の表示文脈を表す。
 *
 * 同じレス番号でも、通常表示・dimmed 親表示・新着範囲表示などで別の役割を持つ。
 */
enum class PostDisplayRole {
    NORMAL,
    NEW_ARRIVAL,
    DIMMED_PARENT,
}
