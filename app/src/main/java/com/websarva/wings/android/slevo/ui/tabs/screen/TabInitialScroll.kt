package com.websarva.wings.android.slevo.ui.tabs.screen

/**
 * タブ一覧の初期スクロール対象indexを表示順とselected keyから導出する。
 *
 * selected keyが表示一覧に存在しない場合は末尾へフォールバックし、空一覧ではnullを返す。
 */
internal fun <T> resolveInitialTabListIndex(
    items: List<T>,
    selectedKey: String?,
    keyOf: (T) -> String,
): Int? {
    if (items.isEmpty()) {
        return null
    }

    return items.indexOfFirst { item -> keyOf(item) == selectedKey }
        .takeIf { index -> index >= 0 }
        ?: items.lastIndex
}
