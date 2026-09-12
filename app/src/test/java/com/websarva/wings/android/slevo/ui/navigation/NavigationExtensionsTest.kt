package com.websarva.wings.android.slevo.ui.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ナビゲーション拡張関数のタブ選択時の遷移規則を検証するテスト。
 */
@RunWith(RobolectricTestRunner::class)
class NavigationExtensionsTest {

    @Test
    fun showBoardScreenForTabSelection_navigatesWhenCurrentScreenIsNull() {
        val controller = createController()
        val route = AppRoute.Board(
            boardName = "board-a",
            boardUrl = "https://example.com/a/",
        )

        controller.showBoardScreenForTabSelection(currentScreenRoute = null, route = route)

        assertBoardRoute(route, controller)
    }

    @Test
    fun showBoardScreenForTabSelection_keepsCurrentBoardScreen() {
        val controller = createController()
        val current = AppRoute.Board(
            boardName = "board-a",
            boardUrl = "https://example.com/a/",
        )
        val next = AppRoute.Board(
            boardName = "board-b",
            boardUrl = "https://example.com/b/",
        )

        controller.navigateToBoardScreen(current)
        controller.showBoardScreenForTabSelection(currentScreenRoute = current, route = next)

        assertBoardRoute(current, controller)
    }

    @Test
    fun showThreadScreenForTabSelection_pushesFromBoardScreen() {
        val controller = createController()
        val current = AppRoute.Board(
            boardName = "board-a",
            boardUrl = "https://example.com/a/",
        )
        val route = AppRoute.Thread(
            threadKey = "123",
            boardUrl = "https://example.com/a/",
            boardName = "board-a",
            threadTitle = "thread",
        )

        controller.navigateToBoardScreen(current)
        controller.showThreadScreenForTabSelection(currentScreenRoute = current, route = route)

        assertThreadRoute(route, controller)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.Board::class) == true)
    }

    /** Thread画面上の同種タブ選択では現在のdestinationとback stack entryを維持する。 */
    @Test
    fun showThreadScreenForTabSelection_keepsCurrentThreadScreen() {
        val controller = createController()
        val current = AppRoute.Thread(
            threadKey = "123",
            boardUrl = "https://example.com/a/",
            boardName = "board-a",
            threadTitle = "thread-a",
        )
        val next = current.copy(threadKey = "456", threadTitle = "thread-b")

        controller.navigateToThreadScreen(current)
        val currentEntryId = controller.currentBackStackEntry?.id
        controller.showThreadScreenForTabSelection(currentScreenRoute = current, route = next)

        assertThreadRoute(current, controller)
        assertEquals(currentEntryId, controller.currentBackStackEntry?.id)
    }

    @Test
    fun showBoardScreenForTabSelection_replacesCurrentThreadScreen() {
        val controller = createController()
        val current = AppRoute.Thread(
            threadKey = "123",
            boardUrl = "https://example.com/a/",
            boardName = "board-a",
            threadTitle = "thread",
        )
        val route = AppRoute.Board(
            boardName = "board-b",
            boardUrl = "https://example.com/b/",
        )

        controller.navigateToThreadScreen(current)
        controller.showBoardScreenForTabSelection(currentScreenRoute = current, route = route)

        assertBoardRoute(route, controller)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.Tabs::class) == true)
    }

    @Test
    fun showBoardScreenForTabSelection_returnsToBoardBehindThread() {
        val controller = createController()
        val previousBoard = AppRoute.Board(
            boardName = "board-a",
            boardUrl = "https://example.com/a/",
        )
        val currentThread = AppRoute.Thread(
            threadKey = "123",
            boardUrl = previousBoard.boardUrl,
            boardName = previousBoard.boardName,
            threadTitle = "thread",
        )
        val selectedBoard = AppRoute.Board(
            boardName = "board-b",
            boardUrl = "https://example.com/b/",
        )

        controller.navigateToBoardScreen(previousBoard)
        val previousBoardEntryId = controller.currentBackStackEntry?.id
        controller.navigateToThreadScreen(currentThread)
        controller.showBoardScreenForTabSelection(
            currentScreenRoute = currentThread,
            route = selectedBoard,
        )

        assertBoardRoute(previousBoard, controller)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.Tabs::class) == true)
        assertEquals(previousBoardEntryId, controller.currentBackStackEntry?.id)
    }

    /** Board起点の同種別選択ではsource BoardとTabsを選択先Boardへ置換する。 */
    @Test
    fun showBoardScreenFromTabs_replacesBoardAndKeepsPreviousHistory() {
        val controller = createController()
        val board = boardRoute("board-a")
        val selectedBoard = boardRoute("board-b")
        controller.navigate(AppRoute.BookmarkList)
        controller.navigateToBoardScreen(board)
        val boardEntryId = controller.currentBackStackEntry?.id
        val tabsEntryId = navigateToTabs(controller)

        controller.showBoardScreenFromTabs(
            sourceRoute = board,
            tabsEntryId = tabsEntryId,
            route = selectedBoard,
        )

        assertBoardRoute(selectedBoard, controller)
        assertNotEquals(boardEntryId, controller.currentBackStackEntry?.id)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.BookmarkList::class) == true)
    }

    /** 同一Board identityでも新destinationを生成し、Pagerの旧表示状態を再利用しない。 */
    @Test
    fun showBoardScreenFromTabs_replacesBoardForSameIdentity() {
        val controller = createController()
        val board = boardRoute("board-a")
        controller.navigateToBoardScreen(board)
        val boardEntryId = controller.currentBackStackEntry?.id
        val tabsEntryId = navigateToTabs(controller)

        controller.showBoardScreenFromTabs(
            sourceRoute = board,
            tabsEntryId = tabsEntryId,
            route = board,
        )

        assertBoardRoute(board, controller)
        assertNotEquals(boardEntryId, controller.currentBackStackEntry?.id)
    }

    @Test
    fun showThreadScreenFromTabs_pushesThreadAfterRemovingTabs() {
        val controller = createController()
        val board = boardRoute("board-a")
        val thread = threadRoute("1")
        controller.navigate(AppRoute.BookmarkList)
        controller.navigateToBoardScreen(board)
        val tabsEntryId = navigateToTabs(controller)

        controller.showThreadScreenFromTabs(
            sourceRoute = board,
            tabsEntryId = tabsEntryId,
            route = thread,
        )

        assertThreadRoute(thread, controller)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.Board::class) == true)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.Tabs::class) == false)
        controller.popBackStack()
        assertTrue(controller.currentBackStackEntry?.destination?.hasRoute(AppRoute.Board::class) == true)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.BookmarkList::class) == true)
    }

    /** Thread起点の同種別選択ではsource ThreadとTabsを選択先Threadへ置換する。 */
    @Test
    fun showThreadScreenFromTabs_replacesThreadAndKeepsPreviousHistory() {
        val controller = createController()
        val thread = threadRoute("1")
        val selectedThread = threadRoute("2")
        controller.navigate(AppRoute.BookmarkList)
        controller.navigateToThreadScreen(thread)
        val threadEntryId = controller.currentBackStackEntry?.id
        val tabsEntryId = navigateToTabs(controller)

        controller.showThreadScreenFromTabs(
            sourceRoute = thread,
            tabsEntryId = tabsEntryId,
            route = selectedThread,
        )

        assertThreadRoute(selectedThread, controller)
        assertNotEquals(threadEntryId, controller.currentBackStackEntry?.id)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.BookmarkList::class) == true)
    }

    /** 同一Thread identityでも新destinationを生成し、Pagerの旧表示状態を再利用しない。 */
    @Test
    fun showThreadScreenFromTabs_replacesThreadForSameIdentity() {
        val controller = createController()
        val thread = threadRoute("1")
        controller.navigateToThreadScreen(thread)
        val threadEntryId = controller.currentBackStackEntry?.id
        val tabsEntryId = navigateToTabs(controller)

        controller.showThreadScreenFromTabs(
            sourceRoute = thread,
            tabsEntryId = tabsEntryId,
            route = thread,
        )

        assertThreadRoute(thread, controller)
        assertNotEquals(threadEntryId, controller.currentBackStackEntry?.id)
    }

    @Test
    fun showBoardScreenFromTabs_popsThreadToExistingBoard() {
        val controller = createController()
        val board = boardRoute("board-a")
        val thread = threadRoute("1")
        controller.navigateToBoardScreen(board)
        val boardEntryId = controller.currentBackStackEntry?.id
        controller.navigateToThreadScreen(thread)
        val tabsEntryId = navigateToTabs(controller)

        controller.showBoardScreenFromTabs(
            sourceRoute = thread,
            tabsEntryId = tabsEntryId,
            route = boardRoute("board-b"),
        )

        assertBoardRoute(board, controller)
        assertEquals(boardEntryId, controller.currentBackStackEntry?.id)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.Tabs::class) == true)
    }

    @Test
    fun showBoardScreenFromTabs_replacesThreadWithoutExistingBoard() {
        val controller = createController()
        val thread = threadRoute("1")
        val board = boardRoute("board-b")
        controller.navigate(AppRoute.BookmarkList)
        controller.navigateToThreadScreen(thread)
        val tabsEntryId = navigateToTabs(controller)

        controller.showBoardScreenFromTabs(
            sourceRoute = thread,
            tabsEntryId = tabsEntryId,
            route = board,
        )

        assertBoardRoute(board, controller)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.BookmarkList::class) == true)
        controller.popBackStack()
        assertTrue(controller.currentBackStackEntry?.destination?.hasRoute(AppRoute.BookmarkList::class) == true)
    }

    /** Thread直下ではない古いBoardを、TabsからのBoard選択で誤って再利用しない。 */
    @Test
    fun showBoardScreenFromTabs_doesNotReuseNonAdjacentOlderBoard() {
        val controller = createController()
        val oldBoard = boardRoute("old-board")
        val thread = threadRoute("1")
        val selectedBoard = boardRoute("selected-board")
        controller.navigateToBoardScreen(oldBoard)
        controller.navigate(AppRoute.BookmarkList)
        controller.navigateToThreadScreen(thread)
        val tabsEntryId = navigateToTabs(controller)

        controller.showBoardScreenFromTabs(
            sourceRoute = thread,
            tabsEntryId = tabsEntryId,
            route = selectedBoard,
        )

        assertBoardRoute(selectedBoard, controller)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.BookmarkList::class) == true)
    }

    @Test
    fun showBoardScreenFromTabs_keepsRootTabsForRootSelection() {
        val controller = createController()
        val tabsEntryId = controller.currentBackStackEntry?.id.orEmpty()
        val board = boardRoute("board-a")

        controller.showBoardScreenFromTabs(
            sourceRoute = null,
            tabsEntryId = tabsEntryId,
            route = board,
        )

        assertBoardRoute(board, controller)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.Tabs::class) == true)
    }

    @Test
    fun showBoardScreenFromTabs_ignoresStaleTabsEntry() {
        val controller = createController()
        val tabsEntryId = controller.currentBackStackEntry?.id.orEmpty()
        val board = boardRoute("board-a")

        controller.showBoardScreenFromTabs(
            sourceRoute = board,
            tabsEntryId = "stale-$tabsEntryId",
            route = boardRoute("board-b"),
        )

        assertTrue(controller.currentBackStackEntry?.destination?.hasRoute(AppRoute.Tabs::class) == true)
    }

    @Test
    fun showBoardScreenFromTabs_doesNotNavigateWhenTabsCannotPop() {
        val controller = createController()
        val tabsEntryId = controller.currentBackStackEntry?.id.orEmpty()
        val board = boardRoute("board-a")

        controller.showBoardScreenFromTabs(
            sourceRoute = board,
            tabsEntryId = tabsEntryId,
            route = boardRoute("board-b"),
        )

        assertTrue(controller.currentBackStackEntry?.destination?.hasRoute(AppRoute.Tabs::class) == true)
    }

    private fun createController(): TestNavHostController {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return TestNavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setGraph(
                graph = createGraph(startDestination = AppRoute.Tabs) {
                    composable<AppRoute.Tabs> { }
                    composable<AppRoute.BookmarkList> { }
                    composable<AppRoute.Board> { }
                    composable<AppRoute.Thread> { }
                }
            , startDestinationArgs = null)
        }
    }

    private fun assertBoardRoute(expected: AppRoute.Board, controller: TestNavHostController) {
        val route = controller.currentBackStackEntry?.toRoute<AppRoute.Board>()
        assertTrue(controller.currentBackStackEntry?.destination?.hasRoute(AppRoute.Board::class) == true)
        assertEquals(expected.boardUrl, route?.boardUrl)
        assertEquals(expected.boardName, route?.boardName)
    }

    private fun assertThreadRoute(expected: AppRoute.Thread, controller: TestNavHostController) {
        val route = controller.currentBackStackEntry?.toRoute<AppRoute.Thread>()
        assertTrue(controller.currentBackStackEntry?.destination?.hasRoute(AppRoute.Thread::class) == true)
        assertEquals(expected.threadKey, route?.threadKey)
        assertEquals(expected.boardUrl, route?.boardUrl)
    }

    private fun navigateToTabs(controller: TestNavHostController): String {
        controller.navigate(AppRoute.Tabs)
        return controller.currentBackStackEntry?.id.orEmpty()
    }

    private fun boardRoute(name: String): AppRoute.Board = AppRoute.Board(
        boardName = name,
        boardUrl = "https://example.com/$name/",
    )

    private fun threadRoute(key: String): AppRoute.Thread = AppRoute.Thread(
        threadKey = key,
        boardName = "board-a",
        boardUrl = "https://example.com/board-a/",
        threadTitle = "thread-$key",
    )
}
