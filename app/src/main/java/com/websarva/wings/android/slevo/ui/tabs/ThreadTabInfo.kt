package com.websarva.wings.android.slevo.ui.tabs

import com.websarva.wings.android.slevo.data.model.ThreadId
import com.websarva.wings.android.slevo.data.model.threadKey

data class ThreadTabInfo(
    val id: ThreadId,
    val title: String,
    val boardName: String,
    val boardUrl: String,
    val boardId: Long,
    val resCount: Int = 0,
    val prevResCount: Int = 0,
    val lastReadResNo: Int = 0,
    val firstNewResNo: Int? = null,
    val firstVisibleItemIndex: Int = 0, // スクロール位置（インデックス）
    val firstVisibleItemScrollOffset: Int = 0, // スクロール位置（オフセット）
    val bookmarkColorName: String? = null
) {
    val threadKey: String get() = id.threadKey
}
