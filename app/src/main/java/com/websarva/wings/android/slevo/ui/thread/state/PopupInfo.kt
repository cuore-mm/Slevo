package com.websarva.wings.android.slevo.ui.thread.state

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Holds the posts and layout info needed for reply popup rendering.
 *
 * [popupId] is a stable identifier allocated when the popup is appended.
 * Offset and size are used to position and measure the popup, while [indentLevels]
 * aligns with [posts] to describe tree indentation.
 */
data class PopupInfo(
    val popupId: Long,
    val posts: List<ThreadPostUiModel>,
    val offset: IntOffset,
    val size: IntSize = IntSize.Zero,
    val indentLevels: List<Int> = emptyList(),
)
