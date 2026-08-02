package com.websarva.wings.android.slevo.ui.bbsroute

/**
 * タブ選択の解決結果を表す共有状態。
 * [Selected] は一覧内に存在する key、[PendingMissing] は既知の反映待ちで一時的に不在の key を保持する。
 */
sealed interface TabSelectionResolution<out Key : Any> {
    /** 初回の canonical tab 一覧がまだ確定していない状態。 */
    data object Loading : TabSelectionResolution<Nothing>

    /** 同じ snapshot の一覧に存在する key が選択されている状態。 */
    data class Selected<Key : Any>(val key: Key) : TabSelectionResolution<Key>

    /** 既知の pending cause が解消するまで選択 key を保持する状態。 */
    data class PendingMissing<Key : Any>(val key: Key) : TabSelectionResolution<Key>

    /** canonical load 完了後の空一覧状態。 */
    data object Empty : TabSelectionResolution<Nothing>
}

/**
 * UI に渡すタブ一覧と選択解決結果を同じ emission で保持する immutable snapshot。
 * [TabSelectionResolution.Selected] の key は [tabs] に存在し、[TabSelectionResolution.Empty] は空一覧に限る。
 */
data class TabPresentationState<TabInfo : Any, Key : Any>(
    val tabs: List<TabInfo>,
    val selection: TabSelectionResolution<Key>,
)

/**
 * Pager が表示・同期すべき内容を解決した結果。
 * [Selected] だけが指定 index への programmatic scroll を許可し、pending 中は現在ページを保持する。
 */
sealed interface TabDisplayDecision {
    /** 選択 key に対応する page index。 */
    data class Selected(val index: Int) : TabDisplayDecision

    /** 現在の pager page を維持する指示。 */
    data object PreserveCurrent : TabDisplayDecision

    /** 初回一覧を待つ状態。 */
    data object Loading : TabDisplayDecision

    /** 表示する tab content がない状態。 */
    data object Empty : TabDisplayDecision
}

/**
 * atomic presentation state から Pager の表示判断を導出する。
 * Selected key の invariant 違反は UI fallback で隠さず例外として通知する。
 */
internal fun <TabInfo : Any, Key : Any> deriveTabDisplayDecision(
    presentationState: TabPresentationState<TabInfo, Key>,
    getKey: (TabInfo) -> Key,
): TabDisplayDecision {
    return when (val selection = presentationState.selection) {
        TabSelectionResolution.Loading -> TabDisplayDecision.Loading
        TabSelectionResolution.Empty -> {
            check(presentationState.tabs.isEmpty()) { "Empty presentation state must not contain tabs" }
            TabDisplayDecision.Empty
        }
        is TabSelectionResolution.PendingMissing -> {
            check(presentationState.tabs.none { getKey(it) == selection.key }) {
                "PendingMissing key must be absent from the presentation tabs"
            }
            TabDisplayDecision.PreserveCurrent
        }
        is TabSelectionResolution.Selected -> {
            val index = presentationState.tabs.indexOfFirst { getKey(it) == selection.key }
            check(index >= 0) { "Selected key must exist in the presentation tabs" }
            TabDisplayDecision.Selected(index)
        }
    }
}

/**
 * 一覧と resolution を個別に受け取る呼び出し用 overload。
 * 内部では必ず同じ immutable snapshot の導出処理へ委譲する。
 */
internal fun <TabInfo : Any, Key : Any> deriveTabDisplayDecision(
    tabs: List<TabInfo>,
    resolution: TabSelectionResolution<Key>,
    getKey: (TabInfo) -> Key,
): TabDisplayDecision = deriveTabDisplayDecision(TabPresentationState(tabs, resolution), getKey)
