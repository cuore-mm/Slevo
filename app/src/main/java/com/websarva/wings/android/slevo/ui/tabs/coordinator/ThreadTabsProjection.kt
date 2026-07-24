package com.websarva.wings.android.slevo.ui.tabs.coordinator

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.mergeThreadTabMetadata

/**
 * A pending tab operation that is applied on top of the latest Room snapshot.
 * Operations are ordered by their position in the coordinator FIFO queue.
 */
internal sealed interface ThreadTabPendingOperation {
    /** Ensures a tab exists and carries the latest route metadata for projection. */
    data class Ensure(val tab: ThreadTabInfo) : ThreadTabPendingOperation

    /** Hides a tab until Room confirms its deletion. */
    data class Delete(val threadId: ThreadId) : ThreadTabPendingOperation

    /** Projects the requested pin value until Room confirms it. */
    data class Pin(val threadId: ThreadId, val isPinned: Boolean) : ThreadTabPendingOperation

    /** Projects a common ThreadState update until the joined Room query reflects it. */
    data class Info(val tab: ThreadTabInfo) : ThreadTabPendingOperation
}

/**
 * Reapplies pending operations to a canonical Room snapshot.
 * The returned list has unique thread IDs and preserves canonical order, appending new tabs.
 */
internal fun projectThreadTabs(
    canonicalTabs: List<ThreadTabInfo>,
    pendingOperations: List<ThreadTabPendingOperation>,
): List<ThreadTabInfo> {
    val projected = canonicalTabs.toMutableList()
    pendingOperations.forEach { operation ->
        when (operation) {
            is ThreadTabPendingOperation.Ensure -> {
                val index = projected.indexOfFirst { it.id == operation.tab.id }
                if (index >= 0) {
                    projected[index] = mergeThreadTabMetadata(projected[index], operation.tab)
                } else {
                    projected += operation.tab
                }
            }

            is ThreadTabPendingOperation.Delete -> {
                projected.removeAll { it.id == operation.threadId }
            }

            is ThreadTabPendingOperation.Pin -> {
                val index = projected.indexOfFirst { it.id == operation.threadId }
                if (index >= 0) {
                    projected[index] = projected[index].copy(isPinned = operation.isPinned)
                }
            }

            is ThreadTabPendingOperation.Info -> {
                val index = projected.indexOfFirst { it.id == operation.tab.id }
                if (index >= 0) {
                    projected[index] = mergeThreadTabMetadata(projected[index], operation.tab)
                }
            }
        }
    }
    return projected.distinctBy { it.id }
}

/** Returns whether the canonical snapshot satisfies this operation's confirmation contract. */
internal fun isThreadTabOperationConfirmed(
    canonicalTabs: List<ThreadTabInfo>,
    operation: ThreadTabPendingOperation,
): Boolean {
    val actual = canonicalTabs.firstOrNull { tab ->
        when (operation) {
            is ThreadTabPendingOperation.Ensure -> tab.id == operation.tab.id
            is ThreadTabPendingOperation.Delete -> tab.id == operation.threadId
            is ThreadTabPendingOperation.Pin -> tab.id == operation.threadId
            is ThreadTabPendingOperation.Info -> tab.id == operation.tab.id
        }
    }
    return when (operation) {
        is ThreadTabPendingOperation.Ensure -> actual != null
        is ThreadTabPendingOperation.Delete -> actual == null
        is ThreadTabPendingOperation.Pin -> actual?.isPinned == operation.isPinned
        is ThreadTabPendingOperation.Info -> actual?.matchesThreadMetadata(operation.tab) == true
    }
}

/** Compares only the fields that are confirmed through the joined ThreadState Flow. */
private fun ThreadTabInfo.matchesThreadMetadata(expected: ThreadTabInfo): Boolean {
    val merged = mergeThreadTabMetadata(this, expected)
    return title == merged.title &&
        boardName == merged.boardName &&
        boardUrl == merged.boardUrl &&
        boardId == merged.boardId &&
        resCount == merged.resCount
}
