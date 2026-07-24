package com.websarva.wings.android.slevo.ui.tabs.screen

import com.websarva.wings.android.slevo.ui.tabs.model.ThreadTabInfo
import com.websarva.wings.android.slevo.ui.tabs.store.TabSessionStore

/**
 * タブ一覧から確定したスレッドタブ close を retained store scope に委譲する callback を生成する。
 *
 * [ThreadTabInfo] 全体ではなく close の識別子だけを [TabSessionStore] に渡すため、呼び出し元の
 * Composition が破棄されても、処理は store が所有する scope で継続する。
 */
internal fun createThreadTabCloseHandler(
    tabSessionStore: TabSessionStore,
): (ThreadTabInfo) -> Unit = { tab ->
    // 表示モデルを retained close API が受け取る識別子へ変換する。
    tabSessionStore.requestCloseThreadTab(
        threadKey = tab.threadKey,
        boardUrl = tab.boardUrl,
    )
}
