package com.websarva.wings.android.slevo.ui.tabs

/**
 * ドラッグ中に保持するタブキーの順序を表す。
 * 表示モデルを複製せず、開始時と現在の stable key 列だけを保持する。
 */
data class ReorderDraft(
    val originalOrder: List<String>,
    val currentOrder: List<String>,
)

/**
 * 指定キーを対象キーの位置へ移動した順序を返す。
 * 対象が見つからない場合や同じキーの場合は、入力順序を変更せず返す。
 */
fun moveKeyBeforeTarget(
    order: List<String>,
    sourceKey: String,
    targetKey: String,
): List<String> {
    val sourceIndex = order.indexOf(sourceKey)
    val targetIndex = order.indexOf(targetKey)
    if (sourceIndex < 0 || targetIndex < 0 || sourceKey == targetKey) return order

    val updated = order.toMutableList()
    val moved = updated.removeAt(sourceIndex)
    val insertionIndex = targetIndex.coerceIn(0, updated.size)
    updated.add(insertionIndex, moved)
    return updated
}

/**
 * 最新一覧へ一時キー順を適用する。
 * 消失したキーは除外し、最新一覧にだけ存在するキーは現在の相対順で末尾へ残す。
 */
fun <T> applyReorderDraft(
    items: List<T>,
    draft: ReorderDraft?,
    keyOf: (T) -> String,
): List<T> {
    if (draft == null) return items
    val byKey = items.associateBy(keyOf)
    val orderedKeys = buildList {
        draft.currentOrder.forEach { key ->
            if (key in byKey && key !in this) add(key)
        }
        items.forEach { item ->
            val key = keyOf(item)
            if (key !in this) add(key)
        }
    }
    return orderedKeys.mapNotNull(byKey::get)
}
