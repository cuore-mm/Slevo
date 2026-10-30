package com.websarva.wings.android.slevo.ui.thread.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 省略候補と未取得範囲から生成されるフッター状態を検証する。 */
class ReplyPreviewFooterUiStateTest {
    /** 候補と未取得範囲の組み合わせに応じた状態を返す。 */
    @Test
    fun createsFooterForAvailableCombinations() {
        val ranges = listOf(ReplyTargetUnavailableRange(101, 103))
        assertNull(replyPreviewFooterState(0, emptyList(), emptyList()))
        assertNull(replyPreviewFooterState(0, listOf(1, 2, 3), emptyList()))
        assertNull(replyPreviewFooterState(2, emptyList(), emptyList()))
        assertEquals(
            ReplyPreviewFooterUiState.More(3),
            replyPreviewFooterState(2, listOf(1, 2, 3), emptyList()),
        )
        assertEquals(
            ReplyPreviewFooterUiState.Unavailable(ranges),
            replyPreviewFooterState(0, emptyList(), ranges),
        )
        assertEquals(
            ReplyPreviewFooterUiState.MoreAndUnavailable(3, ranges),
            replyPreviewFooterState(2, listOf(1, 2, 3), ranges),
        )
    }
}
