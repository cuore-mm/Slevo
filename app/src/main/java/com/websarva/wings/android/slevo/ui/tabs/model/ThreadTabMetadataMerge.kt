package com.websarva.wings.android.slevo.ui.tabs.model

import com.websarva.wings.android.slevo.data.model.ThreadId
import java.net.URI

/**
 * タブ行や履歴のフィールドを変更せず、route 由来のメタデータを正規タブにマージする。
 * プレースホルダー値で解決済みメタデータを置き換えず、レス数は増加する場合だけ更新する。
 * board URL は正規値が空で識別情報が一致する場合だけフォールバックとして受け入れる。
 */
internal fun mergeThreadTabMetadata(
    current: ThreadTabInfo,
    incoming: ThreadTabInfo,
): ThreadTabInfo {
    val title = if (incoming.title.isPlaceholderThreadTitle(current.id) &&
        !current.title.isPlaceholderThreadTitle(current.id)
    ) {
        current.title
    } else {
        incoming.title
    }
    val boardName = if (incoming.boardName.isPlaceholderBoardName(incoming.boardUrl) &&
        !current.boardName.isPlaceholderBoardName(current.boardUrl)
    ) {
        current.boardName
    } else {
        incoming.boardName
    }
    val boardUrl = when {
        current.boardUrl.isNotBlank() -> current.boardUrl
        incoming.boardUrl.matchesBoardIdentity(current.id) -> incoming.boardUrl
        else -> current.boardUrl
    }

    return current.copy(
        title = title,
        boardName = boardName,
        boardUrl = boardUrl,
        boardId = if (incoming.boardId != 0L) incoming.boardId else current.boardId,
        resCount = maxOf(current.resCount, incoming.resCount),
        prevResCount = current.prevResCount,
        lastReadResNo = current.lastReadResNo,
        firstNewResNo = current.firstNewResNo,
        newResCount = current.newResCount,
    )
}

/** [title] が空、または [threadId] から決定的に生成されたタイトルかどうかを返す。 */
private fun String.isPlaceholderThreadTitle(threadId: ThreadId): Boolean =
    isBlank() || this == threadId.initialThreadTitle()

/** [boardName] が空、または route の board URL をそのまま繰り返しているかどうかを返す。 */
private fun String.isPlaceholderBoardName(boardUrl: String): Boolean =
    isBlank() || this == boardUrl

/** [boardUrl] が [threadId] に含まれる board を識別しているかどうかを返す。 */
private fun String.matchesBoardIdentity(threadId: ThreadId): Boolean {
    val identity = threadId.value.split('/', limit = 3)
    if (identity.size != 3) return false
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    val host = uri.host ?: return false
    val board = uri.path.orEmpty().trim('/').substringBefore('/')
    return host == identity[0] && board == identity[1]
}

/** route から作成するタブで使用する初期スレッド URL タイトルを組み立てる。 */
private fun ThreadId.initialThreadTitle(): String? {
    val identity = value.split('/', limit = 3)
    if (identity.size != 3) return null
    return "https://${identity[0]}/test/read.cgi/${identity[1]}/${identity[2]}/"
}
