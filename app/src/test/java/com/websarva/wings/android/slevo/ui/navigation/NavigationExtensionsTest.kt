package com.websarva.wings.android.slevo.ui.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
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

    /** Board起点のcontextual MainShellからの直接Board選択では、source BoardをRoot履歴から除去する。 */
    @Test
    fun showBoardScreenFromMainShell_replacesSourceBoard() {
        val controller = createRootController()
        val sourceBoard = boardRoute("board-a")
        val selectedBoard = boardRoute("board-b")
        controller.navigateToBoardScreen(sourceBoard)
        val sourceBoardEntryId = controller.currentBackStackEntry?.id
        controller.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val mainShellEntryId = controller.currentBackStackEntry?.id.orEmpty()
        val innerController = createController()
        val tabsEntryId = innerController.currentBackStackEntry?.id.orEmpty()

        controller.showBoardScreenFromMainShell(
            sourceRoute = sourceBoard,
            mainShellEntryId = mainShellEntryId,
            tabsEntryId = tabsEntryId,
            mainShellNavController = innerController,
            route = selectedBoard,
        )

        assertBoardRoute(selectedBoard, controller)
        assertNotEquals(sourceBoardEntryId, controller.currentBackStackEntry?.id)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.MainShell::class) == true)
        assertEquals(
            BbsEntryTransition.TabsSharedBounds,
            controller.currentBackStackEntry?.toRoute<AppRoute.Board>()?.entryTransition,
        )
    }

    /** Thread起点のcontextual MainShellからの直接Thread選択では、source ThreadをRoot履歴から除去する。 */
    @Test
    fun showThreadScreenFromMainShell_replacesSourceThread() {
        val controller = createRootController()
        val sourceThread = threadRoute("1")
        val selectedThread = threadRoute("2")
        controller.navigateToThreadScreen(sourceThread)
        controller.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val mainShellEntryId = controller.currentBackStackEntry?.id.orEmpty()
        val innerController = createController()
        val tabsEntryId = innerController.currentBackStackEntry?.id.orEmpty()

        controller.showThreadScreenFromMainShell(
            sourceRoute = sourceThread,
            mainShellEntryId = mainShellEntryId,
            tabsEntryId = tabsEntryId,
            mainShellNavController = innerController,
            route = selectedThread,
        )

        assertThreadRoute(selectedThread, controller)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.MainShell::class) == true)
        assertEquals(
            BbsEntryTransition.TabsSharedBounds,
            controller.currentBackStackEntry?.toRoute<AppRoute.Thread>()?.entryTransition,
        )
    }

    /** contextual MainShellのinner Bookmark経由では、Board起点と中間履歴を保持する。 */
    @Test
    fun mainShellBookmarkSelection_keepsRootAndInnerHistory() {
        val controller = createRootController()
        val sourceBoard = boardRoute("board-a")
        val selectedBoard = boardRoute("board-b")
        controller.navigateToBoardScreen(sourceBoard)
        controller.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val mainShellEntryId = controller.currentBackStackEntry?.id.orEmpty()
        val innerController = createController()
        innerController.navigate(AppRoute.BookmarkList)

        controller.navigateToBoardScreen(
            selectedBoard.copy(entryTransition = BbsEntryTransition.MainShellSlide),
        )

        assertBoardRoute(selectedBoard, controller)
        assertTrue(controller.previousBackStackEntry?.id == mainShellEntryId)
        assertEquals(
            BbsEntryTransition.MainShellSlide,
            controller.currentBackStackEntry?.toRoute<AppRoute.Board>()?.entryTransition,
        )

        // Root Backでcontextual MainShellへ戻り、inner BackでBookmarkからTabsへ戻る。
        controller.popBackStack()
        assertTrue(innerController.currentBackStackEntry?.destination?.hasRoute(AppRoute.BookmarkList::class) == true)
        innerController.popBackStack()
        assertTrue(innerController.currentBackStackEntry?.destination?.hasRoute(AppRoute.Tabs::class) == true)
        controller.popBackStack()
        assertBoardRoute(sourceBoard, controller)
    }

    /** Rootまたはinnerのentry IDが古い場合は、選択結果がRoot履歴を変更しない。 */
    @Test
    fun showBoardScreenFromMainShell_ignoresStaleEntryIds() {
        val controller = createRootController()
        val sourceBoard = boardRoute("board-a")
        controller.navigateToBoardScreen(sourceBoard)
        controller.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val mainShellEntryId = controller.currentBackStackEntry?.id.orEmpty()
        val innerController = createController()
        val tabsEntryId = innerController.currentBackStackEntry?.id.orEmpty()
        val currentRootEntryId = controller.currentBackStackEntry?.id

        controller.showBoardScreenFromMainShell(
            sourceRoute = sourceBoard,
            mainShellEntryId = "stale-$mainShellEntryId",
            tabsEntryId = tabsEntryId,
            mainShellNavController = innerController,
            route = boardRoute("board-b"),
        )
        controller.showBoardScreenFromMainShell(
            sourceRoute = sourceBoard,
            mainShellEntryId = mainShellEntryId,
            tabsEntryId = "stale-$tabsEntryId",
            mainShellNavController = innerController,
            route = boardRoute("board-c"),
        )

        assertEquals(currentRootEntryId, controller.currentBackStackEntry?.id)
        assertTrue(controller.currentBackStackEntry?.destination?.hasRoute(AppRoute.MainShell::class) == true)
    }

    /** Tabs以外のoriginではcontextual MainShellを統合せず、MainShellSlide付きでRootへ積む。 */
    @Test
    fun showBoardScreenFromMainShell_keepsNonTabsOriginHistory() {
        val controller = createRootController()
        val sourceBoard = boardRoute("board-a")
        controller.navigateToBoardScreen(sourceBoard)
        controller.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val mainShellEntryId = controller.currentBackStackEntry?.id.orEmpty()
        val innerController = createController()
        innerController.navigate(AppRoute.BookmarkList)

        controller.showBoardScreenFromMainShell(
            sourceRoute = sourceBoard,
            origin = MainShellBbsOrigin.Bookmark,
            mainShellEntryId = mainShellEntryId,
            tabsEntryId = "not-current-tabs",
            mainShellNavController = innerController,
            route = boardRoute("board-b"),
        )

        assertBoardRoute(boardRoute("board-b"), controller)
        assertEquals(mainShellEntryId, controller.previousBackStackEntry?.id)
        assertEquals(
            BbsEntryTransition.MainShellSlide,
            controller.currentBackStackEntry?.toRoute<AppRoute.Board>()?.entryTransition,
        )
    }

    /** contextual TabsのBoard起点からThreadを選択した場合は、source Boardを残してThreadを追加する。 */
    @Test
    fun showThreadScreenFromMainShell_pushesThreadAfterBoardSource() {
        val controller = createRootController()
        val sourceBoard = boardRoute("board-a")
        val selectedThread = threadRoute("thread-b")
        controller.navigateToBoardScreen(sourceBoard)
        controller.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val mainShellEntryId = controller.currentBackStackEntry?.id.orEmpty()
        val innerController = createController()
        val tabsEntryId = innerController.currentBackStackEntry?.id.orEmpty()

        controller.showThreadScreenFromMainShell(
            sourceRoute = sourceBoard,
            mainShellEntryId = mainShellEntryId,
            tabsEntryId = tabsEntryId,
            mainShellNavController = innerController,
            route = selectedThread,
        )

        assertThreadRoute(selectedThread, controller)
        assertEquals(sourceBoard, controller.previousBackStackEntry?.toRoute<AppRoute.Board>())
        assertEquals(
            BbsEntryTransition.TabsSharedBounds,
            controller.currentBackStackEntry?.toRoute<AppRoute.Thread>()?.entryTransition,
        )
    }

    /** contextual TabsのThread起点で直下Boardがある場合は、既存Boardまでpopする。 */
    @Test
    fun showBoardScreenFromMainShell_popsToImmediatelyPreviousBoard() {
        val controller = createRootController()
        val sourceBoard = boardRoute("board-a")
        val sourceThread = threadRoute("thread-a")
        controller.navigateToBoardScreen(sourceBoard)
        controller.navigateToThreadScreen(sourceThread)
        controller.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val mainShellEntryId = controller.currentBackStackEntry?.id.orEmpty()
        val innerController = createController()
        val tabsEntryId = innerController.currentBackStackEntry?.id.orEmpty()

        controller.showBoardScreenFromMainShell(
            sourceRoute = sourceThread,
            mainShellEntryId = mainShellEntryId,
            tabsEntryId = tabsEntryId,
            mainShellNavController = innerController,
            route = boardRoute("board-b"),
        )

        assertBoardRoute(sourceBoard, controller)
        assertEquals(sourceBoard, controller.currentBackStackEntry?.toRoute<AppRoute.Board>())
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.MainShell::class) == true)
    }

    /** contextual TabsのThread起点で直下Boardがない場合は、source ThreadをBoardへ置換する。 */
    @Test
    fun showBoardScreenFromMainShell_replacesThreadWithoutImmediatelyPreviousBoard() {
        val controller = createRootController()
        val sourceThread = threadRoute("thread-a")
        val selectedBoard = boardRoute("board-b")
        controller.navigateToThreadScreen(sourceThread)
        controller.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val mainShellEntryId = controller.currentBackStackEntry?.id.orEmpty()
        val innerController = createController()
        val tabsEntryId = innerController.currentBackStackEntry?.id.orEmpty()

        controller.showBoardScreenFromMainShell(
            sourceRoute = sourceThread,
            mainShellEntryId = mainShellEntryId,
            tabsEntryId = tabsEntryId,
            mainShellNavController = innerController,
            route = selectedBoard,
        )

        assertBoardRoute(selectedBoard, controller)
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.MainShell::class) == true)
    }

    /** Bookmark起点のcontextual MainShellからBoardとThreadを開いてもsourceを統合しない。 */
    @Test
    fun showBbsScreenFromBookmarkOrigin_keepsRootHistoryForBothRouteTypes() {
        val boardController = createRootController()
        val boardSource = boardRoute("board-a")
        boardController.navigateToBoardScreen(boardSource)
        boardController.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val boardShellId = boardController.currentBackStackEntry?.id.orEmpty()
        val boardInnerController = createController().apply { navigate(AppRoute.BookmarkList) }
        val boardBefore = boardController.currentBackStackEntry?.id
        boardController.showBoardScreenFromMainShell(
            sourceRoute = boardSource,
            origin = MainShellBbsOrigin.Bookmark,
            mainShellEntryId = boardShellId,
            tabsEntryId = boardInnerController.currentBackStackEntry?.id.orEmpty(),
            mainShellNavController = boardInnerController,
            route = boardRoute("board-b"),
        )

        assertBoardRoute(boardRoute("board-b"), boardController)
        assertEquals(boardShellId, boardController.previousBackStackEntry?.id)
        assertNotEquals(boardBefore, boardController.currentBackStackEntry?.id)

        val threadController = createRootController()
        val threadSource = threadRoute("thread-a")
        threadController.navigateToThreadScreen(threadSource)
        threadController.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val threadShellId = threadController.currentBackStackEntry?.id.orEmpty()
        val threadInnerController = createController().apply { navigate(AppRoute.BookmarkList) }
        threadController.showThreadScreenFromMainShell(
            sourceRoute = threadSource,
            origin = MainShellBbsOrigin.Bookmark,
            mainShellEntryId = threadShellId,
            tabsEntryId = threadInnerController.currentBackStackEntry?.id.orEmpty(),
            mainShellNavController = threadInnerController,
            route = threadRoute("thread-b"),
        )

        assertThreadRoute(threadRoute("thread-b"), threadController)
        assertEquals(threadShellId, threadController.previousBackStackEntry?.id)
    }

    /** BBSサービス起点のcontextual MainShellからBoardとThreadを開いてもsourceを統合しない。 */
    @Test
    fun showBbsScreenFromBbsServiceOrigin_keepsRootHistoryForBothRouteTypes() {
        val boardController = createRootController()
        val boardSource = boardRoute("board-a")
        boardController.navigateToBoardScreen(boardSource)
        boardController.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val boardShellId = boardController.currentBackStackEntry?.id.orEmpty()
        val boardInnerController = createController().apply { navigate(AppRoute.BbsServiceGroup) }
        boardController.showBoardScreenFromMainShell(
            sourceRoute = boardSource,
            origin = MainShellBbsOrigin.BbsServiceGroup,
            mainShellEntryId = boardShellId,
            tabsEntryId = boardInnerController.currentBackStackEntry?.id.orEmpty(),
            mainShellNavController = boardInnerController,
            route = boardRoute("board-b"),
        )

        assertBoardRoute(boardRoute("board-b"), boardController)
        assertEquals(boardShellId, boardController.previousBackStackEntry?.id)

        val threadController = createRootController()
        val threadSource = threadRoute("thread-a")
        threadController.navigateToThreadScreen(threadSource)
        threadController.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val threadShellId = threadController.currentBackStackEntry?.id.orEmpty()
        val threadInnerController = createController().apply { navigate(AppRoute.BbsServiceGroup) }
        threadController.showThreadScreenFromMainShell(
            sourceRoute = threadSource,
            origin = MainShellBbsOrigin.BbsServiceGroup,
            mainShellEntryId = threadShellId,
            tabsEntryId = threadInnerController.currentBackStackEntry?.id.orEmpty(),
            mainShellNavController = threadInnerController,
            route = threadRoute("thread-b"),
        )

        assertThreadRoute(threadRoute("thread-b"), threadController)
        assertEquals(threadShellId, threadController.previousBackStackEntry?.id)
    }

    /** MainShell直下がcallbackのsourceと異なる場合は、古いRoot entryを推測せず中止する。 */
    @Test
    fun showBoardScreenFromMainShell_ignoresNonAdjacentSourceRoute() {
        val controller = createRootController()
        val sourceBoard = boardRoute("board-a")
        val sourceThread = threadRoute("thread-a")
        controller.navigateToBoardScreen(sourceBoard)
        controller.navigate(AppRoute.MainShell(MainShellMode.ContextualTabs))
        val mainShellEntryId = controller.currentBackStackEntry?.id.orEmpty()
        val innerController = createController()
        val tabsEntryId = innerController.currentBackStackEntry?.id.orEmpty()
        val currentEntryId = controller.currentBackStackEntry?.id

        controller.showBoardScreenFromMainShell(
            sourceRoute = sourceThread,
            mainShellEntryId = mainShellEntryId,
            tabsEntryId = tabsEntryId,
            mainShellNavController = innerController,
            route = boardRoute("board-b"),
        )

        assertEquals(currentEntryId, controller.currentBackStackEntry?.id)
        assertTrue(controller.currentBackStackEntry?.destination?.hasRoute<AppRoute.MainShell>() == true)

        controller.showThreadScreenFromMainShell(
            sourceRoute = sourceThread,
            mainShellEntryId = mainShellEntryId,
            tabsEntryId = tabsEntryId,
            mainShellNavController = innerController,
            route = threadRoute("thread-b"),
        )

        assertEquals(currentEntryId, controller.currentBackStackEntry?.id)
    }

    /** 追加された遷移文脈はdefault値を持ち、既存のroute生成結果を変更しない。 */
    @Test
    fun bbsRoutes_keepDefaultEntryTransition() {
        assertEquals(BbsEntryTransition.Default, boardRoute("board-a").entryTransition)
        assertEquals(BbsEntryTransition.Default, threadRoute("1").entryTransition)
        assertEquals(MainShellMode.Base, AppRoute.MainShell().mode)
        assertEquals(MainShellStartDestination.Tabs, AppRoute.MainShell().startDestination)
    }

    /** base MainShellのトップレベル切替ではinner履歴を一件に保ち、Root相当の履歴を増やさない。 */
    @Test
    fun mainShellTopLevelSelection_replacesInnerTopLevelWithoutGrowingStack() {
        val controller = createController()

        controller.navigateToMainShellTopLevel(AppRoute.BookmarkList)
        controller.navigateToMainShellTopLevel(AppRoute.ServiceList)
        controller.navigateToMainShellTopLevel(AppRoute.Tabs)

        assertTrue(controller.currentBackStackEntry?.destination?.hasRoute(AppRoute.Tabs::class) == true)
        assertEquals(1, destinationEntryCount(controller))
        assertTrue(!controller.popBackStack())
    }

    /** contextual MainShellのTabsから一覧へ移動した場合はinner履歴を積み、BackでTabsへ戻る。 */
    @Test
    fun contextualMainShellSelection_pushesInnerDestinationAndPopsToTabs() {
        val controller = createController()

        controller.navigateToMainShellTopLevel(AppRoute.BookmarkList)

        assertTrue(
            controller.currentBackStackEntry?.destination?.hasRoute(AppRoute.BookmarkList::class) == true,
        )
        assertEquals(2, destinationEntryCount(controller))

        assertTrue(controller.popBackStack())
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

    private fun createRootController(): TestNavHostController {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return TestNavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setGraph(
                graph = createGraph(startDestination = AppRoute.MainShell()) {
                    composable<AppRoute.MainShell> { }
                    composable<AppRoute.Board> { }
                    composable<AppRoute.Thread> { }
                },
                startDestinationArgs = null,
            )
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

    /** NavGraph自身のentryを除外し、MainShell内の実destination数を数える。 */
    private fun destinationEntryCount(controller: TestNavHostController): Int =
        controller.currentBackStack.value.count { it.destination !is NavGraph }

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
