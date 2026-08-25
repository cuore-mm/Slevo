package com.websarva.wings.android.slevo.data.model

import com.websarva.wings.android.slevo.ui.util.parseBoardUrl
import com.websarva.wings.android.slevo.ui.util.parseServiceName

/**
 * 未確定投稿と取得スレッドを対応付ける安定した文字列キー。
 *
 * provider、板、スレッドを組み合わせて識別し、Roomの内部IDやサービス表示名の登録状態に
 * 依存しない。全フィールドが空でない場合だけ照合対象として利用できる。
 */
data class OwnPostThreadScope(
    val providerId: String,
    val boardKey: String,
    val threadKey: String,
) {
    companion object {
        /** 板URLとスレッドキーから照合用スコープを生成する。 */
        fun from(boardUrl: String, threadKey: String): OwnPostThreadScope? {
            // 不正URLや板URLは、別スレッドの投稿と誤照合しないため対象外にする。
            val providerId = parseServiceName(boardUrl).trim()
            val boardKey = parseBoardUrl(boardUrl)?.second?.trim().orEmpty()
            val normalizedThreadKey = threadKey.trim()
            if (providerId.isEmpty() || boardKey.isEmpty() || normalizedThreadKey.isEmpty()) {
                return null
            }
            return OwnPostThreadScope(providerId, boardKey, normalizedThreadKey)
        }
    }
}
