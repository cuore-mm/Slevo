package com.websarva.wings.android.slevo.ui.thread.viewmodel

import com.websarva.wings.android.slevo.data.datasource.local.entity.history.PendingOwnPostEntity
import com.websarva.wings.android.slevo.ui.thread.state.ThreadPostUiModel
import javax.inject.Inject

/**
 * 投稿成功時の入力値と取得済みレスをprovider非依存で比較するmatcher。
 *
 * 本文の正規化後完全一致を必須とし、投稿時に指定された非空の名前とメールだけを追加条件にする。
 */
class OwnPostMatcher @Inject constructor() {
    /** 未確定投稿と取得レスが同一投稿である可能性を判定する。 */
    fun matches(pending: PendingOwnPostEntity, post: ThreadPostUiModel): Boolean {
        // 本文が一致しない場合はidentityを比較せず、候補から除外する。
        if (normalizeContent(pending.content) != normalizeContent(post.body.content)) {
            return false
        }
        return matchesOptionalIdentity(pending.name, post.header.name) &&
            matchesOptionalIdentity(pending.email, post.header.email)
    }

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
