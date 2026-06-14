package com.websarva.wings.android.slevo.ui.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
    fun showThreadScreenForTabSelection_replacesCurrentBoardScreen() {
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
        assertTrue(controller.previousBackStackEntry?.destination?.hasRoute(AppRoute.Tabs::class) == true)
    }

    private fun createController(): TestNavHostController {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return TestNavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setGraph(startDestination = AppRoute.RouteName.TABS) {
                composable<AppRoute.Tabs> { }
                composable<AppRoute.Board> { }
                composable<AppRoute.Thread> { }
            }
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
}
