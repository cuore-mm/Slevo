package com.websarva.wings.android.slevo.ui.tabs.controller

import com.websarva.wings.android.slevo.ui.bbsroute.TabPresentationState
import com.websarva.wings.android.slevo.ui.bbsroute.TabSelectionResolution

/**
 * indexed fold で canonical 一覧へ ordered pending operation を重ねる入力。
 * keyIndex は一回だけ作り、pending ごとの全件検索を避ける。
 */
data class IndexedTabOperation<Tab : Any, Key : Any>(
    val key: Key,
    val remove: Boolean = false,
    val transform: (Tab?) -> Tab?,
)

/**
 * canonical と pending を acceptance order で一度だけ折りたたみ、effective tabs を作る。
 * 既存順序を維持し、新規 key は pending の初回出現位置で末尾へ追加する。
 */
fun <Tab : Any, Key : Any> foldEffectiveTabs(
    canonicalTabs: List<Tab>,
    operations: List<IndexedTabOperation<Tab, Key>>,
    keyOf: (Tab) -> Key,
): List<Tab> {
    // --- canonical index ---
    val result = canonicalTabs.toMutableList()
    val keyIndex = result.mapIndexed { index, tab -> keyOf(tab) to index }.toMutableMap()

    // --- ordered pending fold ---
    operations.forEach { operation ->
        val index = keyIndex[operation.key]
        if (operation.remove) {
            if (index != null) {
                result.removeAt(index)
                keyIndex.keys.toList().forEach { key ->
                    val current = keyIndex[key] ?: return@forEach
                    if (current > index) keyIndex[key] = current - 1
                }
                keyIndex.remove(operation.key)
            }
            return@forEach
        }
        val current = index?.let(result::get)
        val transformed = operation.transform(current) ?: return@forEach
        if (index == null) {
            keyIndex[operation.key] = result.size
            result += transformed
        } else {
            result[index] = transformed
        }
    }

    // --- stable result ---
    return result
}

/**
 * effective tabs と pending cause から既存 UI 契約の選択解決を作る。
 * Loading、PendingMissing、Empty、Selected を一つの immutable presentation にまとめる。
 */
fun <Tab : Any, Key : Any> resolveTabPresentation(
    tabs: List<Tab>,
    loaded: Boolean,
    requestedKey: Key?,
    pendingMissingKey: Key?,
    keyOf: (Tab) -> Key,
): TabPresentationState<Tab, Key> {
    if (!loaded) return TabPresentationState(emptyList(), TabSelectionResolution.Loading)
    if (requestedKey != null && pendingMissingKey == requestedKey && tabs.none { keyOf(it) == requestedKey }) {
        return TabPresentationState(tabs, TabSelectionResolution.PendingMissing(requestedKey))
    }
    if (tabs.isEmpty()) return TabPresentationState(tabs, TabSelectionResolution.Empty)
    val selectedKey = requestedKey?.takeIf { key -> tabs.any { keyOf(it) == key } } ?: keyOf(tabs.first())
    return TabPresentationState(tabs, TabSelectionResolution.Selected(selectedKey))
}

/**
 * 選択中タブを削除した後の隣接／末尾選択規則を返す。
 * 非選択タブの削除では、まだ存在する選択 key をそのまま保持する。
 */
fun <Tab : Any, Key : Any> selectionAfterTabRemoval(
    selectedKey: Key?,
    removedKey: Key,
    removedIndex: Int,
    remainingTabs: List<Tab>,
    keyOf: (Tab) -> Key,
): Key? {
    if (remainingTabs.isEmpty()) return null
    if (selectedKey != removedKey && selectedKey != null && remainingTabs.any { keyOf(it) == selectedKey }) {
        return selectedKey
    }
    return remainingTabs.getOrNull(removedIndex)?.let(keyOf) ?: keyOf(remainingTabs.last())
}
