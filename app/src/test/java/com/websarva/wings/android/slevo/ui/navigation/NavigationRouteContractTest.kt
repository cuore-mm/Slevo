package com.websarva.wings.android.slevo.ui.navigation

import android.os.Bundle
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * production graphで使用するtyped routeの登録、引数の復元、saved state互換性を検証する。
 */
@RunWith(RobolectricTestRunner::class)
class NavigationRouteContractTest {

    /** production graphに登録する全具象routeがserializeとtoRouteを往復できることを確認する。 */
    @Test
    fun productionTypedRouteGraph_roundTripsEveryConcreteRoute() {
        val controller = createController()

        allRoutes().forEach { route ->
            controller.navigate(route)
            assertRouteEqualsCurrent(route, controller)
        }
    }

    /** default引数とenumを含むRoot back stackがsaved stateから復元されることを確認する。 */
    @Test
    fun savedState_restoresDefaultArgumentsAndEnums() {
        val controller = createController()
        val mainShell = AppRoute.MainShell(
            mode = MainShellMode.ContextualTabs,
            startDestination = MainShellStartDestination.BookmarkList,
        )
        val board = AppRoute.Board(
            boardName = "board-a",
            boardUrl = "https://example.com/a/",
            entryTransition = BbsEntryTransition.TabsSharedBounds,
        )
        controller.navigate(mainShell)
        controller.navigate(board)

        val savedState = controller.saveState()
        assertNotNull(savedState)

        val restoredController = createController(savedState)
        assertEquals(board, restoredController.currentBackStackEntry?.toRoute<AppRoute.Board>())
        assertEquals(
            mainShell,
            restoredController.previousBackStackEntry?.toRoute<AppRoute.MainShell>(),
        )

        // Root entryとは別controllerでもinner stackを独立して保存・復元できることを確認する。
        val innerController = createController()
        innerController.navigate(AppRoute.BookmarkList)
        val innerSavedState = innerController.saveState()
        val restoredInnerController = createController(innerSavedState)
        assertEquals(
            AppRoute.BookmarkList,
            restoredInnerController.currentBackStackEntry?.toRoute<AppRoute.BookmarkList>(),
        )
    }

    /** RootとMainShellで使用する全具象routeを、production graphと同じtyped destinationとして登録する。 */
    private fun createController(savedState: Bundle? = null): TestNavHostController {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return TestNavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            if (savedState != null) {
                restoreState(savedState)
            }
            setGraph(
                graph = createGraph(startDestination = AppRoute.MainShell()) {
                    composable<AppRoute.MainShell> { }
                    composable<AppRoute.Board> { }
                    composable<AppRoute.Thread> { }
                    composable<AppRoute.HistoryList> { }
                    composable<AppRoute.Settings> { }
                    composable<AppRoute.SettingsHome> { }
                    composable<AppRoute.SettingsGeneral> { }
                    composable<AppRoute.SettingsNg> { }
                    composable<AppRoute.SettingsThread> { }
                    composable<AppRoute.SettingsCookie> { }
                    composable<AppRoute.SettingsGesture> { }
                    composable<AppRoute.SettingsBackup> { }
                    composable<AppRoute.About> { }
                    composable<AppRoute.OpenSourceLicense> { }
                    composable<AppRoute.ImageViewer> { }
                    composable<AppRoute.Tabs> { }
                    composable<AppRoute.BookmarkList> { }
                    composable<AppRoute.BbsServiceGroup> { }
                    composable<AppRoute.ServiceList> { }
                    composable<AppRoute.BoardCategoryList> { }
                    composable<AppRoute.BoardListByCategory> { }
                },
                startDestinationArgs = null,
            )
        }
    }

    /** 各routeを実際のnavigation引数へ変換した後、同じ具象型として復元できることを確認する。 */
    private fun assertRouteEqualsCurrent(route: AppRoute, controller: TestNavHostController) {
        val currentEntry = controller.currentBackStackEntry
        when (route) {
            AppRoute.BookmarkList -> assertEquals(route, currentEntry?.toRoute<AppRoute.BookmarkList>())
            AppRoute.HistoryList -> assertEquals(route, currentEntry?.toRoute<AppRoute.HistoryList>())
            AppRoute.BbsServiceGroup -> assertEquals(route, currentEntry?.toRoute<AppRoute.BbsServiceGroup>())
            AppRoute.ServiceList -> assertEquals(route, currentEntry?.toRoute<AppRoute.ServiceList>())
            is AppRoute.BoardCategoryList -> assertEquals(
                route,
                currentEntry?.toRoute<AppRoute.BoardCategoryList>(),
            )
            is AppRoute.BoardListByCategory -> assertEquals(
                route,
                currentEntry?.toRoute<AppRoute.BoardListByCategory>(),
            )
            is AppRoute.Board -> assertEquals(route, currentEntry?.toRoute<AppRoute.Board>())
            is AppRoute.Thread -> assertEquals(route, currentEntry?.toRoute<AppRoute.Thread>())
            AppRoute.Settings -> assertEquals(route, currentEntry?.toRoute<AppRoute.Settings>())
            AppRoute.SettingsHome -> assertEquals(route, currentEntry?.toRoute<AppRoute.SettingsHome>())
            AppRoute.SettingsGeneral -> assertEquals(route, currentEntry?.toRoute<AppRoute.SettingsGeneral>())
            AppRoute.SettingsNg -> assertEquals(route, currentEntry?.toRoute<AppRoute.SettingsNg>())
            AppRoute.SettingsThread -> assertEquals(route, currentEntry?.toRoute<AppRoute.SettingsThread>())
            AppRoute.SettingsCookie -> assertEquals(route, currentEntry?.toRoute<AppRoute.SettingsCookie>())
            AppRoute.SettingsGesture -> assertEquals(route, currentEntry?.toRoute<AppRoute.SettingsGesture>())
            AppRoute.SettingsBackup -> assertEquals(route, currentEntry?.toRoute<AppRoute.SettingsBackup>())
            AppRoute.Tabs -> assertEquals(route, currentEntry?.toRoute<AppRoute.Tabs>())
            is AppRoute.MainShell -> assertEquals(route, currentEntry?.toRoute<AppRoute.MainShell>())
            is AppRoute.ImageViewer -> assertEquals(route, currentEntry?.toRoute<AppRoute.ImageViewer>())
            AppRoute.About -> assertEquals(route, currentEntry?.toRoute<AppRoute.About>())
            AppRoute.OpenSourceLicense -> assertEquals(
                route,
                currentEntry?.toRoute<AppRoute.OpenSourceLicense>(),
            )
        }
    }

    /** route定義に追加漏れがないよう、Root/MainShellの全具象routeをfixtureとして列挙する。 */
    private fun allRoutes(): List<AppRoute> = listOf(
        AppRoute.MainShell(),
        AppRoute.Board(
            boardName = "board-a",
            boardUrl = "https://example.com/a/",
        ),
        AppRoute.Thread(
            threadKey = "thread-a",
            boardUrl = "https://example.com/a/",
            boardName = "board-a",
            threadTitle = "thread",
        ),
        AppRoute.HistoryList,
        AppRoute.Settings,
        AppRoute.SettingsHome,
        AppRoute.SettingsGeneral,
        AppRoute.SettingsNg,
        AppRoute.SettingsThread,
        AppRoute.SettingsCookie,
        AppRoute.SettingsGesture,
        AppRoute.SettingsBackup,
        AppRoute.About,
        AppRoute.OpenSourceLicense,
        AppRoute.ImageViewer(
            imageUrls = listOf("https%3A%2F%2Fexample.com%2Fimage.jpg"),
            initialIndex = 0,
        ),
        AppRoute.Tabs,
        AppRoute.BookmarkList,
        AppRoute.BbsServiceGroup,
        AppRoute.ServiceList,
        AppRoute.BoardCategoryList(serviceId = 1L, serviceName = "service"),
        AppRoute.BoardListByCategory(
            serviceId = 1L,
            categoryId = 2L,
            serviceName = "service",
            categoryName = "category",
        ),
    )
}
