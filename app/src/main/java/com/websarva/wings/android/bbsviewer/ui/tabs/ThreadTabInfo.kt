package com.websarva.wings.android.bbsviewer.ui.tabs

data class ThreadTabInfo(
    val key: String,
    val title: String,
    val boardName: String,
    val boardUrl: String,
    val boardId: Long,
    val resCount: Int = 0,
    val firstVisibleItemIndex: Int = 0, // スクロール位置（インデックス）
    val firstVisibleItemScrollOffset: Int = 0 // スクロール位置（オフセット）
)
