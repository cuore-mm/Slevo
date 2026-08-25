package com.websarva.wings.android.slevo.ui.tabs.session

/**
 * スレッドタブごとの継続ランタイム状態。
 *
 * Compose の描画状態ではないが、タブ単位で分離して扱う必要があるポップアップ ID 採番と
 * オートスクロール更新時刻を保持する。投稿照合待ちはRoomで永続管理する。
 */
data class ThreadSessionRuntimeState(
    val nextPopupId: Long = 1L,
    val lastAutoRefreshTime: Long = 0L,
)
