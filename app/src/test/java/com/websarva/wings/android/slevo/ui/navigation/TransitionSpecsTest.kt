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
        assertFalse(isBoardThreadTransition(initialRoute = null, targetRoute = null))
    }

    /** slide-only transitionが従来と同じ300msを使う。 */
    @Test
    fun boardThreadTransition_usesExistingDuration() {
        assertEquals(300, DefaultAnimDuration)
    }
}
