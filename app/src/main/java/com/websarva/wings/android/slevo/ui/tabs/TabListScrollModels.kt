package com.websarva.wings.android.slevo.ui.tabs

/**
 * タブ一覧検索開始前の板一覧・スレッド一覧のスクロール位置を保持する。
 *
 * 各リストの先頭可见アイテムインデックスとスクロールオフセットを記録し、
 * 検索解除後の復元に使用する。
 */
data class TabSearchScrollSnapshot(
    val boardIndex: Int,
    val boardOffset: Int,
    val threadIndex: Int,
    val threadOffset: Int,
)

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

/**
 * タブ一覧画面に対する一回限りのスクロール命令。
 *
 * ViewModel が発行し、UI が実行して消費する。
 * 消費後は同じ命令が再実行されない。
 */
sealed interface TabListScrollCommand {

    /**
     * 検索解除時に保存済みのスクロール位置へ復元する命令。
     *
     * @property snapshot 検索開始前に保存したスクロール位置
     */
    data class Restore(val snapshot: TabSearchScrollSnapshot) : TabListScrollCommand

    /**
     * 検索クエリ変更時に検索結果を先頭表示する命令。
     *
     * @property page 0: 板一覧, 1: スレッド一覧
     */
    data class ScrollToTop(val page: Int) : TabListScrollCommand
}
