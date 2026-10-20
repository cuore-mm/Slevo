package com.websarva.wings.android.slevo.ui.mainshell

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** MainShell内BackをRoot Backより優先するdispatcher接続を検証する。 */
@RunWith(AndroidJUnit4::class)
class MainShellBackHandlerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** inner entryがある場合はActivityへ委譲せず、inner stackだけをpopする。 */
    @Test
    fun innerEntry_consumesBackBeforeRoot() {
        val controller = createController()
        controller.navigate(AppRoute.BookmarkList)

        composeRule.setContent {
            BackHandlerHarness(controller)
        }
        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertEquals(
            AppRoute.Tabs,
            controller.currentBackStackEntry?.toRoute<AppRoute.Tabs>(),
        )
    }

    /** BackHandlerを表示する最小のComposable harness。 */
    @Composable
    private fun BackHandlerHarness(controller: TestNavHostController) {
        MainShellBackHandler(controller)
    }

    /** Tabsをstart destinationにしたMainShell相当のinner controllerを構成する。 */
    private fun createController(): TestNavHostController =
        TestNavHostController(composeRule.activity).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            setGraph(
                graph = createGraph(startDestination = AppRoute.Tabs) {
                    composable<AppRoute.Tabs> { }
                    composable<AppRoute.BookmarkList> { }
                },
                startDestinationArgs = null,
            )
        }
}
