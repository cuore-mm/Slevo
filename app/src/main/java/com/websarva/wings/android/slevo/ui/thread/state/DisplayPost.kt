package com.websarva.wings.android.slevo.ui.thread.state

/**
 * UI表示用の投稿情報
 */
data class DisplayPost(
    val num: Int,
    val post: ThreadPostUiModel,
    val dimmed: Boolean,
    val isAfter: Boolean,
    val depth: Int
)
