package com.websarva.wings.android.slevo.ui.tabs

/**
 * 検索結果リストを先頭表示する対象ページとクエリを保持する。
 *
 * UI は現在の検索クエリと一致する要求だけを処理し、
 * 古いクエリに対する先頭表示要求の誤実行を防ぐ。
 */
data class TabListScrollToTopRequest(
    val page: Int,
    val query: String,
)
