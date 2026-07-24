package com.websarva.wings.android.slevo.ui.tabs.model

import com.websarva.wings.android.slevo.data.model.ThreadId
import java.net.URI

/**
 * Merges route-derived metadata into a canonical tab without changing tab-row or history fields.
 * Placeholder values never replace resolved metadata, response counts only move upward, and a
 * board URL is accepted as a fallback only when the canonical value is empty and identities match.
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

/** Returns whether [title] is empty or the deterministic title generated from [threadId]. */
private fun String.isPlaceholderThreadTitle(threadId: ThreadId): Boolean =
    isBlank() || this == threadId.initialThreadTitle()

/** Returns whether [boardName] is empty or merely repeats the route board URL. */
private fun String.isPlaceholderBoardName(boardUrl: String): Boolean =
    isBlank() || this == boardUrl

/** Returns whether [boardUrl] identifies the board encoded by [threadId]. */
private fun String.matchesBoardIdentity(threadId: ThreadId): Boolean {
    val identity = threadId.value.split('/', limit = 3)
    if (identity.size != 3) return false
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    val host = uri.host ?: return false
    val board = uri.path.orEmpty().trim('/').substringBefore('/')
    return host == identity[0] && board == identity[1]
}

/** Builds the same initial thread URL title used for route-created tabs. */
private fun ThreadId.initialThreadTitle(): String? {
    val identity = value.split('/', limit = 3)
    if (identity.size != 3) return null
    return "https://${identity[0]}/test/read.cgi/${identity[1]}/${identity[2]}/"
}
