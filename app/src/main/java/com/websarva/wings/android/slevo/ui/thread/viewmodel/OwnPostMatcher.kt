package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PendingOwnPostEntity
import com.websarva.wings.android.slevo.data.util.OwnPostDateParser
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import javax.inject.Inject

/**
 * 投稿成功時の入力値と取得済みレスをprovider非依存で比較するmatcher。
 *
 * 本文、日時、投稿者ID、identityを独立したfilterとして提供し、照合順序はUseCaseが管理する。
 */
class OwnPostMatcher @Inject constructor() {
    /** 未確定投稿と取得レスが同一投稿である可能性を判定する。 */
    fun matches(pending: PendingOwnPostEntity, post: ThreadPostUiModel): Boolean {
        return matchesContent(pending, post) && matchesIdentity(pending, post)
    }

    /** 投稿本文を正規化し、取得レスの本文と完全一致するか判定する。 */
    fun matchesContent(pending: PendingOwnPostEntity, post: ThreadPostUiModel): Boolean =
        normalizeContent(pending.content) == normalizeContent(post.body.content)

    /** 保存済みサーバー時刻とdat日時が許容差内で一致するか判定する。 */
    fun matchesServerDate(pending: PendingOwnPostEntity, post: ThreadPostUiModel): Boolean {
        val expectedMillis = pending.serverPostDateMillis ?: return true
        val actualMillis = OwnPostDateParser.parseDatDate(post.header.date) ?: return false
        return OwnPostDateParser.isWithinTolerance(actualMillis, expectedMillis)
    }

    /** 保存済み投稿者IDヒントが取得レスのID prefixと一致するか判定する。 */
    fun matchesPosterId(pending: PendingOwnPostEntity, post: ThreadPostUiModel): Boolean {
        val hint = pending.posterIdHint?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return post.header.id.trim().startsWith(hint)
    }

    /** 入力済みの名前とメールだけをtrim後の完全一致で比較する。 */
    fun matchesIdentity(pending: PendingOwnPostEntity, post: ThreadPostUiModel): Boolean =
        matchesOptionalIdentity(pending.name, post.header.name) &&
            matchesOptionalIdentity(pending.email, post.header.email)

    /** 本文の改行と行末空白だけをprovider非依存に正規化する。 */
    internal fun normalizeContent(content: String): String {
        return content
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .joinToString("\n") { line -> line.trimEnd(' ', '\t') }
            .trimEnd('\n')
    }

    /** 入力されていないidentityはwildcardとして扱い、それ以外はtrim後に比較する。 */
    private fun matchesOptionalIdentity(expected: String, actual: String): Boolean {
        return expected.trim().isEmpty() || expected.trim() == actual.trim()
    }
}
