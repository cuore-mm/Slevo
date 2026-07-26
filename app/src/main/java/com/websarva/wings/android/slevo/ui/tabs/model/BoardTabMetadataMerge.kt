package com.websarva.wings.android.slevo.ui.tabs.model

/**
 * Board の placeholder metadata を解決済み canonical row へマージする純粋関数。
 * 既存 row の順序、pin、scroll、解決済み ID を保持し、repository と pending projection で共有する。
 */
internal fun mergeBoardTabMetadata(
    current: BoardTabInfo?,
    incoming: BoardTabInfo,
): BoardTabInfo {
    if (current == null) return incoming
    return current.copy(
        boardId = incoming.boardId.takeIf { it != 0L } ?: current.boardId,
        boardName = incoming.boardName.takeIf { it.isNotBlank() } ?: current.boardName,
        serviceName = incoming.serviceName.takeIf { it.isNotBlank() } ?: current.serviceName,
        bookmarkColorName = incoming.bookmarkColorName ?: current.bookmarkColorName,
        firstVisibleItemIndex = current.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = current.firstVisibleItemScrollOffset,
        isPinned = current.isPinned,
    )
}
