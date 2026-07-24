package com.websarva.wings.android.slevo.ui.tabs.coordinator

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.model.mergeThreadTabMetadata

/**
 * 最新の Room スナップショットに重ねて適用する保留中のタブ操作。
 * 操作は coordinator の FIFO キュー内の位置順に並ぶ。
 */
internal sealed interface ThreadTabPendingOperation {
    /** タブの存在を保証し、投影用に最新の route メタデータを保持する。 */
    data class Ensure(val tab: ThreadTabInfo) : ThreadTabPendingOperation

    /** Room が削除を確認するまでタブを非表示にする。 */
    data class Delete(val threadId: ThreadId) : ThreadTabPendingOperation

    /** Room が確認するまで要求された pin 値を投影する。 */
    data class Pin(val threadId: ThreadId, val isPinned: Boolean) : ThreadTabPendingOperation

    /** JOIN された Room query に反映されるまで共通 ThreadState 更新を投影する。 */
    data class Info(val tab: ThreadTabInfo) : ThreadTabPendingOperation
}

/**
 * 正規の Room スナップショットに保留中の操作を再適用する。
 * 返す一覧は thread ID が一意で、正規状態の順序を維持しつつ新規タブを末尾に追加する。
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

/** 正規スナップショットがこの操作の確認条件を満たすかどうかを返す。 */
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

/** JOIN された ThreadState Flow を通して確認されるフィールドだけを比較する。 */
private fun ThreadTabInfo.matchesThreadMetadata(expected: ThreadTabInfo): Boolean {
    val merged = mergeThreadTabMetadata(this, expected)
    return title == merged.title &&
        boardName == merged.boardName &&
        boardUrl == merged.boardUrl &&
        boardId == merged.boardId &&
        resCount == merged.resCount
}
