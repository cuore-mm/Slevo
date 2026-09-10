package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.model.TabPage
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tabsの遷移元に応じた初期ページ導出を検証する。
 */
class TabsScaffoldTest {

    @Test
    fun deriveTabsInitialPage_prefersBoardForBoardSource() {
        val result = deriveTabsInitialPage(
            sourceRoute = AppRoute.Board(
                boardName = "board",
                boardUrl = "https://example.com/board/",
            ),
            lastPage = TabPage.THREAD.index,
        )

        assertEquals(TabPage.BOARD.index, result)
    }

    @Test
    fun deriveTabsInitialPage_prefersThreadForThreadSource() {
        val result = deriveTabsInitialPage(
            sourceRoute = AppRoute.Thread(
                threadKey = "1",
                boardName = "board",
                boardUrl = "https://example.com/board/",
            ),
            lastPage = TabPage.BOARD.index,
        )

        assertEquals(TabPage.THREAD.index, result)
    }

    @Test
    fun deriveTabsInitialPage_restoresLastPageWithoutBbsSource() {
        assertEquals(
            TabPage.THREAD.index,
            deriveTabsInitialPage(sourceRoute = null, lastPage = TabPage.THREAD.index),
        )
    }
}
