package com.websarva.wings.android.slevo.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Board/Thread専用Navigation transitionのroute判定を検証する。
 */
class TransitionSpecsTest {
    /** BoardとThreadの相互遷移だけが専用transition対象になる。 */
    @Test
    fun isBoardThreadTransition_matchesBothDirections() {
        assertTrue(
            isBoardThreadTransition(
                initialRoute = "com.example.AppRoute.Board/{boardUrl}",
                targetRoute = "com.example.AppRoute.Thread/{threadKey}",
            ),
        )
        assertTrue(
            isBoardThreadTransition(
                initialRoute = "com.example.AppRoute.Thread/{threadKey}",
                targetRoute = "com.example.AppRoute.Board/{boardUrl}",
            ),
        )
    }

    /** BoardからThreadへ進む方向だけを判定する。 */
    @Test
    fun isBoardToThreadTransition_matchesOnlyForwardDirection() {
        assertTrue(
            isBoardToThreadTransition(
                initialRoute = "com.example.AppRoute.Board/{boardUrl}",
                targetRoute = "com.example.AppRoute.Thread/{threadKey}",
            ),
        )
        assertFalse(
            isBoardToThreadTransition(
                initialRoute = "com.example.AppRoute.Thread/{threadKey}",
                targetRoute = "com.example.AppRoute.Board/{boardUrl}",
            ),
        )
    }

    /** ThreadからBoardへ戻る方向だけを判定する。 */
    @Test
    fun isThreadToBoardTransition_matchesOnlyReturnDirection() {
        assertTrue(
            isThreadToBoardTransition(
                initialRoute = "com.example.AppRoute.Thread/{threadKey}",
                targetRoute = "com.example.AppRoute.Board/{boardUrl}",
            ),
        )
        assertFalse(
            isThreadToBoardTransition(
                initialRoute = "com.example.AppRoute.Board/{boardUrl}",
                targetRoute = "com.example.AppRoute.Thread/{threadKey}",
            ),
        )
    }

    /** TabsとBoard / Threadの組み合わせだけをpage transition対象にする。 */
    @Test
    fun bbsPageTransition_matchesTabsAndBbsDirections() {
        assertTrue(
            isTabsToBbsTransition(
                initialRoute = "com.example.AppRoute.Tabs",
                targetRoute = "com.example.AppRoute.Board/{boardUrl}",
            ),
        )
        assertTrue(
            isTabsToBbsTransition(
                initialRoute = "com.example.AppRoute.Tabs",
                targetRoute = "com.example.AppRoute.Thread/{threadKey}",
            ),
        )
        assertTrue(
            isBbsToTabsTransition(
                initialRoute = "com.example.AppRoute.Thread/{threadKey}",
                targetRoute = "com.example.AppRoute.Tabs",
            ),
        )
        assertFalse(
            isTabsToBbsTransition(
                initialRoute = "com.example.AppRoute.Tabs",
                targetRoute = "com.example.AppRoute.BookmarkList",
            ),
        )
        assertFalse(
            isBbsToTabsTransition(
                initialRoute = "com.example.AppRoute.Board/{boardUrl}",
                targetRoute = "com.example.AppRoute.Thread/{threadKey}",
            ),
        )
    }

    /** 類似名のdestination、null、ImageViewerは専用transition対象にならない。 */
    @Test
    fun isBoardThreadTransition_rejectsOtherDestinations() {
        assertFalse(
            isBoardThreadTransition(
                initialRoute = "com.example.AppRoute.BoardCategoryList/{serviceId}",
                targetRoute = "com.example.AppRoute.Thread/{threadKey}",
            ),
        )
        assertFalse(
            isBoardThreadTransition(
                initialRoute = "com.example.AppRoute.Thread/{threadKey}",
                targetRoute = "com.example.AppRoute.ImageViewer/{imageUrls}",
            ),
        )
        assertFalse(
            isThreadToBoardTransition(
                initialRoute = "com.example.AppRoute.BoardCategoryList/{serviceId}",
                targetRoute = "com.example.AppRoute.Board/{boardUrl}",
            ),
        )
        assertFalse(isBoardThreadTransition(initialRoute = null, targetRoute = null))
    }

    /** slide-only transitionが従来と同じ300msを使う。 */
    @Test
    fun boardThreadTransition_usesExistingDuration() {
        assertEquals(300, DefaultAnimDuration)
    }
}
