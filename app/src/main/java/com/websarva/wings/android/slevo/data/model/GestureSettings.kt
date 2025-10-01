package com.websarva.wings.android.slevo.data.model

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
        val DEFAULT = GestureSettings(
            isEnabled = false,
            assignments = GestureDirection.values().associateWith { null }
        )
    }
}

enum class GestureDirection(@StringRes val labelRes: Int) {
    Right(R.string.gesture_direction_right),
    RightUp(R.string.gesture_direction_right_up),
    RightLeft(R.string.gesture_direction_right_left),
    RightDown(R.string.gesture_direction_right_down),
    Left(R.string.gesture_direction_left),
    LeftUp(R.string.gesture_direction_left_up),
    LeftRight(R.string.gesture_direction_left_right),
    LeftDown(R.string.gesture_direction_left_down),
}

enum class GestureAction(@StringRes val labelRes: Int) {
    ToTop(R.string.gesture_action_to_top),
    ToBottom(R.string.gesture_action_to_bottom),
    Refresh(R.string.refresh),
    PostOrCreateThread(R.string.gesture_action_post_or_create_thread),
    Search(R.string.search),
}
