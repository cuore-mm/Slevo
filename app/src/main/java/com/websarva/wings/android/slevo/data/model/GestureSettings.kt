package com.websarva.wings.android.slevo.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.websarva.wings.android.slevo.R

/**
 * ジェスチャー設定の保存・読み出しに利用するモデル。
 */
data class GestureSettings(
    val isEnabled: Boolean,
    val assignments: Map<GestureDirection, GestureAction?>
) {
    companion object {
        private val DEFAULT_ASSIGNMENTS: Map<GestureDirection, GestureAction?> = mapOf(
            GestureDirection.Right to GestureAction.SwitchToPreviousTab,
            GestureDirection.RightUp to GestureAction.ToTop,
            GestureDirection.RightLeft to GestureAction.OpenNewTab,
            GestureDirection.RightDown to GestureAction.ToBottom,
            GestureDirection.Left to GestureAction.SwitchToNextTab,
            GestureDirection.LeftUp to GestureAction.Refresh,
            GestureDirection.LeftRight to GestureAction.CloseTab,
            GestureDirection.LeftDown to GestureAction.PostOrCreateThread
        )

        val DEFAULT = GestureSettings(
            isEnabled = false,
            assignments = DEFAULT_ASSIGNMENTS
        )
    }
}

enum class GestureDirection(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    Right(
        labelRes = R.string.gesture_direction_right,
        iconRes = R.drawable.ic_gesture_right,
    ),
    RightUp(
        labelRes = R.string.gesture_direction_right_up,
        iconRes = R.drawable.ic_gesture_right_then_up,
    ),
    RightLeft(
        labelRes = R.string.gesture_direction_right_left,
        iconRes = R.drawable.ic_gesture_right_then_left,
    ),
    RightDown(
        labelRes = R.string.gesture_direction_right_down,
        iconRes = R.drawable.ic_gesture_right_then_down,
    ),
    Left(
        labelRes = R.string.gesture_direction_left,
        iconRes = R.drawable.ic_gesture_left,
    ),
    LeftUp(
        labelRes = R.string.gesture_direction_left_up,
        iconRes = R.drawable.ic_gesture_left_then_up,
    ),
    LeftRight(
        labelRes = R.string.gesture_direction_left_right,
        iconRes = R.drawable.ic_gesture_left_then_right,
    ),
    LeftDown(
        labelRes = R.string.gesture_direction_left_down,
        iconRes = R.drawable.ic_gesture_left_then_down,
    ),
}

enum class GestureAction(@StringRes val labelRes: Int) {
    ToTop(R.string.gesture_action_to_top),
    ToBottom(R.string.gesture_action_to_bottom),
    SwitchToNextTab(R.string.gesture_action_switch_to_next_tab),
    SwitchToPreviousTab(R.string.gesture_action_switch_to_previous_tab),
    OpenNewTab(R.string.gesture_action_open_new_tab),
    CloseTab(R.string.gesture_action_close_tab),
    OpenTabList(R.string.gesture_action_open_tab_list),
    Refresh(R.string.refresh),
    PostOrCreateThread(R.string.gesture_action_post_or_create_thread),
    Search(R.string.search),
    OpenBookmarkList(R.string.gesture_action_open_bookmark_list),
    OpenBoardList(R.string.gesture_action_open_board_list),
    OpenHistory(R.string.gesture_action_open_history),
}
