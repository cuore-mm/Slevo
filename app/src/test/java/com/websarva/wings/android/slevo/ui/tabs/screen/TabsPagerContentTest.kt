package com.websarva.wings.android.slevo.ui.tabs.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TabsカードのShared Bounds候補を、表示状態と操作状態から排他的に導出する規則を検証する。
 */
class TabsPagerContentTest {
    /** 表示中pageのtargetかつidleなカードだけを候補にする。 */
    @Test
    fun targetIdleCard_isEnabled() {
        assertTrue(
            isTabCardSharedTransitionEnabled(
                pageSharedTransitionEnabled = true,
                isRemoving = false,
                isDragging = false,
                isSelectionMode = false,
                isInLongPressSelectionMode = false,
            ),
        )
    }

    /** 非表示page、検索crossfade退出側、削除、drag、選択、長押し中は候補にしない。 */
    @Test
    fun nonTargetOrInteractionState_disablesCard() {
        val states = listOf(
            false to false,
            true to true,
        )
        states.forEach { (pageEnabled, removing) ->
            assertFalse(
                isTabCardSharedTransitionEnabled(
                    pageSharedTransitionEnabled = pageEnabled,
                    isRemoving = removing,
                    isDragging = false,
                    isSelectionMode = false,
                    isInLongPressSelectionMode = false,
                ),
            )
        }
        assertFalse(
            isTabCardSharedTransitionEnabled(
                pageSharedTransitionEnabled = true,
                isRemoving = false,
                isDragging = true,
                isSelectionMode = false,
                isInLongPressSelectionMode = false,
            ),
        )
        assertFalse(
            isTabCardSharedTransitionEnabled(
                pageSharedTransitionEnabled = true,
                isRemoving = false,
                isDragging = false,
                isSelectionMode = true,
                isInLongPressSelectionMode = false,
            ),
        )
        assertFalse(
            isTabCardSharedTransitionEnabled(
                pageSharedTransitionEnabled = true,
                isRemoving = false,
                isDragging = false,
                isSelectionMode = false,
                isInLongPressSelectionMode = true,
            ),
        )
    }
}
