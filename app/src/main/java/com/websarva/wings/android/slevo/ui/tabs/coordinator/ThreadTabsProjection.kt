package com.websarva.wings.android.slevo.ui.tabs.coordinator

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.tabs.controller.IndexedTabOperation
import com.websarva.wings.android.slevo.ui.tabs.controller.foldEffectiveTabs
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

    /** 指定された複数スレッドを一つのpending除去として扱う。 */
    data class BulkDelete(
        val threadIds: List<ThreadId>,
        val requestedSelection: String?,
    ) : ThreadTabPendingOperation

    /** Room が確認するまで要求された pin 値を投影する。 */
    data class Pin(val threadId: ThreadId, val isPinned: Boolean) : ThreadTabPendingOperation

    /** JOIN された Room query に反映されるまで共通 ThreadState 更新を投影する。 */
    data class Info(val tab: ThreadTabInfo) : ThreadTabPendingOperation

    /** Room が確認するまで要求された stable key 順を投影する。 */
    data class Reorder(val threadIds: List<ThreadId>) : ThreadTabPendingOperation
}

/**
 * 正規の Room スナップショットに保留中の操作を再適用する。
 * 返す一覧は thread ID が一意で、正規状態の順序を維持しつつ新規タブを末尾に追加する。
 */
internal fun projectThreadTabs(
    canonicalTabs: List<ThreadTabInfo>,
    pendingOperations: List<ThreadTabPendingOperation>,
): List<ThreadTabInfo> = foldEffectiveTabs(
    canonicalTabs = canonicalTabs,
    operations = pendingOperations.map { operation ->
        when (operation) {
            is ThreadTabPendingOperation.Ensure -> IndexedTabOperation(operation.tab.id) { current ->
                if (current == null) operation.tab else mergeThreadTabMetadata(current, operation.tab)
            }
            is ThreadTabPendingOperation.Delete -> IndexedTabOperation(operation.threadId, remove = true) { current -> current }
            is ThreadTabPendingOperation.BulkDelete -> IndexedTabOperation(
                key = operation.threadIds.first(),
                remove = true,
                removeKeys = operation.threadIds.toSet(),
            ) { current -> current }
            is ThreadTabPendingOperation.Pin -> IndexedTabOperation(operation.threadId) { current ->
                current?.copy(isPinned = operation.isPinned)
            }
            is ThreadTabPendingOperation.Info -> IndexedTabOperation(operation.tab.id) { current ->
                current?.let { mergeThreadTabMetadata(it, operation.tab) }
            }
            is ThreadTabPendingOperation.Reorder -> IndexedTabOperation(
                key = operation.threadIds.first(),
                reorderKeys = operation.threadIds,
                transform = { current -> current },
            )
        }
    },
    keyOf = ThreadTabInfo::id,
)

/** 正規スナップショットがこの操作の確認条件を満たすかどうかを返す。 */
internal fun isThreadTabOperationConfirmed(
    canonicalTabs: List<ThreadTabInfo>,
    operation: ThreadTabPendingOperation,
): Boolean {
    if (operation is ThreadTabPendingOperation.BulkDelete) {
        val targetIds = operation.threadIds.toSet()
        return canonicalTabs.none { it.id in targetIds }
    }
    val actual = canonicalTabs.firstOrNull { tab ->
        when (operation) {
            is ThreadTabPendingOperation.Ensure -> tab.id == operation.tab.id
            is ThreadTabPendingOperation.Delete -> tab.id == operation.threadId
            is ThreadTabPendingOperation.BulkDelete -> false
            is ThreadTabPendingOperation.Pin -> tab.id == operation.threadId
            is ThreadTabPendingOperation.Info -> tab.id == operation.tab.id
            is ThreadTabPendingOperation.Reorder -> false
        }
    }
    return when (operation) {
            is ThreadTabPendingOperation.Ensure -> actual != null
            is ThreadTabPendingOperation.Delete -> actual == null
            is ThreadTabPendingOperation.BulkDelete -> error("BulkDelete is handled above")
            is ThreadTabPendingOperation.Pin -> actual?.isPinned == operation.isPinned
        is ThreadTabPendingOperation.Info -> actual != null
        is ThreadTabPendingOperation.Reorder -> {
            val actualKeys = canonicalTabs.map { it.id.value }
            val expectedKeys = com.websarva.wings.android.slevo.ui.tabs.controller.reorderTabs(
                canonicalTabs,
                operation.threadIds.map(ThreadId::value),
                { it.id.value },
            ).map { it.id.value }
            actualKeys == expectedKeys
        }
    }
}
