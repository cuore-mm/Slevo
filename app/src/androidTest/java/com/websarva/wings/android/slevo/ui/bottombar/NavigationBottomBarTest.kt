package com.websarva.wings.android.slevo.ui.bottombar

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.websarva.wings.android.slevo.R
import com.websarva.wings.android.slevo.ui.navigation.AppRoute
import com.websarva.wings.android.slevo.ui.theme.SlevoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Material 3 NavigationBar の項目表示とクリック可能性を検証する。 */
@RunWith(AndroidJUnit4::class)
class NavigationBottomBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** 3つの遷移項目とMore項目が表示され、各クリック callback が一度ずつ呼ばれる。 */
    @Test
    fun allItems_areDisplayedAndClickable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clickedRoutes = mutableListOf<AppRoute>()
        var moreClickCount = 0

        composeRule.setContent {
            SlevoTheme {
                NavigationBottomBar(
                    modifier = Modifier.testTag("navigation-bar"),
                    currentDestination = null,
                    onClick = { clickedRoutes += it },
                    onMoreClick = { moreClickCount++ },
                )
            }
        }

        // Material 3 NavigationBar の標準最小高を下回らず、固定56dp制約を受けていないことを確認する。
        composeRule.onNodeWithTag("navigation-bar").assertHeightIsAtLeast(80.dp)

        listOf(
            R.string.tabs,
            R.string.bookmark,
            R.string.boardList,
            R.string.more,
        ).forEach { labelResId ->
            composeRule.onNodeWithContentDescription(context.getString(labelResId))
                .assertIsDisplayed()
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.tabs)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.bookmark)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.boardList)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.more)).performClick()

        assertEquals(
            listOf(AppRoute.Tabs, AppRoute.BookmarkList, AppRoute.ServiceList),
            clickedRoutes,
        )
        assertEquals(1, moreClickCount)
    }
}
