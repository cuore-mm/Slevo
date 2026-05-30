package com.websarva.wings.android.slevo.ui.tabs.model

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.threadKey

/**
 * タブ一覧とスレッド画面間で受け渡す、開いているスレッドタブの表示モデル。
 * タブ固有のスクロール位置に加え、`thread_states` と履歴既読状態から合成したタイトル・レス数・新着数を保持する。
 */
data class ThreadTabInfo(
    val id: ThreadId,
    val title: String,
    val boardName: String,
    val boardUrl: String,
    val boardId: Long,
    val resCount: Int = 0,
    val newResCount: Int = 0,
    val prevResCount: Int = 0,
    val lastReadResNo: Int = 0,
    val firstNewResNo: Int? = null,
    val firstVisibleItemIndex: Int = 0, // スクロール位置（インデックス）
    val firstVisibleItemScrollOffset: Int = 0, // スクロール位置（オフセット）
    val bookmarkColorName: String? = null,
    val isPinned: Boolean = false
) {
    val threadKey: String get() = id.threadKey
}
