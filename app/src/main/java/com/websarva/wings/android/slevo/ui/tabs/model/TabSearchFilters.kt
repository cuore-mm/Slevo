package com.websarva.wings.android.slevo.ui.tabs.model

import com.websarva.wings.android.slevo.ui.util.matchesSearchQuery

/**
 * タブ一覧検索で利用する板タブ絞り込みを提供する。
 */
fun filterBoardTabsByQuery(tabs: List<BoardTabInfo>, query: String): List<BoardTabInfo> {
    if (query.isBlank()) return tabs

    // 板タブは板名と表示サービス名のどちらかに一致すれば表示対象とする。
    return tabs.filter { tab ->
        matchesSearchQuery(tab.boardName, query) || matchesSearchQuery(tab.serviceName, query)
    }
}

/**
 * タブ一覧検索で利用するスレッドタブ絞り込みを提供する。
 */
fun filterThreadTabsByQuery(tabs: List<ThreadTabInfo>, query: String): List<ThreadTabInfo> {
    if (query.isBlank()) return tabs

    // スレッドタブはスレ名か板名に一致すれば表示対象とする。
    return tabs.filter { tab ->
        matchesSearchQuery(tab.title, query) || matchesSearchQuery(tab.boardName, query)
    }
}
