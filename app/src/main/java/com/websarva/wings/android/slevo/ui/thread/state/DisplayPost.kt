package com.websarva.wings.android.slevo.ui.thread.state

/**
 * UI表示用の投稿情報。
 *
 * 表示順に応じた深さと、所属ツリーのルート番号を保持する。
 */
data class DisplayPost(
    val num: Int,
    val post: ThreadPostUiModel,
    val dimmed: Boolean,
    val isAfter: Boolean,
    val depth: Int,
    val rootNumber: Int,
)
