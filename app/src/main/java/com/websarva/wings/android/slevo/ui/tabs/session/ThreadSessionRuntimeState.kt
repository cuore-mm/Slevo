package com.websarva.wings.android.slevo.ui.tabs.session

/**
 * スレッドタブごとの継続ランタイム状態。
 *
 * Compose の描画状態ではないが、タブ単位で分離して扱う必要がある保留投稿情報、
 * ポップアップ ID 採番、オートスクロール更新時刻を保持する。
 */
data class ThreadSessionRuntimeState(
    val pendingPost: PendingThreadPostState? = null,
    val nextPopupId: Long = 1L,
    val lastAutoRefreshTime: Long = 0L,
)

/**
 * スレッド再読み込み後に履歴へ反映するための保留投稿情報。
 */
data class PendingThreadPostState(
    val resNum: Int?,
    val content: String,
    val name: String,
    val email: String,
)
