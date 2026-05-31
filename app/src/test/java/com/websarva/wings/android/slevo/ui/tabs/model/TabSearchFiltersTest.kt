package com.websarva.wings.android.slevo.ui.tabs.model

import com.google.common.truth.Truth.assertThat
import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.ui.util.matchesSearchQuery
import org.junit.Test

/**
 * タブ一覧検索の文字列一致とフィルタリング規則を検証するユニットテスト。
 */
class TabSearchFiltersTest {

    /**
     * 空クエリ時は全件一致として扱うことを確認する。
     */
    @Test
    fun matchesSearchQuery_returnsTrue_whenQueryIsBlank() {
        assertThat(matchesSearchQuery(content = "てすと", query = "")).isTrue()
    }

    /**
     * ひらがな/カタカナを区別せず一致判定できることを確認する。
     */
    @Test
    fun matchesSearchQuery_ignoresKanaDifference() {
        assertThat(matchesSearchQuery(content = "テスト", query = "てす")).isTrue()
    }

    /**
     * 大文字小文字を区別せず一致判定できることを確認する。
     */
    @Test
    fun matchesSearchQuery_ignoresCaseDifference() {
        assertThat(matchesSearchQuery(content = "SLEVO", query = "sle")).isTrue()
    }

    /**
     * 板名とサービス名のどちらでも板タブが絞り込まれることを確認する。
     */
    @Test
    fun filterBoardTabsByQuery_filtersByBoardNameOrServiceName() {
        val tabs = listOf(
            BoardTabInfo(1L, "ニュース速報", "https://example.com/news/", "example.com"),
            BoardTabInfo(2L, "ゲーム", "https://hoge.net/game/", "hoge.net"),
        )

        val byBoardName = filterBoardTabsByQuery(tabs, "そくほう")
        val byServiceName = filterBoardTabsByQuery(tabs, "HOGE")

        assertThat(byBoardName.map { it.boardId }).containsExactly(1L)
        assertThat(byServiceName.map { it.boardId }).containsExactly(2L)
    }

    /**
     * 板名とスレ名のどちらでもスレッドタブが絞り込まれることを確認する。
     */
    @Test
    fun filterThreadTabsByQuery_filtersByThreadNameOrBoardName() {
        val tabs = listOf(
            ThreadTabInfo(
                id = ThreadId.of("example.com", "news", "100"),
                title = "テストスレ",
                boardName = "ニュース速報",
                boardUrl = "https://example.com/news/",
                boardId = 1L,
            ),
            ThreadTabInfo(
                id = ThreadId.of("hoge.net", "game", "200"),
                title = "雑談",
                boardName = "ゲーム",
                boardUrl = "https://hoge.net/game/",
                boardId = 2L,
            ),
        )

        val byThreadName = filterThreadTabsByQuery(tabs, "てすと")
        val byBoardName = filterThreadTabsByQuery(tabs, "ゲーム")

        assertThat(byThreadName.map { it.title }).containsExactly("テストスレ")
        assertThat(byBoardName.map { it.title }).containsExactly("雑談")
    }
}
