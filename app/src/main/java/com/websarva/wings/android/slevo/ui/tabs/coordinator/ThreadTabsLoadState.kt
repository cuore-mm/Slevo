package com.websarva.wings.android.slevo.ui.tabs.coordinator

import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo

/**
 * 永続化されたスレッドタブのスナップショット読み込み状態。
 * `Loaded(emptyList())` は有効な DB 状態であり、[Loading] とは区別される。
 */
sealed interface ThreadTabsLoadState {
    /** 初回の Room スナップショットをまだ受信していない。 */
    data object Loading : ThreadTabsLoadState

    /** 正規の Room スナップショットを受信済みである。 */
    data class Loaded(val tabs: List<ThreadTabInfo>) : ThreadTabsLoadState
}
