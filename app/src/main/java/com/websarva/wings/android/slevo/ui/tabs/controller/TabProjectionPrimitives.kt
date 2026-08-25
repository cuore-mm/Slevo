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
    val removeKeys: Set<Key> = emptySet(),
    val transform: (Tab?) -> Tab?,
    val reorderKeys: List<Key>? = null,
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
    val keyIndex = result.mapIndexed { index, tab -> keyOf(tab) to index }.toMap().toMutableMap()

    // --- ordered pending fold ---
    operations.forEach { operation ->
        operation.reorderKeys?.let { requestedKeys ->
            val reordered = reorderTabs(result, requestedKeys, keyOf)
            result.clear()
            result.addAll(reordered)
            keyIndex.clear()
            result.forEachIndexed { reorderedIndex, tab ->
                keyIndex[keyOf(tab)] = reorderedIndex
            }
            return@forEach
        }
        val index = keyIndex[operation.key]
        if (operation.remove) {
            val keysToRemove = operation.removeKeys.ifEmpty { setOf(operation.key) }
            if (keysToRemove.size == 1 && index != null) {
                result.removeAt(index)
                rebuildKeyIndexAfterRemoval(keyIndex, index, keysToRemove)
            } else if (keysToRemove.size > 1) {
                val filtered = result.filterNot { keyOf(it) in keysToRemove }
                if (filtered.size != result.size) {
                    result.clear()
                    result.addAll(filtered)
                    keyIndex.clear()
                    result.forEachIndexed { filteredIndex, tab ->
                        keyIndex[keyOf(tab)] = filteredIndex
                    }
                }
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
 * 既存keyを要求順へ並べ、要求に含まれない新規keyを既存相対順で末尾へ残す。
 * 未知keyと重複keyは無視し、結果のkeyは一意になる。
 */
fun <Tab : Any, Key : Any> reorderTabs(
    tabs: List<Tab>,
    requestedKeys: List<Key>,
    keyOf: (Tab) -> Key,
): List<Tab> {
    val byKey = tabs.associateBy(keyOf)
    val orderedKeys = buildList {
        requestedKeys.forEach { key ->
            if (key in byKey && key !in this) add(key)
        }
        tabs.forEach { tab ->
            val key = keyOf(tab)
            if (key !in this) add(key)
        }
    }
    return orderedKeys.mapNotNull(byKey::get)
}

/** 複数key削除後のindex mapを、削除位置以降だけ一つずつ詰め直す。 */
private fun <Key : Any> rebuildKeyIndexAfterRemoval(
    keyIndex: MutableMap<Key, Int>,
    removedIndex: Int,
    removedKeys: Set<Key>,
) {
    keyIndex.keys.toList().forEach { key ->
        val current = keyIndex[key] ?: return@forEach
        if (key in removedKeys) {
            keyIndex.remove(key)
        } else if (current > removedIndex) {
            keyIndex[key] = current - 1
        }
    }
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

/**
 * 複数タブを一覧順に削除した場合の最終選択 key を返す。
 * 既存の単体削除規則を削除対象順に折りたたみ、対象外選択の維持と隣接／末尾補正を同じ結果にする。
 */
fun <Tab : Any, Key : Any> selectionAfterTabRemovals(
    selectedKey: Key?,
    tabs: List<Tab>,
    removedKeys: List<Key>,
    keyOf: (Tab) -> Key,
): Key? {
    var currentTabs = tabs
    var currentSelection = selectedKey
    removedKeys.forEach { removedKey ->
        val removedIndex = currentTabs.indexOfFirst { keyOf(it) == removedKey }
        val remainingTabs = currentTabs.filterNot { keyOf(it) == removedKey }
        currentSelection = selectionAfterTabRemoval(
            selectedKey = currentSelection,
            removedKey = removedKey,
            removedIndex = removedIndex,
            remainingTabs = remainingTabs,
            keyOf = keyOf,
        )
        currentTabs = remainingTabs
    }
    return currentSelection
}
