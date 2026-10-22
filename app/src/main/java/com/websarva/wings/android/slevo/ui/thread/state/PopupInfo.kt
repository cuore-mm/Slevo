package com.websarva.wings.android.slevo.ui.thread.state

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/** ポップアップの通常レス表示と返信先候補一覧を区別する。 */
enum class PopupContentType {
    STANDARD,
    REPLY_TARGETS,
}

/**
 * Holds the post numbers and layout info needed for reply popup rendering.
 *
 * [popupId] is a stable identifier allocated when the popup is appended.
 * [postNumbers] stores the 1-based post numbers to render, resolved against
 * the latest [ThreadUiState.posts] at composition time.
 * Offset and size are used to position and measure the popup, while [indentLevels]
 * aligns with [postNumbers] to describe tree indentation.
 *
 * ルート番号はポップアップ内のツリー境界を判断するために保持する。
 */
data class PopupInfo(
    val popupId: Long,
    val postNumbers: List<Int>,
    val offset: IntOffset,
    val size: IntSize = IntSize.Zero,
    val indentLevels: List<Int> = emptyList(),
    val rootNumbers: List<Int> = emptyList(),
    val contentType: PopupContentType = PopupContentType.STANDARD,
    val hasMoreCandidates: Boolean = false,
)
